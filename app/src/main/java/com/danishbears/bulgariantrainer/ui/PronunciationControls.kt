package com.danishbears.bulgariantrainer.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danishbears.bulgariantrainer.R
import com.danishbears.bulgariantrainer.pronunciation.*

private val LocalPronunciation = staticCompositionLocalOf<BulgarianPronunciation> { error("PronunciationHost required") }

@Composable
fun PronunciationHost(content: @Composable () -> Unit) {
    val application = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val speech = remember(application, scope) { BulgarianPronunciation(scope, { AndroidTtsAdapter(application) }) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(speech, lifecycle) {
        speech.initialize()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) speech.stop()
            if (event == Lifecycle.Event.ON_RESUME && speech.state.value.status in setOf(PronunciationStatus.ERROR,
                    PronunciationStatus.MISSING_DATA, PronunciationStatus.UNSUPPORTED, PronunciationStatus.NETWORK_ONLY)) speech.initialize()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); speech.close() }
    }
    CompositionLocalProvider(LocalPronunciation provides speech, content = content)
}

/** Question/session/route changes cancel even when the old lazy row has left the viewport. */
@Composable
internal fun PronunciationBoundary(key: Any?) {
    val speech = LocalPronunciation.current
    DisposableEffect(speech, key) { onDispose { speech.stop() } }
}

@Composable
internal fun PronunciationPlayback(target: PronunciationTarget?) {
    if (target == null) return
    val speech = LocalPronunciation.current
    val state by speech.state.collectAsStateWithLifecycle()
    DisposableEffect(speech, target) {
        speech.register(target)
        onDispose { speech.unregister(target.owner) }
    }
    val own = state.owner == target.owner
    val available = state.status == PronunciationStatus.READY || state.status == PronunciationStatus.SPEAKING
    val normalLabel = stringResource(R.string.pronunciation_listen_label, target.text)
    val slowLabel = stringResource(R.string.pronunciation_slow_label, target.text)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = available, onClick = { speech.speak(target.owner) }, modifier = Modifier.semantics { contentDescription = normalLabel }) {
                Text(stringResource(R.string.pronunciation_listen))
            }
            OutlinedButton(enabled = available, onClick = { speech.speak(target.owner, slow = true) }, modifier = Modifier.semantics { contentDescription = slowLabel }) {
                Text(stringResource(R.string.pronunciation_slow))
            }
        }
        if (own) TextButton(onClick = speech::stop) { Text(stringResource(R.string.pronunciation_stop)) }
        if (own || !available) Text(pronunciationStatusLabel(state.status),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun PronunciationSettings() {
    val speech = LocalPronunciation.current
    val state by speech.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.pronunciation_settings)) }
    if (expanded) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pronunciationStatusLabel(state.status), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        state.voice?.let { Text(stringResource(R.string.pronunciation_voice, it)) }
        Text(stringResource(R.string.pronunciation_setup_hint))
        Text(stringResource(R.string.pronunciation_optional))
        TextButton(onClick = { openSpeechSetup(context, install = false) }) { Text(stringResource(R.string.pronunciation_open_settings)) }
        TextButton(onClick = { openSpeechSetup(context, install = true) }) { Text(stringResource(R.string.pronunciation_install)) }
        TextButton(enabled = state.status != PronunciationStatus.INITIALIZING, onClick = speech::initialize) {
            Text(stringResource(R.string.pronunciation_retry))
        }
    }
}

private fun openSpeechSetup(context: Context, install: Boolean) {
    val action = if (install) TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA else "com.android.settings.TTS_SETTINGS"
    try { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: ActivityNotFoundException) { openGeneralSettings(context) }
    catch (_: SecurityException) { openGeneralSettings(context) }
}
private fun openGeneralSettings(context: Context) {
    try { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: ActivityNotFoundException) { Toast.makeText(context, R.string.pronunciation_settings_unavailable, Toast.LENGTH_LONG).show() }
    catch (_: SecurityException) { Toast.makeText(context, R.string.pronunciation_settings_unavailable, Toast.LENGTH_LONG).show() }
}
@Composable
private fun pronunciationStatusLabel(status: PronunciationStatus) = stringResource(when (status) {
    PronunciationStatus.INITIALIZING -> R.string.pronunciation_initializing
    PronunciationStatus.READY -> R.string.pronunciation_ready
    PronunciationStatus.SPEAKING -> R.string.pronunciation_speaking
    PronunciationStatus.MISSING_DATA -> R.string.pronunciation_missing
    PronunciationStatus.UNSUPPORTED -> R.string.pronunciation_unsupported
    PronunciationStatus.NETWORK_ONLY -> R.string.pronunciation_network_only
    PronunciationStatus.ERROR -> R.string.pronunciation_error
    PronunciationStatus.CLOSED -> R.string.pronunciation_closed
})
