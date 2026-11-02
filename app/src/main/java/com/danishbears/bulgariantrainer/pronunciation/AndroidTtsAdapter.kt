package com.danishbears.bulgariantrainer.pronunciation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

class AndroidTtsAdapter(context: Context) : TtsAdapter {
    private val application = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var released = false
    override fun initialize(onInitialized: (Boolean) -> Unit, onUtterance: (String, SpeechEvent) -> Unit) {
        check(engine == null && !released)
        engine = TextToSpeech(application) { result ->
            // Posting also handles an engine that calls back before its constructor returns.
            main.post {
                if (!released) {
                    val listening = try { engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        private fun event(id: String?, value: SpeechEvent) { main.post { if (!released && id != null) onUtterance(id, value) } }
                        override fun onStart(id: String?) = event(id, SpeechEvent.STARTED)
                        override fun onDone(id: String?) = event(id, SpeechEvent.DONE)
                        @Deprecated("Android legacy callback") override fun onError(id: String?) = event(id, SpeechEvent.ERROR)
                        override fun onError(id: String?, errorCode: Int) = event(id, SpeechEvent.ERROR)
                        override fun onStop(id: String?, interrupted: Boolean) = event(id, SpeechEvent.STOPPED)
                    }) == TextToSpeech.SUCCESS } catch (_: Exception) { false }
                    onInitialized(result == TextToSpeech.SUCCESS && listening)
                }
            }
        }
    }
    override fun languageAvailability() = when (engine?.isLanguageAvailable(Locale.forLanguageTag("bg-BG"))) {
        TextToSpeech.LANG_MISSING_DATA -> LanguageAvailability.MISSING_DATA
        TextToSpeech.LANG_NOT_SUPPORTED, null -> LanguageAvailability.UNSUPPORTED
        else -> LanguageAvailability.AVAILABLE
    }
    private fun installed(voice: Voice) = !voice.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
    override fun voices() = engine?.voices.orEmpty().map {
        BulgarianVoice(it.name, it.locale.toLanguageTag(), it.isNetworkConnectionRequired, installed(it), it.quality, it.latency)
    }
    override fun selectVoice(voice: BulgarianVoice): Boolean {
        val tts = engine ?: return false
        val actual = tts.voices?.firstOrNull { it.name == voice.id && it.locale.language.equals("bg", true) && !it.isNetworkConnectionRequired && installed(it) } ?: return false
        if (tts.setVoice(actual) != TextToSpeech.SUCCESS) return false
        val selected = tts.voice ?: return false
        return selected.name == actual.name && selected.locale.language.equals("bg", true) && !selected.isNetworkConnectionRequired && installed(selected)
    }
    override fun speak(text: String, rate: Float, utteranceId: String): Boolean {
        val tts = engine ?: return false
        val selected = tts.voice ?: return false
        if (!selected.locale.language.equals("bg", true) || selected.isNetworkConnectionRequired || !installed(selected)) return false
        if (tts.setSpeechRate(rate) != TextToSpeech.SUCCESS) return false
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS
    }
    override fun stop() = engine?.stop() == TextToSpeech.SUCCESS
    override fun shutdown() {
        if (released) return
        released = true
        val tts = engine
        engine = null
        try { tts?.stop() } finally { tts?.shutdown() }
    }
}
