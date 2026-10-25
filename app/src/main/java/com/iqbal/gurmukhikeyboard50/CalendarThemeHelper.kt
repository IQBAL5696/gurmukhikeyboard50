package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.content.res.Configuration
import androidx.preference.PreferenceManager

object CalendarThemeHelper {
    const val PREF_CALENDAR_THEME_MODE = "pref_calendar_theme_mode"
    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    fun getThemeMode(context: Context): String {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        return sharedPrefs.getString(PREF_CALENDAR_THEME_MODE, MODE_SYSTEM) ?: MODE_SYSTEM
    }

    fun setThemeMode(context: Context, mode: String) {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        sharedPrefs.edit().putString(PREF_CALENDAR_THEME_MODE, mode).apply()
    }

    fun isDarkMode(context: Context): Boolean {
        val mode = getThemeMode(context)
        if (mode == MODE_DARK) return true
        if (mode == MODE_LIGHT) return false

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val currentTheme = sharedPrefs.getString(ImeConstants.PREF_KEYBOARD_THEME, "system") ?: "system"
        if (currentTheme in listOf("dark", "midnight", "night_sky", "space", "indigo", "custom")) {
            return true
        }
        if (currentTheme != "system") {
            return false
        }
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }
}
