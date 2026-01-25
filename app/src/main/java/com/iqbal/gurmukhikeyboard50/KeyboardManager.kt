package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log

class KeyboardManager(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) {

    var gurmukhiKeyboard: MyKeyboard? = null
    var englishKeyboard: MyKeyboard? = null
    var symbolsKeyboard: MyKeyboard? = null
    var numPadKeyboard: MyKeyboard? = null
    
    // Split Keyboards
    var splitGurmukhiKeyboard: MyKeyboard? = null
    var splitEnglishKeyboard: MyKeyboard? = null

    var currentKeyboardType: KeyboardType = KeyboardType.GURMUKHI

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
            numPadKeyboard = MyKeyboard(contextForKeyboardCreation, R.xml.number_pad_keyboard, 0, targetWidth, 0)
            
            // Load Split Layouts
            splitGurmukhiKeyboard = MyKeyboard(contextForKeyboardCreation, R.xml.split_gurmukhi, 0, targetWidth, 0)
            splitEnglishKeyboard = MyKeyboard(contextForKeyboardCreation, R.xml.split_qwerty, 0, targetWidth, 0)
            
            Log.d(TAG, "All keyboards loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Failed to load keyboards", e)
        }
    }

    fun getCurrentKeyboard(): MyKeyboard? {
        val oneHandedMode = sharedPreferences.getString("pref_one_handed_mode", "off")
        val isSplit = oneHandedMode == "split"

        return when (currentKeyboardType) {
            KeyboardType.GURMUKHI -> if (isSplit) splitGurmukhiKeyboard else gurmukhiKeyboard
            KeyboardType.ENGLISH, KeyboardType.PHONETIC -> if (isSplit) splitEnglishKeyboard else englishKeyboard
            KeyboardType.SYMBOLS -> symbolsKeyboard
            KeyboardType.NUMPAD -> numPadKeyboard
            else -> null
        }
    }

    fun switchKeyboard(newType: KeyboardType): MyKeyboard? {
        currentKeyboardType = newType
        isGurmukhiShifted = false
        isEnglishShifted = false
        isEnglishCapsLock = false
        isSymbolsShifted = false
        
        gurmukhiKeyboard?.setShifted(false)
        splitGurmukhiKeyboard?.setShifted(false)
        englishKeyboard?.apply { setShifted(false); setCapsLock(false) }
        splitEnglishKeyboard?.apply { setShifted(false); setCapsLock(false) }
        symbolsKeyboard?.setShifted(false)
        numPadKeyboard?.setShifted(false)
        
        return getCurrentKeyboard()
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
            KeyboardType.ENGLISH, KeyboardType.PHONETIC -> handleEnglishShift()
            KeyboardType.SYMBOLS -> {
                isSymbolsShifted = !isSymbolsShifted
                symbolsKeyboard?.setShifted(isSymbolsShifted)
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
        KeyboardType.ENGLISH, KeyboardType.PHONETIC -> isEnglishShifted || isEnglishCapsLock
        KeyboardType.SYMBOLS -> isSymbolsShifted
        else -> false
    }

    fun unshiftIfNeeded() {
        val isSplit = sharedPreferences.getString("pref_one_handed_mode", "off") == "split"
        when (currentKeyboardType) {
            KeyboardType.GURMUKHI -> {
                if (isGurmukhiShifted) {
                    isGurmukhiShifted = false
                    if (isSplit) splitGurmukhiKeyboard?.setShifted(false) else gurmukhiKeyboard?.setShifted(false)
                }
            }
            KeyboardType.ENGLISH, KeyboardType.PHONETIC -> {
                if (isEnglishShifted && !isEnglishCapsLock) {
                    isEnglishShifted = false
                    if (isSplit) splitEnglishKeyboard?.setShifted(false) else englishKeyboard?.setShifted(false)
                }
            }
            else -> {}
        }
    }
}
