package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.content.SharedPreferences

object ShortcutsManager {
    private const val PREF_NAME = "keyboard_shortcuts"

    private val defaultShortcuts = mapOf(
        "ssa" to "ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ",
        "wahe" to "ਵਾਹਿਗੁਰੂ",
        "thx" to "ਸਾਡੇ ਨਾਲ ਵਪਾਰ ਕਰਨ ਲਈ ਧੰਨਵਾਦ!",
        "acc" to "Bank: SBI, A/c: 1234567890, IFSC: SBIN0001234",
        "gst" to "GSTIN: 03AAAAA0000A1Z5",
        "addr" to "Main Bazar, near Clock Tower, Ludhiana, Punjab",
        "pay" to "ਕਿਰਪਾ ਕਰਕੇ ਪੇਮੈਂਟ ਕਰਕੇ ਸਕ੍ਰੀਨਸ਼ੌਟ ਭੇਜ ਦਿਓ ਜੀ।"
    )

    fun getShortcut(context: Context, key: String): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        // Check for user-defined first
        val result = prefs.getString(key, null)
        if (result != null) return result
        
        // Then check defaults
        val defaultResult = defaultShortcuts[key]
        if (defaultResult != null) return defaultResult

        // Finally try lowercase for English
        return prefs.getString(key.lowercase(), defaultShortcuts[key.lowercase()])
    }

    fun addShortcut(context: Context, key: String, value: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(key, value).apply()
    }

    fun deleteShortcut(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(key).apply()
    }

    fun getAllShortcuts(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val allUserShortcuts = prefs.all as Map<String, String?>
        
        val result = defaultShortcuts.toMutableMap()
        allUserShortcuts.forEach { (k, v) -> if (v != null) result[k] = v }
        return result
    }
}
