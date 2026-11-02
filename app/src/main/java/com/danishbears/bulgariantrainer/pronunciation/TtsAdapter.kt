package com.danishbears.bulgariantrainer.pronunciation

data class BulgarianVoice(val id: String, val language: String, val networkRequired: Boolean,
    val installed: Boolean = true, val quality: Int = 0, val latency: Int = 0)
enum class LanguageAvailability { AVAILABLE, MISSING_DATA, UNSUPPORTED }
enum class SpeechEvent { STARTED, DONE, ERROR, STOPPED }

/** Android callbacks are marshalled to the main thread; fake adapters need no speech engine. */
interface TtsAdapter {
    fun initialize(onInitialized: (Boolean) -> Unit, onUtterance: (String, SpeechEvent) -> Unit)
    fun languageAvailability(): LanguageAvailability
    fun voices(): List<BulgarianVoice>
    fun selectVoice(voice: BulgarianVoice): Boolean
    fun speak(text: String, rate: Float, utteranceId: String): Boolean
    fun stop(): Boolean
    fun shutdown()
}
