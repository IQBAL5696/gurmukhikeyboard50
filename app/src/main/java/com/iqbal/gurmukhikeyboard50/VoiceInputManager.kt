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
import androidx.core.content.ContextCompat

class VoiceInputManager(
    private val context: Context,
    private val listener: VoiceRecognitionResultListener
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isContinuous = false
    private var isListening = false
    private var currentLocale: String = "pa-IN"
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "VoiceInputManager"

    fun isListening(): Boolean = isListening

    fun startVoiceRecognition(languageLocale: String, continuous: Boolean = false) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.onListeningError("Missing RECORD_AUDIO permission")
            return
        }

        isContinuous = continuous
        currentLocale = languageLocale
        isListening = true
        
        handler.removeCallbacksAndMessages(null)
        createAndStart()
    }

    private fun createAndStart() {
        handler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    listener.onListeningError("Speech Recognition is not available")
                    isListening = false
                    return@post
                }

                // Clean up previous instance before starting new session
                destroyInternal()
                
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                speechRecognizer?.setRecognitionListener(createRecognitionListener())
                
                val intent = createRecognizerIntent(currentLocale)
                speechRecognizer?.startListening(intent)
                Log.d(TAG, "SpeechRecognizer started. Continuous: $isContinuous")
            } catch (e: Exception) {
                Log.e(TAG, "Start failed", e)
                if (isContinuous) {
                    restart(1000)
                } else {
                    isListening = false
                    stopVoiceRecognition()
                }
            }
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "onReadyForSpeech")
            listener.onReadyForSpeech()
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            listener.onRmsChanged(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
        }

        override fun onError(error: Int) {
            val errorMsg = getErrorText(error)
            Log.d(TAG, "onError: $error ($errorMsg)")

            if (isContinuous) {
                // In continuous mode, we restart for almost all errors (timeout, no-match, etc.)
                // to keep the session alive.
                if (error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    Log.d(TAG, "Restarting continuous recognition after error: $error")
                    restart(500)
                    return
                }
            }

            isListening = false
            listener.onListeningError(errorMsg)
            stopVoiceRecognition()
        }

        override fun onResults(results: Bundle?) {
            Log.d(TAG, "onResults")
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()
            
            if (!text.isNullOrEmpty()) {
                Log.d(TAG, "Final Result: $text")
                listener.onTextRecognized(text, true)
            }

            if (isContinuous) {
                restart(100)
            } else {
                isListening = false
                stopVoiceRecognition()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.firstOrNull()?.let { 
                Log.d(TAG, "Partial: $it")
                listener.onTextRecognized(it, false) 
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun getErrorText(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
        else -> "Error code: $error"
    }

    private fun createRecognizerIntent(locale: String): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Critical for some devices to recognize the keyboard as the source
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            
            // Try to extend the silence timeout
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        }
    }

    private fun restart(delay: Long) {
        if (!isContinuous) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (isContinuous) {
                createAndStart()
            }
        }, delay)
    }

    fun stopVoiceRecognition() {
        Log.d(TAG, "stopVoiceRecognition")
        isContinuous = false
        isListening = false
        handler.removeCallbacksAndMessages(null)
        handler.post {
            destroyInternal()
            listener.onEndOfSpeech()
        }
    }

    private fun destroyInternal() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Destroy failed", e)
        }
        speechRecognizer = null
    }
}
