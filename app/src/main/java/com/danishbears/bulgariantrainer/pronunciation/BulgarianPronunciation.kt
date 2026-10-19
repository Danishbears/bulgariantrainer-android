package com.danishbears.bulgariantrainer.pronunciation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PronunciationStatus { INITIALIZING, READY, SPEAKING, MISSING_DATA, UNSUPPORTED, NETWORK_ONLY, ERROR, CLOSED }
data class PronunciationState(val status: PronunciationStatus = PronunciationStatus.INITIALIZING,
    val voice: String? = null, val owner: String? = null)
data class PronunciationTarget(val owner: String, val text: String)

/** Activity-owned, main-thread coordinator. Audio never changes saved learning records. */
class BulgarianPronunciation(private val scope: CoroutineScope, private val factory: () -> TtsAdapter,
    private val initializationTimeoutMs: Long = 10_000) {
    private val mutable = MutableStateFlow(PronunciationState())
    val state = mutable.asStateFlow()
    private var adapter: TtsAdapter? = null
    private var generation = 0L
    private var sequence = 0L
    private var timeout: Job? = null
    private var closed = false
    private var initialized = false
    private var activeId: String? = null
    private val targets = mutableMapOf<String, String>()

    fun initialize() {
        if (closed) return
        generation++
        val expected = generation
        timeout?.cancel()
        activeId = null
        initialized = false
        runCatching { adapter?.shutdown() }
        adapter = null
        mutable.value = PronunciationState()
        try {
            val engine = factory()
            adapter = engine
            timeout = scope.launch {
                delay(initializationTimeoutMs)
                if (generation == expected && !initialized && !closed) {
                    generation++ // Ignore an engine that finishes after its timeout.
                    runCatching { engine.shutdown() }
                    adapter = null
                    mutable.value = PronunciationState(PronunciationStatus.ERROR)
                }
            }
            engine.initialize(onInit@{ success ->
                if (closed || generation != expected) return@onInit
                timeout?.cancel()
                if (!success) {
                    generation++
                    runCatching { engine.shutdown() }
                    adapter = null
                    mutable.value = PronunciationState(PronunciationStatus.ERROR)
                } else {
                    initialized = true
                    discover(engine)
                }
            }, { id, event ->
                if (!closed && generation == expected && activeId == id) {
                    when (event) {
                        SpeechEvent.STARTED -> Unit
                        SpeechEvent.DONE, SpeechEvent.STOPPED -> {
                            activeId = null
                            mutable.value = mutable.value.copy(status = PronunciationStatus.READY, owner = null)
                        }
                        SpeechEvent.ERROR -> {
                            activeId = null
                            mutable.value = mutable.value.copy(status = PronunciationStatus.ERROR, owner = null)
                        }
                    }
                }
            })
        } catch (_: Exception) {
            generation++
            timeout?.cancel()
            runCatching { adapter?.shutdown() }
            adapter = null
            mutable.value = PronunciationState(PronunciationStatus.ERROR)
        }
    }

    private fun discover(engine: TtsAdapter): Boolean = try {
        val availability = engine.languageAvailability()
        val bulgarian = engine.voices().filter { it.language.equals("bg", ignoreCase = true) || it.language.startsWith("bg-", ignoreCase = true) }
        val local = bulgarian.filter { it.installed && !it.networkRequired }
            .sortedWith(compareByDescending<BulgarianVoice> { it.quality }.thenBy { it.latency }.thenBy { it.id })
        val status = when {
            availability == LanguageAvailability.UNSUPPORTED -> PronunciationStatus.UNSUPPORTED
            availability == LanguageAvailability.MISSING_DATA -> PronunciationStatus.MISSING_DATA
            local.isNotEmpty() -> PronunciationStatus.READY
            bulgarian.any { it.networkRequired } -> PronunciationStatus.NETWORK_ONLY
            else -> PronunciationStatus.MISSING_DATA
        }
        // Do not call setLanguage: some engines silently select a different language.
        val voice = if (status == PronunciationStatus.READY) local.firstOrNull { engine.selectVoice(it) } else null
        mutable.value = PronunciationState(if (status == PronunciationStatus.READY && voice == null) PronunciationStatus.ERROR else status, voice?.id)
        voice != null
    } catch (_: Exception) {
        mutable.value = PronunciationState(PronunciationStatus.ERROR)
        false
    }

    fun register(target: PronunciationTarget) {
        if (closed) return
        val cleaned = target.text.trim()
        if (cleaned.isEmpty()) { unregister(target.owner); return }
        if (targets[target.owner] != cleaned && mutable.value.owner == target.owner) stop()
        targets[target.owner] = cleaned
    }
    fun unregister(owner: String) {
        if (mutable.value.owner == owner) stop()
        targets.remove(owner)
    }
    fun speak(owner: String, slow: Boolean = false): Boolean {
        val text = targets[owner] ?: return false
        val engine = adapter ?: return false
        if (closed || !initialized) return false // Never queue taps made during initialization.
        activeId = null // Ignore cancellation callbacks from the previous utterance.
        return try {
            if (!engine.stop()) {
                mutable.value = PronunciationState(PronunciationStatus.ERROR)
                false
            } else if (!discover(engine)) false
            else {
                val id = "bg-$generation-${++sequence}"
                activeId = id
                mutable.value = mutable.value.copy(status = PronunciationStatus.SPEAKING, owner = owner)
                if (engine.speak(text, if (slow) 0.5f else 1f, id)) true
                else {
                    activeId = null
                    mutable.value = mutable.value.copy(status = PronunciationStatus.ERROR, owner = null)
                    runCatching { engine.stop() }
                    false
                }
            }
        } catch (_: Exception) {
            activeId = null
            runCatching { engine.stop() }
            mutable.value = PronunciationState(PronunciationStatus.ERROR)
            false
        }
    }
    fun stop() {
        if (closed || activeId == null) return
        activeId = null
        val success = runCatching { adapter?.stop() == true }.getOrDefault(false)
        mutable.value = mutable.value.copy(status = if (success) PronunciationStatus.READY else PronunciationStatus.ERROR, owner = null)
    }
    fun close() {
        if (closed) return
        closed = true
        generation++
        timeout?.cancel()
        activeId = null
        targets.clear()
        runCatching { adapter?.shutdown() }
        adapter = null
        mutable.value = PronunciationState(PronunciationStatus.CLOSED)
    }
}
