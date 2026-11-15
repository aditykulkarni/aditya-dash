package com.example.opendash.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import com.example.opendash.util.DebugLog
import java.util.Locale

/**
 * Push-to-talk voice commands through the rider's earbuds.
 *
 * One press = one short listen. The Bluetooth headset microphone is opened only for
 * those few seconds, because while it is open earbuds drop music to call quality.
 * Recognition prefers Android's on-device recogniser (Android 12+), so commands such
 * as "next song" work with no signal.
 *
 * Main thread only: [SpeechRecognizer] requires it.
 */
class VoiceControl(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /** Listen once, then report the words heard and the command they map to. */
    fun listen(onResult: (heard: String, command: VoiceCommand) -> Unit) {
        if (listening) return
        if (!hasMicPermission()) {
            say("Allow the microphone in the app settings first")
            onResult("", VoiceCommand.Unknown(""))
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            say("Speech recognition is not available on this phone")
            onResult("", VoiceCommand.Unknown(""))
            return
        }
        listening = true
        tts?.stop()
        openHeadsetMic()

        val rec = createRecognizer()
        recognizer = rec
        var delivered = false
        fun finish(text: String) {
            if (delivered) return
            delivered = true
            listening = false
            main.removeCallbacksAndMessages(TIMEOUT_TOKEN)
            runCatching { rec.destroy() }
            if (recognizer === rec) recognizer = null
            closeHeadsetMic()
            // A blank result (silence, wind) is reported but not spoken back.
            onResult(text, if (text.isBlank()) VoiceCommand.Unknown("") else VoiceCommandParser.parse(text))
        }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                finish(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
            }
            override fun onError(error: Int) {
                DebugLog.i(TAG) { "Recognizer error $error" }
                finish("")
            }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
        // Give the Bluetooth mic link a moment to come up before recording starts.
        main.postDelayed({
            if (!delivered) runCatching { rec.startListening(intent) }.onFailure { finish("") }
        }, MIC_SETTLE_MS)
        main.postAtTime({ finish("") }, TIMEOUT_TOKEN, android.os.SystemClock.uptimeMillis() + LISTEN_TIMEOUT_MS)
    }

    private fun createRecognizer(): SpeechRecognizer =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(app)
        ) SpeechRecognizer.createOnDeviceSpeechRecognizer(app)
        else SpeechRecognizer.createSpeechRecognizer(app)

    // ── Bluetooth headset microphone ────────────────────────────────────────
    private var scoOpened = false

    private fun openHeadsetMic() {
        val am = audio ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val headset = am.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                } ?: return
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                scoOpened = am.setCommunicationDevice(headset)
            } else {
                @Suppress("DEPRECATION")
                if (am.isBluetoothScoAvailableOffCall) {
                    am.mode = AudioManager.MODE_IN_COMMUNICATION
                    @Suppress("DEPRECATION") am.startBluetoothSco()
                    @Suppress("DEPRECATION") am.isBluetoothScoOn = true
                    scoOpened = true
                }
            }
        }.onFailure { DebugLog.w(TAG) { "Headset mic unavailable: ${it.message}" } }
    }

    private fun closeHeadsetMic() {
        val am = audio ?: return
        if (!scoOpened) return
        scoOpened = false
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.clearCommunicationDevice()
            else {
                @Suppress("DEPRECATION") am.isBluetoothScoOn = false
                @Suppress("DEPRECATION") am.stopBluetoothSco()
            }
            am.mode = AudioManager.MODE_NORMAL
        }
    }

    // ── Spoken replies ──────────────────────────────────────────────────────
    fun say(text: String) {
        val engine = tts
        if (engine != null && ttsReady) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice-reply")
            return
        }
        if (engine == null) {
            tts = TextToSpeech(app) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale.getDefault()
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice-reply")
                }
            }
        }
    }

    fun shutdown() {
        main.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
        listening = false
        closeHeadsetMic()
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null; ttsReady = false
    }

    companion object {
        private const val TAG = "VoiceControl"
        private const val MIC_SETTLE_MS = 700L
        private const val LISTEN_TIMEOUT_MS = 9_000L
        private val TIMEOUT_TOKEN = Any()
    }
}
