package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat

class KeyboardManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) {

    var gurmukhiKeyboard: MyKeyboard? = null
    var englishKeyboard: MyKeyboard? = null
    var symbolsKeyboard: MyKeyboard? = null
    var symbols1Keyboard: MyKeyboard? = null
    var numPadKeyboard: MyKeyboard? = null
    var gridNumbersKeyboard: MyKeyboard? = null

    // Split Keyboards
    var splitGurmukhiKeyboard: MyKeyboard? = null
    var splitEnglishKeyboard: MyKeyboard? = null

    var currentKeyboardType: KeyboardType = KeyboardType.GURMUKHI
    var lastAlphabeticKeyboard: KeyboardType = KeyboardType.GURMUKHI

    val defaultKeyboardType: KeyboardType
        get() = when (sharedPreferences.getString("pref_default_keyboard", "gurmukhi")) {
            "english" -> KeyboardType.ENGLISH
            else -> KeyboardType.GURMUKHI
        }

    private var isGurmukhiShifted = false
    private var isEnglishShifted = false
    private var isEnglishCapsLock = false
    private var isSymbolsShifted = false

    private var lastShiftPressTime: Long = 0
    private val doubleTapThreshold: Long = 300 // ms

    companion object {
        private const val TAG = "KeyboardManager"
    }

    fun getThemeResIdForValue(themeValue: String?): Int {
        return when (themeValue) {
            "dark" -> R.style.KeyboardTheme_Dark
            "kesari" -> R.style.KeyboardTheme_Kesari
            "royal_blue" -> R.style.KeyboardTheme_RoyalBlue
            "blue" -> R.style.KeyboardTheme_Blue
            "green" -> R.style.KeyboardTheme_Green
            "red" -> R.style.KeyboardTheme_Red
            "pink" -> R.style.KeyboardTheme_Pink
            "purple" -> R.style.KeyboardTheme_Purple
            "orange" -> R.style.KeyboardTheme_Orange
            "gold" -> R.style.KeyboardTheme_Gold
            "teal" -> R.style.KeyboardTheme_Teal
            "indigo" -> R.style.KeyboardTheme_Indigo
            "silver" -> R.style.KeyboardTheme_Silver
            "coffee" -> R.style.KeyboardTheme_Coffee
            "custom" -> R.style.KeyboardTheme_Custom
            "midnight" -> R.style.KeyboardTheme_Midnight
            "lavender" -> R.style.KeyboardTheme_Lavender
            "moss" -> R.style.KeyboardTheme_Moss
            "sunset" -> R.style.KeyboardTheme_Sunset
            "space" -> R.style.KeyboardTheme_Space
            "ocean" -> R.style.KeyboardTheme_Ocean
            "autumn" -> R.style.KeyboardTheme_Autumn
            "blossom" -> R.style.KeyboardTheme_Blossom
            "sand" -> R.style.KeyboardTheme_Sand
            "night_sky" -> R.style.KeyboardTheme_NightSky
            "cotton" -> R.style.KeyboardTheme_Cotton
            "sunny" -> R.style.KeyboardTheme_Sunny
            "mint" -> R.style.KeyboardTheme_Mint
            "ivory" -> R.style.KeyboardTheme_Ivory
            "sky_bliss" -> R.style.KeyboardTheme_SkyBliss
            else -> R.style.KeyboardTheme_Light
        }
    }

    fun loadAllKeyboards(contextForKeyboardCreation: Context, width: Int = 0) {
        try {
            val targetWidth = if (width > 0) width else contextForKeyboardCreation.resources.displayMetrics.widthPixels

            gurmukhiKeyboard = MyKeyboard(contextForKeyboardCreation, R.xml.gurmukhi_keyboard, 0, targetWidth, 0)
            englishKeyboard = MyKeyboard(contextForKeyboardCreation, R.xml.qwerty_keyboard, 0, targetWidth, 0)
            symbolsKeyboard = MyKeyboard(contextForKeyboardCreation, R.xml.symbols_keyboard, 0, targetWidth, 0)
            symbols1Keyboard = MyKeyboard(contextForKeyboardCreation, R.xml.symbols1_keyboard, 0, targetWidth, 0)
            numPadKeyboard = MyKeyboard(contextForKeyboardCreation, R.xml.number_pad_keyboard, 0, targetWidth, 0)
            gridNumbersKeyboard = MyKeyboard(contextForKeyboardCreation, R.xml.grid_numbers_keyboard, 0, targetWidth, 0)

            // Load Split Layouts
            splitGurmukhiKeyboard = MyKeyboard(contextForKeyboardCreation, R.xml.split_gurmukhi, 0, targetWidth, 0)
            splitEnglishKeyboard = MyKeyboard(contextForKeyboardCreation, R.xml.split_qwerty, 0, targetWidth, 0)

            Log.d(TAG, "All keyboards loaded successfully")
            updateSpaceBarLabel(currentKeyboardType)
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Failed to load keyboards", e)
        }
    }

