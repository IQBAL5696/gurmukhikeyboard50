package com.iqbal.gurmukhikeyboard50
import android.content.Context; import android.media.AudioManager; import android.os.Build; import android.os.SystemClock; import android.util.Log; import android.os.VibrationEffect; import android.os.Vibrator; import android.os.VibratorManager; import android.view.KeyEvent; import android.view.inputmethod.EditorInfo; import android.inputmethodservice.Keyboard; import android.inputmethodservice.KeyboardView; import android.view.inputmethod.BaseInputConnection; import android.view.inputmethod.InputConnection; import androidx.preference.PreferenceManager; import android.widget.Toast
class MyKeyboardActionListener(private val service: MyKeyboardIME) : KeyboardView.OnKeyboardActionListener { val englishWordBuffer = StringBuilder(); private val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager; private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { (service.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator } else { @Suppress("DEPRECATION") (service.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator) }
    private fun getTargetInputConnection(): InputConnection? { if (MyKeyboardIME.currentPanel == ImeConstants.PANEL_TRANSLATION) { val translationInput = service.translationInput; if (translationInput != null) return translationInput.onCreateInputConnection(EditorInfo()) }; return service.currentInputConnection }
    private fun isFunctional(keyCode: Int): Boolean = keyCode == Keyboard.KEYCODE_DELETE || keyCode == Keyboard.KEYCODE_SHIFT || keyCode == Keyboard.KEYCODE_MODE_CHANGE || keyCode == Keyboard.KEYCODE_DONE || keyCode == 10 || keyCode == 32 || keyCode == -100 || keyCode == -101 || keyCode == -103 || keyCode == -153 || keyCode == -133 || keyCode == -138 || keyCode == ImeConstants.KEYCODE_LANGUAGE_SWITCH || keyCode < 0
    override fun onPress(primaryCode: Int) {
        provideFeedback(primaryCode)
        ReviewHelper.incrementUsage(service)
    }
    private fun provideFeedback(keyCode: Int) { 
        val prefs = PreferenceManager.getDefaultSharedPreferences(service); 
        if (prefs.getBoolean("sound_on_keypress", true)) { 
            val volume = prefs.getInt("sound_volume", 5) / 100f; 
            // Unified sound: All keys now play the standard click sound for consistency
            audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, volume) 
        }; 
        if (prefs.getBoolean("vibrate_on_keypress", true)) { 
            val base = prefs.getInt("vibration_intensity", 20); 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { 
                // Unified vibration: All keys now use the same vibration intensity
                val effect = VibrationEffect.createOneShot(base.toLong(), VibrationEffect.DEFAULT_AMPLITUDE); 
                vibrator.vibrate(effect) 
            } else { 
                @Suppress("DEPRECATION") vibrator.vibrate(base.toLong()) 
            } 
        } 
    }
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) { val ic = getTargetInputConnection() ?: return; when (primaryCode) { ImeConstants.KEYCODE_EMOJI -> { service.switchPanel(ImeConstants.PANEL_EMOJI); return }; ImeConstants.KEYCODE_TRANSLATE -> { service.switchPanel(ImeConstants.PANEL_TRANSLATION); return };
        ImeConstants.KEYCODE_VOICE_INPUT, -103 -> {
            if (service.voiceInputManager.isListening()) {
                service.voiceInputManager.stopVoiceRecognition()
            } else {
                val targetLang = if (service.keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) "pa-IN" else "en-US"
                service.voiceInputManager.startVoiceRecognition(targetLang, continuous = true)
            }
            return
        };
        ImeConstants.KEYCODE_LANGUAGE_SWITCH -> { ic.finishComposingText(); resetBuffers(); toggleLanguage(); return };
        ImeConstants.KEYCODE_SWITCH_TO_NANAKSHAHI_CALENDAR_PANEL -> { service.switchPanel(ImeConstants.PANEL_NANAKSHAHI_CALENDAR); return }; ImeConstants.KEYCODE_SETTINGS -> { service.launchSettings(); return }; ImeConstants.KEYCODE_NITNEM -> { service.switchPanel(ImeConstants.PANEL_NITNEM) ; return }; ImeConstants.KEYCODE_CALCULATOR -> { service.switchPanel(ImeConstants.PANEL_CALCULATOR); return }; ImeConstants.KEYCODE_NUMPAD_SWITCH, -140 -> { ic.finishComposingText(); resetBuffers(); service.handleKeyboardSwitch(KeyboardType.NUMPAD); return }; ImeConstants.KEYCODE_SWITCH_TO_SYMBOLS, -133 -> { ic.finishComposingText(); resetBuffers(); service.handleKeyboardSwitch(KeyboardType.SYMBOLS); return }; -138 -> { ic.finishComposingText(); resetBuffers(); service.handleKeyboardSwitch(KeyboardType.SYMBOLS1); return }; -153 -> { ic.beginBatchEdit(); ic.deleteSurroundingText(1000, 1000); ic.endBatchEdit(); resetBuffers(); return }; 61 -> { val textBefore = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""; val expression = textBefore.trim().split(" ").lastOrNull() ?: ""; val result = CalculatorHelper.evaluate(expression); if (result != null && result.contains("=")) { val finalRes = result.split("=").last(); ic.beginBatchEdit(); ic.finishComposingText(); ic.deleteSurroundingText(expression.length, 0); ic.commitText(finalRes, 1); ic.endBatchEdit(); resetBuffers() } else { ic.finishComposingText(); ic.commitText("=", 1); resetBuffers() }; return };
        -100 -> { ic.finishComposingText(); resetBuffers(); service.handleKeyboardSwitch(KeyboardType.GURMUKHI); return }
    }; handleRegularInput(primaryCode, ic) }
    private fun toggleLanguage() { val ic = getTargetInputConnection(); ic?.finishComposingText(); val word = getCurrentWord(); if (word.isNotEmpty()) { service.learnWord(word); if (service.currentInputEditorInfo?.packageName == "com.whatsapp") service.saveToClipboard(word) }; resetBuffers(); val nextType = when (service.keyboardManager.currentKeyboardType) { KeyboardType.GURMUKHI -> KeyboardType.ENGLISH; KeyboardType.ENGLISH -> KeyboardType.GURMUKHI; else -> service.lastAlphabeticKeyboard }; service.handleKeyboardSwitch(nextType) }
    private fun handleRegularInput(primaryCode: Int, ic: InputConnection) {
        val currentType = service.keyboardManager.currentKeyboardType
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                if (currentType == KeyboardType.NUMPAD || currentType == KeyboardType.SYMBOLS || currentType == KeyboardType.SYMBOLS1) {
                    ic.deleteSurroundingText(1, 0)
                    if (currentType == KeyboardType.NUMPAD) service.updateSuggestions("")
                } else if (currentType == KeyboardType.GURMUKHI) {
                    service.gurmukhiInputHandler.handleDelete(ic)
                } else {
                    if (englishWordBuffer.isNotEmpty()) {
                        englishWordBuffer.deleteAt(englishWordBuffer.length - 1);
                        ic.setComposingText(englishWordBuffer.toString(), 1)
                    } else {
                        ic.deleteSurroundingText(1, 0)
                    };
                    service.updateSuggestions(englishWordBuffer.toString())
                }
                if (currentType == KeyboardType.ENGLISH) {
                    service.keyboardManager.updateEnglishShiftState(ic, service.currentInputEditorInfo)
                    service.kv?.invalidateAllKeys()
                }
            };
            Keyboard.KEYCODE_SHIFT -> { service.keyboardManager.handleShift(); service.kv?.invalidateAllKeys() };
            Keyboard.KEYCODE_MODE_CHANGE -> {
                val word = getCurrentWord(); if (word.isNotEmpty()) { service.learnWord(word); if (service.currentInputEditorInfo?.packageName == "com.whatsapp") service.saveToClipboard(word) };
                ic.finishComposingText(); resetBuffers();
                when (currentType) {
                    KeyboardType.GURMUKHI, KeyboardType.ENGLISH -> {
                        service.lastAlphabeticKeyboard = currentType;
                        service.handleKeyboardSwitch(KeyboardType.SYMBOLS)
                    };
                    KeyboardType.SYMBOLS, KeyboardType.NUMPAD -> service.handleKeyboardSwitch(service.lastAlphabeticKeyboard);
                    else -> {}
                }
            };
            Keyboard.KEYCODE_DONE -> {
                val word = getCurrentWord(); if (word.isNotEmpty()) { service.learnWord(word); if (service.currentInputEditorInfo?.packageName == "com.whatsapp") service.saveToClipboard(word) };
                ic.finishComposingText();
                if (MyKeyboardIME.currentPanel == ImeConstants.PANEL_KEYBOARD) {
                    val editorInfo = service.currentInputEditorInfo;
                    if (editorInfo.packageName == "com.whatsapp") ic.commitText("\n", 1)
                    else ic.performEditorAction(editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION)
                } else {
                    ic.commitText("\n", 1)
                };
                resetBuffers()
            };
            else -> handleCharacter(primaryCode, ic)
        }
    }
    private fun handleCharacter(primaryCode: Int, ic: InputConnection) {
        if (primaryCode == 32) {
            try {
                ic.beginBatchEdit();
                if (!performAutoCorrect(ic)) {
                    val word = getCurrentWord();
                    ShortcutsManager.getShortcut(service, word)?.let {
                        ic.commitText(it, 1)
                        if (service.currentInputEditorInfo?.packageName == "com.whatsapp") service.saveToClipboard(it)
                    } ?: run { 
                        if (word.isNotEmpty()) {
                            service.learnWord(word)
                            if (service.currentInputEditorInfo?.packageName == "com.whatsapp") service.saveToClipboard(word)
                        }
                    };
                }
                ic.finishComposingText();
                ic.commitText(" ", 1);
            } catch (e: Exception) {
                Log.e("MyKeyboardActionListener", "Error handling space", e)
                ic.commitText(" ", 1)
            } finally {
                ic.endBatchEdit();
                resetBuffers()
            }
        } else if (service.keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) {
            service.gurmukhiInputHandler.handleCharacter(primaryCode, ic);
            checkUnshift()
        } else if (primaryCode > 0) {
            val char = primaryCode.toChar();
            if (service.keyboardManager.currentKeyboardType == KeyboardType.NUMPAD) {
                ic.commitText(char.toString(), 1)
                service.updateSuggestions("")
            } else if (isPunctuation(char)) {
                ic.beginBatchEdit()
                if (!performAutoCorrect(ic)) {
                    val word = getCurrentWord()
                    if (word.isNotEmpty()) {
                        service.learnWord(word)
                        if (service.currentInputEditorInfo?.packageName == "com.whatsapp") service.saveToClipboard(word)
                    }
                }
                ic.finishComposingText();
                ic.commitText(char.toString(), 1);
                ic.endBatchEdit()
                resetBuffers();
            } else {
                englishWordBuffer.append(char);
                ic.setComposingText(englishWordBuffer.toString(), 1);
                service.updateSuggestions(englishWordBuffer.toString())
            };
            checkUnshift()
        }
    }
    private fun isPunctuation(c: Char): Boolean = ".,!?;:()\"\'।॥".contains(c)

    private fun performAutoCorrect(ic: InputConnection): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(service)
        if (!prefs.getBoolean("pref_auto_correct", true)) return false

        val suggestions = service.currentSuggestions
        val correctionIndex = service.currentCorrectionIndex

        if (correctionIndex != -1 && correctionIndex < suggestions.size) {
            val correction = suggestions[correctionIndex]
            ic.commitText(correction, 1)
            if (service.currentInputEditorInfo?.packageName == "com.whatsapp") service.saveToClipboard(correction)
            service.learnWord(correction)
            return true
        }
        return false
    }

    private fun checkUnshift() { service.keyboardManager.unshiftIfNeeded(service.currentInputConnection, service.currentInputEditorInfo); service.kv?.invalidateAllKeys() }
    private fun getCurrentWord(): String = if (service.keyboardManager.currentKeyboardType == KeyboardType.GURMUKHI) service.gurmukhiInputHandler.getCurrentWord() else englishWordBuffer.toString()
    fun resetBuffers() { englishWordBuffer.clear(); service.gurmukhiInputHandler.reset(); service.updateSuggestions("") }
    override fun onRelease(primaryCode: Int) { service.kv?.unpressAllKeys() }
    override fun onText(text: CharSequence?) { val ic = getTargetInputConnection() ?: return; provideFeedback(0); text?.let { ic.finishComposingText(); resetBuffers(); ic.commitText(it, 1); if (it.length >= 2) { service.learnWord(it.toString()); if (service.currentInputEditorInfo?.packageName == "com.whatsapp") service.saveToClipboard(it.toString()) }; checkUnshift() } }
    override fun swipeLeft() { }; override fun swipeRight() { }; override fun swipeDown() {}; override fun swipeUp() {}
    fun moveCursor(direction: Int) { val ic = getTargetInputConnection() ?: return; val keyCode = if (direction > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT; val now = SystemClock.uptimeMillis() ; ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)); ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)) } }
