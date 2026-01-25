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
    fun isContinuousMode(): Boolean = isContinuous

    fun startVoiceRecognition(languageLocale: String, continuous: Boolean = true) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.onListeningError("Missing RECORD_AUDIO permission")
            return
        }

        internalStop()
        isContinuous = continuous
        currentLocale = languageLocale
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent = createRecognizerIntent(languageLocale)

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                listener.onReadyForSpeech()
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (!isContinuous) {
                    isListening = false
                    listener.onEndOfSpeech()
                }
            }

            override fun onError(error: Int) {
                Log.d(TAG, "Error code: $error")
                if (isContinuous && (error == SpeechRecognizer.ERROR_NO_MATCH || 
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || 
                    error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT)) {
                    restart(300)
                } else {
                    stopVoiceRecognition()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { listener.onTextRecognized(it, true) }
                
                if (isContinuous) restart(300)
                else stopVoiceRecognition()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { listener.onTextRecognized(it, false) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer?.startListening(intent)
    }

    private fun createRecognizerIntent(locale: String): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale)
            
            // Add support for multiple English dialects and other languages as hints
            val supportedLanguages = arrayListOf("pa-IN", "en-US", "en-GB", "en-IN", "hi-IN")
            if (!supportedLanguages.contains(locale)) supportedLanguages.add(locale)
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, supportedLanguages)
            
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
        }
    }

    private fun restart(delay: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (isContinuous && speechRecognizer != null) {
                speechRecognizer?.startListening(createRecognizerIntent(currentLocale))
            }
        }, delay)
    }

    fun stopVoiceRecognition() {
        isContinuous = false
        internalStop()
        listener.onEndOfSpeech()
    }

    private fun internalStop() {
        isListening = false
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.apply {
            cancel()
            destroy()
        }
        speechRecognizer = null
    }
}
