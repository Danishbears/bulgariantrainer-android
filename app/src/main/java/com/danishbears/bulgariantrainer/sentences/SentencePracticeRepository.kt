package com.danishbears.bulgariantrainer.sentences

import androidx.room.withTransaction
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

/** One answer row; a pending mismatch is finalized in place, exactly once. No vocabulary progress writes. */
class SentencePracticeRepository internal constructor(private val database: TrainerDatabase,
    private val clock: Clock = Clock.systemUTC(), private val random: Random = Random.Default) {
    private val dao = database.sentenceDao()
    fun observeAvailable(): Flow<Long> = dao.observeAvailable()
    fun observe(id: Long): Flow<SentencePracticeState?> = dao.observe(id).map { it?.toState() }.flowOn(Dispatchers.Default)
    suspend fun restore(preferred: Long? = null): SentencePracticeState? = database.withTransaction {
        val requested = preferred?.let { dao.graph(it) }
        if (requested?.session?.status == SentenceStatus.IN_PROGRESS.name) return@withTransaction requested.toState()
        val recent = dao.recoverableId()?.let { dao.graph(it) }
        if (recent?.session?.status == SentenceStatus.IN_PROGRESS.name) recent.toState() else (requested ?: recent)?.toState()
    }
    suspend fun get(id: Long): SentencePracticeState = database.withTransaction { graph(id).toState() }
    suspend fun review(id: Long): List<SentenceReview> = get(id).let {
        if (it.active) fail(SentenceError.RESULTS_UNAVAILABLE)
        it.review
    }
    suspend fun progress(id: Long): SentenceProgress? = dao.progress(id)?.let {
        SentenceProgress(id, it.attempts, it.correct, it.partial, it.incorrect, it.consecutiveCorrect, Instant.ofEpochMilli(it.lastEvaluatedAt))
    }
    suspend fun start(requestId: String, size: Int = 10): SentencePracticeState {
        val request = try { UUID.fromString(requestId).toString() } catch (_: IllegalArgumentException) { fail(SentenceError.INVALID_SETTINGS) }
        if (size !in 1..100) fail(SentenceError.INVALID_SETTINGS)
        return database.withTransaction {
            dao.forRequest(request)?.let {
                if (it.requestedSize != size) fail(SentenceError.START_REQUEST_CHANGED)
                return@withTransaction graph(it.id).toState()
            }
            val ids = dao.candidateIds().shuffled(random).take(size)
            if (ids.isEmpty()) fail(SentenceError.EMPTY_SENTENCES)
            val selected = dao.candidates(ids).associateBy { it.sentence.id }
            val id = dao.insertSession(SentenceSessionEntity(requestId = request, requestedSize = size, total = ids.size, startedAt = clock.millis()))
            ids.forEachIndexed { index, sentenceId ->
                val sentence = selected.getValue(sentenceId).toRecord().input
                dao.insertQuestion(SentenceQuestionEntity(sessionId = id, sentenceId = sentenceId, position = index + 1,
                    russian = sentence.russian, acceptedJson = JsonArray((listOf(sentence.bulgarian) + sentence.alternatives).map(::JsonPrimitive)).toString(),
                    explanation = sentence.explanation, difficulty = sentence.difficulty, topic = sentence.topic))
            }
            graph(id).toState()
        }
    }
    suspend fun submit(sessionId: Long, questionId: Long, submitted: String): SentencePracticeState = database.withTransaction {
        val session = session(sessionId); val question = question(sessionId, questionId)
        dao.attempt(questionId)?.let {
            if (it.submitted != submitted) fail(SentenceError.ANSWER_CONFLICT)
            return@withTransaction graph(sessionId).toState()
        }
        requireActive(session); requireCurrent(session, question)
        val key = VocabularyText.key(submitted)
        if (submitted.length > 4000 || key.isEmpty()) fail(SentenceError.INVALID_ANSWER)
        val match = question.accepted().any { VocabularyText.key(it) == key }
        val at = clock.millis()
        dao.insertAttempt(SentenceAttemptEntity(questionId = questionId, submitted = submitted, normalizedAnswer = key,
            automaticMatch = match, submittedAt = at, evaluatedAt = if (match) at else null))
        if (match) record(question.sentenceId, SentenceRating.CORRECT, at)
        finishMutation(session, match && question.position == session.total, at)
        graph(sessionId).toState()
    }
    suspend fun selfRate(sessionId: Long, questionId: Long, rating: SentenceRating): SentencePracticeState = database.withTransaction {
        val session = session(sessionId); val question = question(sessionId, questionId)
        val attempt = dao.attempt(questionId) ?: fail(SentenceError.ANSWER_REQUIRED)
        if (attempt.automaticMatch) fail(SentenceError.EVALUATION_CONFLICT)
        attempt.selfRating?.let {
            if (it != rating.name) fail(SentenceError.EVALUATION_CONFLICT)
            return@withTransaction graph(sessionId).toState()
        }
        requireActive(session); requireCurrent(session, question)
        val at = clock.millis()
        dao.updateAttempt(attempt.copy(selfRating = rating.name, evaluatedAt = at))
        record(question.sentenceId, rating, at)
        finishMutation(session, question.position == session.total, at)
        graph(sessionId).toState()
    }
    suspend fun next(sessionId: Long, questionId: Long): SentencePracticeState = database.withTransaction {
        val session = session(sessionId); val question = question(sessionId, questionId)
        if (question.acknowledged) return@withTransaction graph(sessionId).toState()
        if (session.status == SentenceStatus.ABANDONED.name) fail(SentenceError.SESSION_CLOSED)
        requireCurrent(session, question)
        val attempt = dao.attempt(questionId)
        if (attempt == null || !attempt.automaticMatch && attempt.selfRating == null) fail(SentenceError.EVALUATION_REQUIRED)
        dao.updateQuestion(question.copy(acknowledged = true))
        dao.updateSession(session.copy(currentPosition = session.currentPosition + 1, revision = session.revision + 1))
        graph(sessionId).toState()
    }
    suspend fun complete(id: Long): SentencePracticeState = database.withTransaction {
        val session = session(id)
        if (session.status != SentenceStatus.ABANDONED.name && session.currentPosition <= session.total) {
            dao.acknowledgeSubmitted(id)
            dao.updateSession(session.copy(currentPosition = session.total + 1, revision = session.revision + 1,
                status = if (session.status == SentenceStatus.COMPLETED.name) session.status else SentenceStatus.ABANDONED.name,
                completedAt = session.completedAt ?: clock.millis()))
        }
        graph(id).toState()
    }
    private suspend fun record(id: Long, rating: SentenceRating, at: Long) {
        val old = dao.progress(id)
        dao.saveProgress(SentenceProgressEntity(id, (old?.attempts ?: 0) + 1,
            (old?.correct ?: 0) + if (rating == SentenceRating.CORRECT) 1 else 0,
            (old?.partial ?: 0) + if (rating == SentenceRating.PARTIAL) 1 else 0,
            (old?.incorrect ?: 0) + if (rating == SentenceRating.INCORRECT) 1 else 0,
            if (rating == SentenceRating.CORRECT) (old?.consecutiveCorrect ?: 0) + 1 else 0, at))
    }
    private suspend fun finishMutation(session: SentenceSessionEntity, last: Boolean, at: Long) {
        dao.updateSession(session.copy(revision = session.revision + 1,
            status = if (last) SentenceStatus.COMPLETED.name else session.status, completedAt = if (last) at else null))
    }
    private suspend fun session(id: Long) = dao.session(id) ?: fail(SentenceError.SESSION_NOT_FOUND)
    private suspend fun graph(id: Long) = dao.graph(id) ?: fail(SentenceError.SESSION_NOT_FOUND)
    private suspend fun question(session: Long, id: Long) = dao.question(session, id) ?: fail(SentenceError.STALE_QUESTION)
    private fun requireActive(session: SentenceSessionEntity) { if (session.status != SentenceStatus.IN_PROGRESS.name) fail(SentenceError.SESSION_CLOSED) }
    private fun requireCurrent(session: SentenceSessionEntity, question: SentenceQuestionEntity) {
        if (question.position != session.currentPosition || question.acknowledged) fail(SentenceError.STALE_QUESTION)
    }
    private fun SentenceQuestionEntity.accepted() = Json.parseToJsonElement(acceptedJson).jsonArray.map { it.jsonPrimitive.content }
    private fun SentenceQuestionWithAttempt.feedback(): SentenceFeedback? = attempt?.let {
        SentenceFeedback(it.submitted, it.automaticMatch, it.selfRating?.let(SentenceRating::valueOf), question.accepted(), question.explanation)
    }
    private fun SentenceSessionGraph.toState(): SentencePracticeState {
        val ordered = questions.sortedBy { it.question.position }
        val feedback = ordered.mapNotNull { it.feedback() }
        val current = if (session.status == SentenceStatus.ABANDONED.name) null else ordered.firstOrNull {
            it.question.position == session.currentPosition && !it.question.acknowledged
        }
        val question = current?.let { SentenceQuestion(it.question.id, it.question.position, it.question.russian, it.feedback()) }
        val review = if (session.status == SentenceStatus.IN_PROGRESS.name) emptyList() else ordered.map {
            SentenceReview(it.question.id, it.question.position, it.question.russian, it.feedback(), it.question.accepted(), it.question.explanation)
        }
        return SentencePracticeState(session.id, SentenceStatus.valueOf(session.status), session.requestedSize, session.total,
            feedback.size, feedback.count { !it.needsSelfAssessment }, feedback.count { it.result == SentenceResult.CORRECT },
            feedback.count { it.result == SentenceResult.PARTIAL }, feedback.count { it.result == SentenceResult.INCORRECT },
            feedback.count { it.needsSelfAssessment }, session.currentPosition, session.revision, question, review)
    }
    private fun fail(code: SentenceError): Nothing = throw SentenceException(code)
}
