package com.danishbears.bulgariantrainer.typing

import androidx.room.withTransaction
import com.danishbears.bulgariantrainer.review.*
import com.danishbears.bulgariantrainer.data.*
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*

/** Durable automatic grading. Scheduling is separate from these direction-specific counters. */
class TypingRepository internal constructor(
    private val database: TrainerDatabase,
    private val clock: Clock = Clock.systemUTC(),
    random: Random = Random.Default,
) {
    private val dao = database.typingDao()
    private val review = database.reviewDao()
    private val scheduler = ReviewScheduler(clock)
    private val planner = TypingPlanner(random)
    fun observeAvailable(scope: StudyScope = StudyScope.MIXED): Flow<Long> = ReviewRepository(database, clock).observeTypingAvailable(scope)
    fun observe(id: Long): Flow<TypingState?> = dao.observe(id).map { it?.toState() }.flowOn(Dispatchers.Default)
    suspend fun restore(preferred: Long? = null): TypingState? = database.withTransaction {
        val requested = preferred?.let { dao.graph(it) }
        if (requested?.session?.status == TypingStatus.IN_PROGRESS.name) return@withTransaction requested.toState()
        val recent = dao.recoverableId()?.let { dao.graph(it) }
        if (recent?.session?.status == TypingStatus.IN_PROGRESS.name) recent.toState() else (requested ?: recent)?.toState()
    }
    suspend fun get(id: Long): TypingState = database.withTransaction { graph(id).toState() }
    suspend fun mistakes(id: Long): List<TypingMistake> = get(id).let {
        if (it.active) fail(TypingError.RESULTS_UNAVAILABLE)
        it.mistakes
    }
    suspend fun progress(wordId: Long, direction: TypingDirection): TypingProgress? = dao.progress(wordId, direction.name)?.let {
        TypingProgress(it.wordId, direction, it.attempts, it.correct, it.consecutiveCorrect, Instant.ofEpochMilli(it.lastSubmittedAt))
    }
    suspend fun start(requestId: String, allowShorter: Boolean = false, scope: StudyScope = StudyScope.MIXED): TypingState {
        val request = try { UUID.fromString(requestId).toString() } catch (_: IllegalArgumentException) { fail(TypingError.INVALID_REQUEST) }
        return database.withTransaction {
            dao.forRequest(request)?.let { previous ->
                if (previous.allowShorter != allowShorter || previous.scope != scope.name) fail(TypingError.START_REQUEST_CHANGED)
                return@withTransaction graph(previous.id).toState()
            }
            val at = scheduler.now(); val rows = review.words()
            val bg = rows.filter { it.mode == ReviewMode.TYPING_BG_TO_RU.name }.associate { it.wordId to scheduler.category(it.schedule(), at) }
            val ru = rows.filter { it.mode == ReviewMode.TYPING_RU_TO_BG.name }.associate { it.wordId to scheduler.category(it.schedule(), at) }
            val plan = planner.plan(dao.candidateIds(), bg, ru, scope)
            if (plan.isEmpty()) fail(when (scope) { StudyScope.DUE -> TypingError.EMPTY_REVIEW_QUEUE; StudyScope.NEW -> TypingError.EMPTY_NEW_QUEUE; StudyScope.MIXED -> TypingError.EMPTY_DICTIONARY })
            if (plan.size < 30 && !allowShorter) fail(TypingError.SHORTER_SESSION_REQUIRED)
            // Only the <=30 selected relations are hydrated; deterministic query avoids Room RANDOM/reset bugs.
            val words = dao.candidates(plan.map { it.wordId }).associateBy { it.word.id }
            val id = dao.insertSession(TypingSessionEntity(requestId = request, allowShorter = allowShorter,
                scope = scope.name, total = plan.size, startedAt = clock.millis()))
            plan.forEachIndexed { index, item ->
                val word = words.getValue(item.wordId)
                val translations = word.translations.sortedWith(compareByDescending<TranslationEntity> { it.isPrimary }.thenBy { it.id })
                    .map { JsonPrimitive(it.russianText) }
                dao.insertQuestion(TypingQuestionEntity(sessionId = id, wordId = item.wordId, position = index + 1,
                    direction = item.direction.name, bulgarian = word.word.bulgarianText, russianJson = JsonArray(translations).toString()))
            }
            graph(id).toState()
        }
    }
    suspend fun submit(sessionId: Long, questionId: Long, submitted: String): TypingState = database.withTransaction {
        val session = session(sessionId)
        val question = question(sessionId, questionId)
        // Raw text identity, not normalized equality, determines replay; no new attempt even after completion.
        dao.attempt(questionId)?.let {
            if (it.submitted != submitted) fail(TypingError.ANSWER_CONFLICT)
            return@withTransaction graph(sessionId).toState()
        }
        requireActive(session)
        requireCurrent(session, question)
        val normalized = TypingEvaluator.key(submitted)
        if (submitted.length > 4000 || normalized.isEmpty()) fail(TypingError.INVALID_REQUEST)
        val correct = TypingEvaluator.accepts(submitted, question.accepted())
        val at = clock.millis()
        dao.insertAttempt(TypingAttemptEntity(questionId = questionId, submitted = submitted, normalizedAnswer = normalized,
            correct = correct, submittedAt = at))
        val previous = dao.progress(question.wordId, question.direction)
        dao.saveProgress(TypingProgressEntity(question.wordId, question.direction, (previous?.attempts ?: 0) + 1,
            (previous?.correct ?: 0) + if (correct) 1 else 0,
            if (correct) (previous?.consecutiveCorrect ?: 0) + 1 else 0, at))
        val mode = if (question.direction == TypingDirection.BG_TO_RU.name) ReviewMode.TYPING_BG_TO_RU else ReviewMode.TYPING_RU_TO_BG
        review.save(scheduler.record(review.word(question.wordId, mode.name)?.schedule(), correct, Instant.ofEpochMilli(at)).wordEntity(question.wordId, mode.name))
        val last = question.position == session.total
        dao.updateSession(session.copy(revision = session.revision + 1,
            status = if (last) TypingStatus.COMPLETED.name else session.status, completedAt = if (last) at else null))
        graph(sessionId).toState() // Feedback, including final feedback, stays until acknowledged.
    }
    suspend fun next(sessionId: Long, questionId: Long): TypingState = database.withTransaction {
        val session = session(sessionId)
        val question = question(sessionId, questionId)
        if (question.acknowledged) return@withTransaction graph(sessionId).toState()
        if (session.status == TypingStatus.ABANDONED.name) fail(TypingError.SESSION_CLOSED)
        requireCurrent(session, question)
        if (dao.attempt(questionId) == null) fail(TypingError.ANSWER_REQUIRED)
        dao.updateQuestion(question.copy(acknowledged = true))
        dao.updateSession(session.copy(currentPosition = session.currentPosition + 1, revision = session.revision + 1))
        graph(sessionId).toState()
    }
    suspend fun complete(sessionId: Long): TypingState = database.withTransaction {
        val session = session(sessionId)
        // A completed session may still have its final feedback. Closing only acknowledges saved answers.
        if (session.status != TypingStatus.ABANDONED.name && session.currentPosition <= session.total) {
            dao.acknowledgeAnswered(sessionId)
            dao.updateSession(session.copy(currentPosition = session.total + 1, revision = session.revision + 1,
                status = if (session.status == TypingStatus.COMPLETED.name) session.status else TypingStatus.ABANDONED.name,
                completedAt = session.completedAt ?: clock.millis()))
        }
        graph(sessionId).toState()
    }
    private suspend fun session(id: Long) = dao.session(id) ?: fail(TypingError.SESSION_NOT_FOUND)
    private suspend fun graph(id: Long) = dao.graph(id) ?: fail(TypingError.SESSION_NOT_FOUND)
    private suspend fun question(session: Long, id: Long) = dao.question(session, id) ?: fail(TypingError.STALE_QUESTION)
    private fun requireActive(session: TypingSessionEntity) { if (session.status != TypingStatus.IN_PROGRESS.name) fail(TypingError.SESSION_CLOSED) }
    private fun requireCurrent(session: TypingSessionEntity, question: TypingQuestionEntity) {
        if (question.position != session.currentPosition || question.acknowledged) fail(TypingError.STALE_QUESTION)
    }
    private fun TypingSessionGraph.toState(): TypingState {
        val ordered = questions.sortedBy { it.question.position }
        val current = if (session.status == TypingStatus.ABANDONED.name) null else ordered.firstOrNull {
            it.question.position == session.currentPosition && !it.question.acknowledged
        }
        val question = current?.let {
            TypingQuestion(it.question.id, it.question.position, TypingDirection.valueOf(it.question.direction), it.question.prompt(),
                it.attempt?.let { attempt -> TypingFeedback(attempt.correct, attempt.submitted, it.question.accepted()) })
        }
        val mistakes = if (session.status == TypingStatus.IN_PROGRESS.name) emptyList() else ordered.mapNotNull {
            if (it.attempt?.correct != false) null else TypingMistake(it.question.id, it.question.position,
                TypingDirection.valueOf(it.question.direction), it.question.prompt(), it.attempt.submitted, it.question.accepted())
        }
        return TypingState(session.id, TypingStatus.valueOf(session.status), session.total, questions.count { it.attempt != null },
            questions.count { it.attempt?.correct == true }, session.currentPosition, session.revision, question, mistakes, StudyScope.valueOf(session.scope),
            ordered.count { it.question.direction == TypingDirection.BG_TO_RU.name }, ordered.count { it.question.direction == TypingDirection.RU_TO_BG.name })
    }
    private fun TypingQuestionEntity.russian() = Json.parseToJsonElement(russianJson).jsonArray.map { it.jsonPrimitive.content }
    private fun TypingQuestionEntity.prompt() = if (direction == TypingDirection.BG_TO_RU.name) listOf(bulgarian) else russian()
    private fun TypingQuestionEntity.accepted() = if (direction == TypingDirection.BG_TO_RU.name) russian() else listOf(bulgarian)
    private fun fail(code: TypingError): Nothing = throw TypingException(code)
}