    fun getCurrentKeyboard(): MyKeyboard? {
        val oneHandedMode = sharedPreferences.getString("pref_one_handed_mode", "off")
        val isSplit = oneHandedMode == "split"

        return when (currentKeyboardType) {
            KeyboardType.GURMUKHI -> if (isSplit) splitGurmukhiKeyboard else gurmukhiKeyboard
            KeyboardType.ENGLISH -> if (isSplit) splitEnglishKeyboard else englishKeyboard
            KeyboardType.SYMBOLS -> symbolsKeyboard
            KeyboardType.SYMBOLS1 -> symbols1Keyboard
            KeyboardType.NUMPAD -> numPadKeyboard
            KeyboardType.GRID_NUMBERS -> gridNumbersKeyboard
            else -> null
        }
    }

    fun switchKeyboard(newType: KeyboardType): MyKeyboard? {
        if (newType == KeyboardType.GURMUKHI || newType == KeyboardType.ENGLISH) {
            lastAlphabeticKeyboard = newType
        }
        currentKeyboardType = newType
        isGurmukhiShifted = false
        isSymbolsShifted = false

        // Reset English shift - will be re-evaluated by updateEnglishShiftState if needed
        isEnglishShifted = false
        isEnglishCapsLock = false

        gurmukhiKeyboard?.setShifted(false)
        splitGurmukhiKeyboard?.setShifted(false)

        val engShift = isEnglishShifted
        englishKeyboard?.apply { setShifted(engShift); setCapsLock(false) }
        splitEnglishKeyboard?.apply { setShifted(engShift); setCapsLock(false) }

        symbolsKeyboard?.setShifted(false)
        symbols1Keyboard?.setShifted(false)
        numPadKeyboard?.setShifted(false)
        gridNumbersKeyboard?.setShifted(false)

        updateSpaceBarLabel(newType)

        return getCurrentKeyboard()
    }

    private fun updateSpaceBarLabel(type: KeyboardType) {
        val gurmukhiKbs = listOfNotNull(gurmukhiKeyboard, splitGurmukhiKeyboard)
        for (kb in gurmukhiKbs) {
            val spaceKey = kb.keys.find { it.codes.contains(32) } ?: continue
            spaceKey.icon = null
            spaceKey.label = "ਪੰਜਾਬੀ"
        }

        val englishKbs = listOfNotNull(englishKeyboard, splitEnglishKeyboard)
        for (kb in englishKbs) {
            val spaceKey = kb.keys.find { it.codes.contains(32) } ?: continue
            spaceKey.icon = null
            spaceKey.label = "English"
        }

        val otherKbs = listOfNotNull(symbolsKeyboard, symbols1Keyboard, numPadKeyboard, gridNumbersKeyboard)
        for (kb in otherKbs) {
            val spaceKey = kb.keys.find { it.codes.contains(32) } ?: continue
            spaceKey.icon = ContextCompat.getDrawable(context, R.drawable.ic_space_bar)
            spaceKey.label = null

            // 🔥 Fix: Update language switch key label based on last alphabetic keyboard
            val langKey = kb.keys.find { it.codes.contains(ImeConstants.KEYCODE_LANGUAGE_SWITCH) } ?: continue
            langKey.label = if (lastAlphabeticKeyboard == KeyboardType.GURMUKHI) "ੳਅੲ" else "abc"
        }
    }

