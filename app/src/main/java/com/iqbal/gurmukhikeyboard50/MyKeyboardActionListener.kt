package com.iqbal.gurmukhikeyboard50

import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputConnection
import android.widget.EditText

class MyKeyboardActionListener(
    private val service: MyKeyboardIME
) : KeyboardView.OnKeyboardActionListener {

    val englishWordBuffer = StringBuilder()
    private val phoneticBuffer = StringBuilder()
    private var lastSpaceTime: Long = 0

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        when (primaryCode) {
            ImeConstants.KEYCODE_EMOJI -> { service.switchPanel(ImeConstants.PANEL_EMOJI); return }
            ImeConstants.KEYCODE_TRANSLATE -> { service.switchPanel(ImeConstants.PANEL_TRANSLATION); return }
            ImeConstants.KEYCODE_AI_ASSISTANT -> { service.switchPanel(ImeConstants.PANEL_AI_ASSISTANT); return }
            ImeConstants.KEYCODE_VOICE_INPUT -> {
                val langCode = when (service.keyboardManager.currentKeyboardType) {
                    KeyboardType.GURMUKHI -> "pa-IN"
                    else -> "en-US"
                }
                val targetLang = when (service.currentPanel) {
                    ImeConstants.PANEL_TRANSLATION -> service.translationManager.sourceLanguage.speechCode
                    ImeConstants.PANEL_AI_ASSISTANT -> "en-US" // or pa-IN based on user prompt expectation
                    else -> langCode
                }
                service.voiceInputManager.startVoiceRecognition(targetLang)
                return
            }
            ImeConstants.KEYCODE_LANGUAGE_SWITCH -> {
                toggleLanguage()
                return
            }
            ImeConstants.KEYCODE_SWITCH_TO_NANAKSHAHI_CALENDAR_PANEL -> { service.switchPanel(ImeConstants.PANEL_NANAKSHAHI_CALENDAR); return }
            ImeConstants.KEYCODE_SETTINGS -> { service.launchSettings(); return }
            ImeConstants.KEYCODE_SWITCH_TO_SYMBOLS -> { resetBuffers(); service.handleKeyboardSwitch(KeyboardType.SYMBOLS); return }
            -1000 -> { sendCombinedKeyEvent(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON); return }
            -1001 -> { sendCombinedKeyEvent(KeyEvent.KEYCODE_Y, KeyEvent.META_CTRL_ON); return }
        }

        val targetIc = when (service.currentPanel) {
            ImeConstants.PANEL_TRANSLATION -> service.translationInput?.let { BaseInputConnection(it, true) }
            ImeConstants.PANEL_AI_ASSISTANT -> service.aiPromptInput?.let { BaseInputConnection(it, true) }
            else -> service.currentInputConnection
        } ?: service.currentInputConnection

        if (primaryCode == Keyboard.KEYCODE_DONE) {
            when (service.currentPanel) {
                ImeConstants.PANEL_TRANSLATION -> { service.translationManager.translate(service.translationInput?.text.toString(), null); return }
                ImeConstants.PANEL_AI_ASSISTANT -> { /* Trigger AI search if needed on Enter */ return }
            }
        }

        handleRegularInput(primaryCode, targetIc)
    }

    private fun toggleLanguage() {
        resetBuffers()
        if (service.keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) {
            service.handleKeyboardSwitch(KeyboardType.ENGLISH)
        } else {
            service.handleKeyboardSwitch(KeyboardType.GURMUKHI)
        }
    }

    private fun sendCombinedKeyEvent(keyCode: Int, metaState: Int) {
        val ic = service.currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
    }

    private fun handleRegularInput(primaryCode: Int, ic: InputConnection?) {
        ic ?: return
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                if (service.keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) {
                    service.gurmukhiInputHandler.handleDelete(ic)
                } else if (service.keyboardManager.currentKeyboardType == KeyboardType.PHONETIC) {
                    if (phoneticBuffer.isNotEmpty()) {
                        phoneticBuffer.deleteCharAt(phoneticBuffer.length - 1)
                        updatePhoneticComposition(ic)
                    } else { ic.deleteSurroundingText(1, 0) }
                } else {
                    if (englishWordBuffer.isNotEmpty()) {
                        englishWordBuffer.deleteCharAt(englishWordBuffer.length - 1)
                    }
                    ic.deleteSurroundingText(1, 0)
                }
                service.updateSuggestions(getCurrentWord())
            }
            Keyboard.KEYCODE_SHIFT -> { service.keyboardManager.handleShift(); service.kv?.invalidateAllKeys() }
            Keyboard.KEYCODE_MODE_CHANGE -> {
                resetBuffers()
                when (service.keyboardManager.currentKeyboardType) {
                    KeyboardType.GURMUKHI, KeyboardType.ENGLISH, KeyboardType.PHONETIC -> { service.lastAlphabeticKeyboard = service.keyboardManager.currentKeyboardType; service.handleKeyboardSwitch(KeyboardType.SYMBOLS) }
                    KeyboardType.SYMBOLS, KeyboardType.NUMPAD -> service.handleKeyboardSwitch(service.lastAlphabeticKeyboard)
                    else -> {}
                }
            }
            Keyboard.KEYCODE_DONE -> {
                resetBuffers()
                val editorInfo = service.currentInputEditorInfo
                if (editorInfo.packageName == "com.whatsapp") ic.commitText("\n", 1) else ic.performEditorAction(editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION)
            }
            -101 -> { resetBuffers(); service.handleKeyboardSwitch(KeyboardType.ENGLISH) }
            -100 -> { resetBuffers(); service.handleKeyboardSwitch(KeyboardType.GURMUKHI) }
            -103 -> { resetBuffers(); service.handleKeyboardSwitch(KeyboardType.NUMPAD) }
            -104 -> { resetBuffers(); service.handleKeyboardSwitch(KeyboardType.PHONETIC) }
            else -> handleCharacter(primaryCode, ic)
        }
    }

    private fun handleCharacter(primaryCode: Int, ic: InputConnection) {
        var characterHandled = false
        if (primaryCode == 32) {
            val currentTime = System.currentTimeMillis()
            val currentWord = getCurrentWord()
            val expanded = ShortcutsManager.getShortcut(service, currentWord)
            if (expanded != null) {
                ic.beginBatchEdit(); ic.deleteSurroundingText(currentWord.length, 0); ic.commitText(expanded, 1); ic.endBatchEdit(); resetBuffers()
            }
            if (currentTime - lastSpaceTime < 400) {
                ic.deleteSurroundingText(1, 0); val danda = if (service.keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) "। " else ". "; ic.commitText(danda, 1); lastSpaceTime = 0
            } else {
                if (service.keyboardManager.currentKeyboardType == KeyboardType.PHONETIC) { ic.finishComposingText(); phoneticBuffer.clear() }
                else if (service.keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) { val lastWord = service.gurmukhiInputHandler.getCurrentWord(); if (lastWord.isNotEmpty()) service.learnWord(lastWord); service.gurmukhiInputHandler.reset() }
                else { val lastWord = englishWordBuffer.toString(); if (lastWord.isNotEmpty()) service.learnWord(lastWord); englishWordBuffer.clear() }
                ic.commitText(" ", 1); lastSpaceTime = currentTime
            }
            characterHandled = true
        } else if (service.keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) {
            service.gurmukhiInputHandler.handleCharacter(primaryCode, ic); characterHandled = true
        } else if (service.keyboardManager.currentKeyboardType == KeyboardType.PHONETIC && primaryCode > 0) {
            phoneticBuffer.append(primaryCode.toChar()); updatePhoneticComposition(ic); characterHandled = true
        } else if (primaryCode > 0) {
            val char = primaryCode.toChar(); ic.commitText(char.toString(), 1)
            if (service.keyboardManager.currentKeyboardType != KeyboardType.GURMUKHI && 
                service.keyboardManager.currentKeyboardType != KeyboardType.PHONETIC) {
                englishWordBuffer.append(char)
            }
            characterHandled = true
        }

        if (characterHandled) {
            service.keyboardManager.unshiftIfNeeded(); service.kv?.invalidateAllKeys()
            service.updateSuggestions(getCurrentWord())
        }
    }

    private fun getCurrentWord(): String {
        return when (service.keyboardManager.currentKeyboardType) {
            KeyboardType.GURMUKHI -> service.gurmukhiInputHandler.getCurrentWord()
            KeyboardType.PHONETIC -> phoneticBuffer.toString()
            else -> englishWordBuffer.toString()
        }
    }

    private fun updatePhoneticComposition(ic: InputConnection) {
        val result = TransliterationHelper.transliterate(phoneticBuffer.toString())
        ic.setComposingText(result, 1)
    }

    fun resetBuffers() { englishWordBuffer.clear(); phoneticBuffer.clear(); service.gurmukhiInputHandler.reset(); service.updateSuggestions("") }

    fun moveCursor(direction: Int) {
        val ic = service.currentInputConnection ?: return
        val eventDown = KeyEvent(KeyEvent.ACTION_DOWN, if (direction > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
        val eventUp = KeyEvent(KeyEvent.ACTION_UP, if (direction > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT)
        ic.sendKeyEvent(eventDown); ic.sendKeyEvent(eventUp)
    }

    override fun onPress(primaryCode: Int) {
        val vibrateOn = service.sharedPreferences.getBoolean("vibrate_on_keypress", true)
        if (vibrateOn) { val vibrator = service.getSystemService(android.os.Vibrator::class.java); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)) }
        val soundOn = service.sharedPreferences.getBoolean("sound_on_keypress", true)
        if (soundOn) { val audioManager = service.getSystemService(android.media.AudioManager::class.java); val sound = if (primaryCode in listOf(Keyboard.KEYCODE_DELETE, Keyboard.KEYCODE_SHIFT, Keyboard.KEYCODE_MODE_CHANGE, Keyboard.KEYCODE_DONE)) AudioManager.FX_KEY_CLICK else AudioManager.FX_KEYPRESS_STANDARD; audioManager.playSoundEffect(sound) }
    }

    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {
        val targetIc = when (service.currentPanel) {
            ImeConstants.PANEL_TRANSLATION -> service.translationInput?.let { BaseInputConnection(it, true) }
            ImeConstants.PANEL_AI_ASSISTANT -> service.aiPromptInput?.let { BaseInputConnection(it, true) }
            else -> service.currentInputConnection
        } ?: service.currentInputConnection

        if (text != null) {
            targetIc.commitText(text, 1)
            if (service.keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) service.gurmukhiInputHandler.appendCommittedText(text.toString())
            else { englishWordBuffer.append(text); service.updateSuggestions(englishWordBuffer.toString()) }
        }
    }
    override fun swipeLeft() { val ic = service.currentInputConnection ?: return; ic.beginBatchEdit(); val textBefore = ic.getTextBeforeCursor(50, 0) ?: ""; val lastSpace = textBefore.trimEnd().lastIndexOf(' '); val charsToDelete = if (lastSpace != -1) textBefore.length - lastSpace - 1 else textBefore.length; ic.deleteSurroundingText(charsToDelete.toInt(), 0); ic.endBatchEdit(); resetBuffers() }
    
    override fun swipeRight() {
        toggleLanguage()
    }
    
    override fun swipeDown() {}
    override fun swipeUp() {}
}
