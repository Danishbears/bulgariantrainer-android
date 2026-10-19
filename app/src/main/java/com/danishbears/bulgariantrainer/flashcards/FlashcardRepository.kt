package com.danishbears.bulgariantrainer.flashcards

import androidx.room.withTransaction
import com.danishbears.bulgariantrainer.data.*
import com.danishbears.bulgariantrainer.review.*
import com.danishbears.bulgariantrainer.data.FlashcardAttemptEntity
import com.danishbears.bulgariantrainer.data.FlashcardCardEntity
import com.danishbears.bulgariantrainer.data.FlashcardSessionEntity
import com.danishbears.bulgariantrainer.data.FlashcardSessionGraph
import com.danishbears.bulgariantrainer.data.RecognitionProgressEntity
import com.danishbears.bulgariantrainer.data.TrainerDatabase
import com.danishbears.bulgariantrainer.data.TranslationEntity
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Durable recognition practice. All state transitions and their counters commit atomically.
 * Review scheduling is intentionally absent; future schedules belong to a separate component/table.
 */
class FlashcardRepository internal constructor(
    private val database: TrainerDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val random: Random = Random.Default,
) {
    private val dao = database.flashcardDao()
    private val review = database.reviewDao()
    private val scheduler = ReviewScheduler(clock)
    fun observeAvailable(scope: StudyScope = StudyScope.MIXED): Flow<Long> = ReviewRepository(database, clock).observeAvailable(ReviewMode.FLASHCARD, scope)
    fun observe(id: Long): Flow<FlashcardState?> = dao.observe(id).map { it?.toState() }.flowOn(Dispatchers.Default)

    suspend fun restore(preferred: Long? = null): FlashcardState? = database.withTransaction {
        val requested = preferred?.let { dao.graph(it) }
        if (requested?.session?.status == FlashcardStatus.IN_PROGRESS.name) return@withTransaction requested.toState()
        val recent = dao.recoverableId()?.let { dao.graph(it) }
        if (recent?.session?.status == FlashcardStatus.IN_PROGRESS.name) recent.toState()
        else (requested ?: recent)?.toState()
    }
    suspend fun get(id: Long): FlashcardState = database.withTransaction { graph(id).toState() }
    suspend fun results(id: Long): List<FlashcardResult> = get(id).let {
        if (it.active) fail(FlashcardError.RESULTS_UNAVAILABLE)
        it.results
    }
    suspend fun progress(wordId: Long): RecognitionProgress? = dao.progress(wordId)?.toProgress()

    suspend fun start(requestId: String, settings: FlashcardSettings = FlashcardSettings()): FlashcardState {
        val request = try { UUID.fromString(requestId).toString() } catch (_: IllegalArgumentException) { fail(FlashcardError.INVALID_SETTINGS) }
        if (settings.size !in 1..100) fail(FlashcardError.INVALID_SETTINGS)
        return database.withTransaction {
            dao.forRequest(request)?.let { previous ->
                if (previous.requestedSize != settings.size || previous.direction != settings.direction.name || previous.scope != settings.scope.name)
                    fail(FlashcardError.START_REQUEST_CHANGED)
                return@withTransaction graph(previous.id).toState()
            }
            val at = scheduler.now()
            val categories = review.words().filter { it.mode == ReviewMode.FLASHCARD.name }.associate { it.wordId to scheduler.category(it.schedule(), at) }
            val ids = ReviewSelection.flashcards(review.wordIds(), categories, settings.scope, settings.size, random)
            if (ids.isEmpty()) fail(when (settings.scope) { StudyScope.DUE -> FlashcardError.EMPTY_REVIEW_QUEUE; StudyScope.NEW -> FlashcardError.EMPTY_NEW_QUEUE; StudyScope.MIXED -> FlashcardError.EMPTY_DICTIONARY })
            // Room relation mapping may reset/re-execute its parent statement. Never put RANDOM()
            // in that relation query: the second pass could produce different IDs without child maps.
            val records = dao.candidatesByIds(ids).associateBy { it.word.id }
            val selected = ids.map { records.getValue(it) }
            val id = dao.insertSession(FlashcardSessionEntity(requestId = request, requestedSize = settings.size,
                direction = settings.direction.name, scope = settings.scope.name, total = selected.size, startedAt = clock.millis()))
            val firstBulgarian = random.nextBoolean()
            selected.forEachIndexed { index, word ->
                val russian = word.translations.sortedWith(compareByDescending<TranslationEntity> { it.isPrimary }.thenBy { it.id })
                    .map { JsonPrimitive(it.russianText) }
                dao.insertCard(FlashcardCardEntity(sessionId = id, wordId = word.word.id, position = index + 1,
                    direction = cardDirection(settings.direction, index, firstBulgarian).name,
                    bulgarian = word.word.bulgarianText, russianJson = JsonArray(russian).toString()))
            }
            graph(id).toState()
        }
    }

    suspend fun reveal(sessionId: Long, cardId: Long): FlashcardState = database.withTransaction {
        val session = session(sessionId)
        requireActive(session)
        val card = dao.card(sessionId, cardId) ?: fail(FlashcardError.STALE_CARD)
        if (card.position != session.currentPosition) fail(FlashcardError.STALE_CARD)
        if (card.revealedAt == null) dao.updateCard(card.copy(revealedAt = clock.millis()))
        graph(sessionId).toState()
    }
    suspend fun rate(sessionId: Long, cardId: Long, known: Boolean): FlashcardState = database.withTransaction {
        val session = session(sessionId)
        val card = dao.card(sessionId, cardId) ?: fail(FlashcardError.STALE_CARD)
        dao.attempt(cardId)?.let { attempt ->
            if (attempt.known != known) fail(FlashcardError.RATING_CONFLICT)
            return@withTransaction graph(sessionId).toState() // Also valid after completion/abandonment.
        }
        requireActive(session)
        if (card.position != session.currentPosition) fail(FlashcardError.STALE_CARD)
        if (card.revealedAt == null) fail(FlashcardError.ANSWER_HIDDEN)
        val at = clock.instant()
        dao.insertAttempt(FlashcardAttemptEntity(cardId = cardId, known = known, ratedAt = at.toEpochMilli()))
        val progress = RecognitionUpdates.record(dao.progress(card.wordId)?.toProgress(), card.wordId, known, at)
        dao.saveProgress(RecognitionProgressEntity(progress.wordId, progress.attempts, progress.known,
            progress.consecutiveKnown, progress.lastRatedAt.toEpochMilli()))
        val scheduled = scheduler.record(review.word(card.wordId, ReviewMode.FLASHCARD.name)?.schedule(), known, at)
        review.save(scheduled.wordEntity(card.wordId, ReviewMode.FLASHCARD.name))
        val next = session.currentPosition + 1
        dao.updateSession(session.copy(currentPosition = next,
            status = if (next > session.total) FlashcardStatus.COMPLETED.name else FlashcardStatus.IN_PROGRESS.name,
            completedAt = if (next > session.total) at.toEpochMilli() else null))
        graph(sessionId).toState()
    }
    suspend fun complete(sessionId: Long): FlashcardState = database.withTransaction {
        val session = session(sessionId)
        if (session.status == FlashcardStatus.IN_PROGRESS.name) dao.updateSession(session.copy(
            status = if (session.currentPosition <= session.total) FlashcardStatus.ABANDONED.name else FlashcardStatus.COMPLETED.name,
            completedAt = clock.millis()))
        graph(sessionId).toState()
    }
    private suspend fun session(id: Long) = dao.session(id) ?: fail(FlashcardError.SESSION_NOT_FOUND)
    private suspend fun graph(id: Long) = dao.graph(id) ?: fail(FlashcardError.SESSION_NOT_FOUND)
    private fun requireActive(session: FlashcardSessionEntity) {
        if (session.status != FlashcardStatus.IN_PROGRESS.name) fail(FlashcardError.SESSION_CLOSED)
    }
    private fun FlashcardSessionGraph.toState(): FlashcardState {
        val ordered = cards.sortedBy { it.card.position }
        val answered = cards.count { it.attempt != null }
        val known = cards.count { it.attempt?.known == true }
        val status = FlashcardStatus.valueOf(session.status)
        val current = if (status == FlashcardStatus.IN_PROGRESS) ordered.firstOrNull { it.card.position == session.currentPosition }?.card else null
        val question = current?.let { card ->
            val bg = card.direction == CardDirection.BG_TO_RU.name
            FlashcardQuestion(card.id, card.position, CardDirection.valueOf(card.direction),
                if (bg) listOf(card.bulgarian) else card.russian(), card.revealedAt != null,
                if (card.revealedAt == null) emptyList() else if (bg) card.russian() else listOf(card.bulgarian))
        }
        val results = if (status == FlashcardStatus.IN_PROGRESS) emptyList() else ordered.map {
            FlashcardResult(it.card.id, it.card.position, CardDirection.valueOf(it.card.direction), it.card.bulgarian,
                it.card.russian(), when (it.attempt?.known) { true -> CardResult.CORRECT; false -> CardResult.INCORRECT; null -> CardResult.SKIPPED })
        }
        return FlashcardState(session.id, FlashcardSettings(session.requestedSize, FlashcardDirection.valueOf(session.direction), StudyScope.valueOf(session.scope)),
            status, session.total, answered, known, answered - known, session.total - answered, session.currentPosition,
            Instant.ofEpochMilli(session.startedAt), session.completedAt?.let(Instant::ofEpochMilli), question, results)
    }
    private fun FlashcardCardEntity.russian() = Json.parseToJsonElement(russianJson).jsonArray.map { it.jsonPrimitive.content }
    private fun RecognitionProgressEntity.toProgress() = RecognitionProgress(wordId, attempts, known, consecutiveKnown, Instant.ofEpochMilli(lastRatedAt))
    private fun fail(code: FlashcardError): Nothing = throw FlashcardException(code)
}