    fun handleShift() {
        val currentKb = getCurrentKeyboard() ?: return
        if (!currentKb.hasShiftKey()) return

        val isSplit = sharedPreferences.getString("pref_one_handed_mode", "off") == "split"

        when (currentKeyboardType) {
            KeyboardType.GURMUKHI -> {
                isGurmukhiShifted = !isGurmukhiShifted
                if (isSplit) splitGurmukhiKeyboard?.setShifted(isGurmukhiShifted) else gurmukhiKeyboard?.setShifted(isGurmukhiShifted)
            }
            KeyboardType.ENGLISH -> handleEnglishShift()
            KeyboardType.SYMBOLS -> {
                isSymbolsShifted = !isSymbolsShifted
                symbolsKeyboard?.setShifted(isSymbolsShifted)
            }
            KeyboardType.SYMBOLS1 -> {
            }
            else -> {}
        }
    }

    private fun handleEnglishShift() {
        val now = SystemClock.uptimeMillis()
        val isSplit = sharedPreferences.getString("pref_one_handed_mode", "off") == "split"

        when {
            isEnglishCapsLock -> { isEnglishCapsLock = false; isEnglishShifted = false }
            isEnglishShifted && (now - lastShiftPressTime < doubleTapThreshold) -> { isEnglishCapsLock = true; isEnglishShifted = true }
            isEnglishShifted -> { isEnglishShifted = false }
            else -> { isEnglishShifted = true }
        }
        lastShiftPressTime = now

        if (isSplit) {
            splitEnglishKeyboard?.setCapsLock(isEnglishCapsLock)
            splitEnglishKeyboard?.setShifted(isEnglishShifted)
        } else {
            englishKeyboard?.setCapsLock(isEnglishCapsLock)
            englishKeyboard?.setShifted(isEnglishShifted)
        }
    }

    fun isCurrentKeyboardShifted(): Boolean = when (currentKeyboardType) {
        KeyboardType.GURMUKHI -> isGurmukhiShifted
        KeyboardType.ENGLISH -> isEnglishShifted || isEnglishCapsLock
        KeyboardType.SYMBOLS -> isSymbolsShifted
        else -> false
    }

    fun unshiftIfNeeded(ic: android.view.inputmethod.InputConnection? = null, editorInfo: android.view.inputmethod.EditorInfo? = null) {
        val isSplit = sharedPreferences.getString("pref_one_handed_mode", "off") == "split"
        when (currentKeyboardType) {
            KeyboardType.GURMUKHI -> {
                if (isGurmukhiShifted) {
                    isGurmukhiShifted = false
                    if (isSplit) splitGurmukhiKeyboard?.setShifted(false) else gurmukhiKeyboard?.setShifted(false)
                }
            }
            KeyboardType.ENGLISH -> {
                if (isEnglishCapsLock) return
                if (isEnglishShifted) {
                    isEnglishShifted = false
                    if (isSplit) splitEnglishKeyboard?.setShifted(false) else englishKeyboard?.setShifted(false)
                }
                updateEnglishShiftState(ic, editorInfo)
            }
            else -> {}
        }
    }

    fun updateEnglishShiftState(ic: android.view.inputmethod.InputConnection?, editorInfo: android.view.inputmethod.EditorInfo?) {
        if (currentKeyboardType != KeyboardType.ENGLISH || isEnglishCapsLock) return

        val inputType = editorInfo?.inputType ?: 0
        if ((inputType and android.text.InputType.TYPE_MASK_CLASS) != android.text.InputType.TYPE_CLASS_TEXT) return

        val textBefore = ic?.getTextBeforeCursor(3, 0) ?: ""

        var shouldShift = false

        if ((inputType and android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
            shouldShift = true
        } else if ((inputType and android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0) {
            if (textBefore.endsWith(" ")) shouldShift = true
        } else {
            // Default: Cap Sentences if flag is set
            val capSentences = (inputType and android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0
            if (capSentences) {
                val text = textBefore.toString()
                if (text.endsWith("\n") || text.endsWith(". ") || text.endsWith("? ") || text.endsWith("! ")) {
                    shouldShift = true
                }
            }
        }

        if (isEnglishShifted != shouldShift) {
            isEnglishShifted = shouldShift
            val isSplit = sharedPreferences.getString("pref_one_handed_mode", "off") == "split"
            if (isSplit) {
                splitEnglishKeyboard?.setShifted(isEnglishShifted)
            } else {
                englishKeyboard?.setShifted(isEnglishShifted)
            }
        }
    }
}
