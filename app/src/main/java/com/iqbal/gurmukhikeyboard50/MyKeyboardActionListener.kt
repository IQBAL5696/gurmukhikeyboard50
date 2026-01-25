package com.iqbal.gurmukhikeyboard50

import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.inputmethod.EditorInfo
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.inputmethod.InputConnection

class MyKeyboardActionListener(
    private val service: MyKeyboardIME
) : KeyboardView.OnKeyboardActionListener {

    val englishWordBuffer = StringBuilder()
    private val phoneticBuffer = StringBuilder()

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        // Handle global keys first
        when (primaryCode) {
            ImeConstants.KEYCODE_EMOJI -> {
                service.switchPanel(ImeConstants.PANEL_EMOJI)
                return
            }
            ImeConstants.KEYCODE_TRANSLATE -> {
                service.switchPanel(ImeConstants.PANEL_TRANSLATION)
                return
            }
            ImeConstants.KEYCODE_VOICE_INPUT -> {
                val langCode = when (service.keyboardManager.currentKeyboardType) {
                    KeyboardType.GURMUKHI -> "pa-IN"
                    else -> "en-US"
                }
                val targetLang = if (service.currentPanel == ImeConstants.PANEL_TRANSLATION) {
                    service.translationManager.sourceLanguage.speechCode
                } else {
                    langCode
                }
                service.voiceInputManager.startVoiceRecognition(targetLang)
                return
            }
            ImeConstants.KEYCODE_LANGUAGE_SWITCH -> {
                resetBuffers()
                when (service.keyboardManager.currentKeyboardType) {
                    KeyboardType.GURMUKHI -> service.handleKeyboardSwitch(KeyboardType.ENGLISH)
                    KeyboardType.ENGLISH -> service.handleKeyboardSwitch(KeyboardType.PHONETIC)
                    KeyboardType.PHONETIC -> service.handleKeyboardSwitch(KeyboardType.GURMUKHI)
                    else -> service.handleKeyboardSwitch(service.lastAlphabeticKeyboard)
                }
                return
            }
            ImeConstants.KEYCODE_SWITCH_TO_NANAKSHAHI_CALENDAR_PANEL -> {
                service.switchPanel(ImeConstants.PANEL_NANAKSHAHI_CALENDAR)
                return
            }
            ImeConstants.KEYCODE_SETTINGS -> {
                service.launchSettings()
                return
            }
            ImeConstants.KEYCODE_SWITCH_TO_SYMBOLS -> {
                resetBuffers()
                service.handleKeyboardSwitch(KeyboardType.SYMBOLS)
                return
            }
        }

        // Handle panel-specific keys
        when (service.currentPanel) {
            ImeConstants.PANEL_TRANSLATION -> handleTranslationInput(primaryCode)
            else -> handleRegularInput(primaryCode, service.currentInputConnection)
        }
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
                    } else {
                        ic.deleteSurroundingText(1, 0)
                    }
                } else {
                    if (englishWordBuffer.isNotEmpty()) {
                        englishWordBuffer.deleteCharAt(englishWordBuffer.length - 1)
                        service.updateSuggestions(englishWordBuffer.toString())
                    }
                    ic.deleteSurroundingText(1, 0)
                }
            }
            Keyboard.KEYCODE_SHIFT -> {
                service.keyboardManager.handleShift()
                service.kv?.invalidateAllKeys()
            }
            Keyboard.KEYCODE_MODE_CHANGE -> {
                resetBuffers()
                when (service.keyboardManager.currentKeyboardType) {
                    KeyboardType.GURMUKHI, KeyboardType.ENGLISH, KeyboardType.PHONETIC -> {
                        service.lastAlphabeticKeyboard = service.keyboardManager.currentKeyboardType
                        service.handleKeyboardSwitch(KeyboardType.SYMBOLS)
                    }
                    KeyboardType.SYMBOLS, KeyboardType.NUMPAD -> service.handleKeyboardSwitch(service.lastAlphabeticKeyboard)
                    else -> {}
                }
            }
            Keyboard.KEYCODE_DONE -> {
                resetBuffers()
                val editorInfo = service.currentInputEditorInfo
                val isWhatsApp = editorInfo.packageName == "com.whatsapp"
                
                if (isWhatsApp) {
                    ic.commitText("\n", 1)
                } else {
                    ic.performEditorAction(editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION)
                }
            }
            -101 -> { resetBuffers(); service.handleKeyboardSwitch(KeyboardType.ENGLISH) }
            -100 -> { resetBuffers(); service.handleKeyboardSwitch(KeyboardType.GURMUKHI) }
            ImeConstants.KEYCODE_SETTINGS -> { service.launchSettings() }
            -103 -> { resetBuffers(); service.handleKeyboardSwitch(KeyboardType.NUMPAD) }
            -104 -> { resetBuffers(); service.handleKeyboardSwitch(KeyboardType.PHONETIC) }
            ImeConstants.KEYCODE_SWITCH_TO_SYMBOLS -> { resetBuffers(); service.handleKeyboardSwitch(KeyboardType.SYMBOLS) }
            else -> handleCharacter(primaryCode, ic)
        }
    }

    private fun handleTranslationInput(primaryCode: Int) {
        handleGenericTextInput(primaryCode, service.translationInput)
    }

    private fun handleGenericTextInput(primaryCode: Int, editText: android.widget.EditText?) {
        editText ?: return
        val editable = editText.text
        val start = editText.selectionStart

        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                if (start > 0) {
                    editable.delete(start - 1, start)
                }
            }
            Keyboard.KEYCODE_SHIFT -> {
                service.keyboardManager.handleShift()
                service.kv?.invalidateAllKeys()
            }
            else -> {
                if (primaryCode > 0) {
                    editable.insert(start, primaryCode.toChar().toString())
                }
            }
        }
    }

    private fun handleCharacter(primaryCode: Int, ic: InputConnection) {
        var characterHandled = false

        if (primaryCode == 32) { // Space
            if (service.keyboardManager.currentKeyboardType == KeyboardType.PHONETIC) {
                ic.finishComposingText()
                phoneticBuffer.clear()
            } else if (service.keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) {
                val lastWord = service.gurmukhiInputHandler.getCurrentWord()
                if (lastWord.isNotEmpty()) service.learnWord(lastWord)
                service.gurmukhiInputHandler.reset()
            } else if (service.keyboardManager.currentKeyboardType == KeyboardType.ENGLISH) {
                val lastWord = englishWordBuffer.toString()
                if (lastWord.isNotEmpty()) service.learnWord(lastWord)
                englishWordBuffer.clear()
                service.updateSuggestions("")
            }
            ic.commitText(" ", 1)
            characterHandled = true
        } else if (service.keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) {
            service.gurmukhiInputHandler.handleCharacter(primaryCode, ic)
            characterHandled = true
        } else if (service.keyboardManager.currentKeyboardType == KeyboardType.PHONETIC && primaryCode > 0) {
            phoneticBuffer.append(primaryCode.toChar())
            updatePhoneticComposition(ic)
            characterHandled = true
        } else if (primaryCode > 0) {
            val char = primaryCode.toChar()
            ic.commitText(char.toString(), 1)
            if (service.keyboardManager.currentKeyboardType == KeyboardType.ENGLISH) {
                englishWordBuffer.append(char)
                service.updateSuggestions(englishWordBuffer.toString())
            }
            characterHandled = true
        }

        if (characterHandled) {
            service.keyboardManager.unshiftIfNeeded()
            service.kv?.invalidateAllKeys()
        }
    }

    private fun updatePhoneticComposition(ic: InputConnection) {
        val result = TransliterationHelper.transliterate(phoneticBuffer.toString())
        ic.setComposingText(result, 1)
    }

    fun resetBuffers() {
        englishWordBuffer.clear()
        phoneticBuffer.clear()
        service.gurmukhiInputHandler.reset()
        service.updateSuggestions("")
    }

    override fun onPress(primaryCode: Int) {
        val vibrateOn = service.sharedPreferences.getBoolean("vibrate_on_keypress", true)
        if (vibrateOn) {
            val vibrator = service.getSystemService(android.os.Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }

        val soundOn = service.sharedPreferences.getBoolean("sound_on_keypress", true)
        if (soundOn) {
            val functionalKeys = listOf(
                Keyboard.KEYCODE_DELETE,
                Keyboard.KEYCODE_SHIFT,
                Keyboard.KEYCODE_MODE_CHANGE,
                Keyboard.KEYCODE_DONE
            )
            val audioManager = service.getSystemService(android.media.AudioManager::class.java)
            val sound = if (primaryCode in functionalKeys) AudioManager.FX_KEY_CLICK else AudioManager.FX_KEYPRESS_STANDARD
            audioManager.playSoundEffect(sound)
        }
    }

    override fun onRelease(primaryCode: Int) {}

    override fun onText(text: CharSequence?) {
        when (service.currentPanel) {
            ImeConstants.PANEL_TRANSLATION -> {
                val editText = service.translationInput ?: return
                val editable = editText.text
                val start = editText.selectionStart
                editable.insert(start, text)
            }
            else -> {
                if (text != null) {
                    service.currentInputConnection?.commitText(text, 1)
                    if (service.keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) {
                        service.gurmukhiInputHandler.appendCommittedText(text.toString())
                    } else if (service.keyboardManager.currentKeyboardType == KeyboardType.ENGLISH) {
                        englishWordBuffer.append(text)
                        service.updateSuggestions(englishWordBuffer.toString())
                    }
                }
            }
        }
    }

    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
