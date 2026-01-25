package com.iqbal.gurmukhikeyboard50

interface VoiceRecognitionResultListener {
    fun onTextRecognized(text: String, isFinal: Boolean)
    fun onListeningError(errorMessage: String)
    fun onReadyForSpeech()
    fun onEndOfSpeech()
}
