package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.content.SharedPreferences

object ShortcutsManager {
    private const val PREF_NAME = "keyboard_shortcuts"

    private val defaultShortcuts = mapOf(
        "ssa" to "ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ",
        "wahe" to "ਵਾਹਿਗੁਰੂ",
        "pk" to "ਪ੍ਰਣਾਮ",
        "gn" to "ਗੁਰੂ ਨਾਨਕ ਦੇਵ ਜੀ",
        "thx" to "ਧੰਨਵਾਦ",
        "ੲਕਬਲ" to "ਇਕਬਾਲ"
    )

    fun getShortcut(context: Context, key: String): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // Check for the key exactly as typed first
        val result = prefs.getString(key, defaultShortcuts[key])
        if (result != null) return result

        // Then try lowercase for English shortcuts
        return prefs.getString(key.lowercase(), defaultShortcuts[key.lowercase()])
    }

    fun addShortcut(context: Context, key: String, value: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(key, value).apply()
    }

    fun getAllShortcuts(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val all = prefs.all as Map<String, String?>
        val result = mutableMapOf<String, String>()
        all.forEach { (k, v) -> if (v != null) result[k] = v }
        if (result.isEmpty()) return defaultShortcuts
        return result
    }
}
