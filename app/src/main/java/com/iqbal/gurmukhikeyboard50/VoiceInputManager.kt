// VoiceInputManager.kt
package com.iqbal.gurmukhikeyboard50

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Continuous voice input manager.
 *
 * • When `continuous = true` → restarts automatically after silence / no-match.
 * • When `continuous = false` → behaves exactly like your original one-shot version.
 * • Partial results are ignored in one-shot mode to prevent duplicate typing.
 */
class VoiceInputManager(
    private val context: Context,
    private val listener: VoiceRecognitionResultListener
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isContinuous = false
    private var isListening = false
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "VoiceInputManager"

    /** Public getter – used by MyKeyboardIME to decide when to hide the popup */
    fun isListening(): Boolean = isListening

    /** Public getter – used by MyKeyboardIME to know the mode */
    fun isContinuousMode(): Boolean = isContinuous

    /**
     * Start recognition.
     *
     * @param languageLocale e.g. "en-US", "pa-IN"
     * @param continuous     true → keep listening until stopVoiceRecognition() is called
     */
    fun startVoiceRecognition(languageLocale: String, continuous: Boolean = false) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            listener.onListeningError("Missing RECORD_AUDIO permission. Please grant it in app settings.")
            return
        }

        stopVoiceRecognition() // clean any previous session
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, "Speech recognition unavailable", Toast.LENGTH_SHORT).show()
            return
        }

        isContinuous = continuous
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageLocale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 15)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 15000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 15000L)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                listener.onReadyForSpeech()
                Log.d(TAG, "onReadyForSpeech")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech")
                isListening = false
                listener.onEndOfSpeech()
                if (isContinuous) restart(languageLocale, delay = 100)
            }

            override fun onError(error: Int) {
                isListening = false
                Log.e(TAG, "SpeechRecognizer error: $error")

                val isNonFatalError = error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

                if (isContinuous && isNonFatalError) {
                    Log.d(TAG, "Non-fatal error, restarting listener.")
                    restart(languageLocale, delay = 100)
                    return
                }

                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected (timeout)"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network problem"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing RECORD_AUDIO permission"
                    SpeechRecognizer.ERROR_CLIENT -> "A client-side error occurred"
                    SpeechRecognizer.ERROR_SERVER -> "A server-side error occurred"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy"
                    else -> "Error: $error"
                }
                listener.onListeningError(msg)
            }

            // ✅ FIXED: Only send final result once to prevent repeated words
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()?.trim() ?: ""
                if (best.isNotBlank()) {
                    Log.d(TAG, "onResults (final): $best")
                    listener.onTextRecognized(best, true)
                }

                if (isContinuous) restart(languageLocale, delay = 100)
                else stopVoiceRecognition()
            }

            // ✅ FIXED: Ignore partial results in one-shot mode
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = partial?.firstOrNull()?.trim() ?: ""
                if (best.isNotBlank()) {
                    if (isContinuous) {
                        // Show partial text only in translation view
                        Log.d(TAG, "onPartialResults (continuous): $best")
                        listener.onTextRecognized(best, false)
                    } else {
                        Log.d(TAG, "onPartialResults ignored (one-shot mode)")
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        Log.d(TAG, "Starting listening...")
        speechRecognizer?.startListening(intent)
    }

    /** Restart the recognizer after a short delay – avoids gaps & crashes */
    private fun restart(languageLocale: String, delay: Long) {
        handler.postDelayed({
            if (isContinuous) {
                Log.d(TAG, "Restarting listener")
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageLocale)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 15)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 15000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 15000L)
                }
                speechRecognizer?.startListening(intent)
            }
        }, delay)
    }

    /** Public stop – used by “Stop” button and when the panel is closed */
    fun stopVoiceRecognition() {
        Log.d(TAG, "stopVoiceRecognition() called")
        isContinuous = false
        isListening = false
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.let {
            try {
                it.stopListening()
                it.cancel()
                it.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Exception while stopping recognizer", e)
            }
        }
        speechRecognizer = null
    }
}
