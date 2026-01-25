package com.iqbal.gurmukhikeyboard50

import android.content.Context;
import android.content.SharedPreferences;

object RecentEmojiManager {

    private const val PREFS_NAME = "RecentEmojisPrefs";
    private const val KEY_RECENT_EMOJIS = "recent_emojis_list";
    private const val MAX_RECENT_EMOJIS = 30; // Max number of recent emojis to store

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    fun addEmoji(context: Context, emoji: String) {
        val prefs = getSharedPreferences(context);
        val currentRecents = getRecentEmojis(context).toMutableList();

        // Remove if already present to add it to the top (most recent)
        currentRecents.remove(emoji);
        currentRecents.add(0, emoji); // Add to the beginning

        // Limit the list size
        val limitedRecents = if (currentRecents.size > MAX_RECENT_EMOJIS) {
            currentRecents.subList(0, MAX_RECENT_EMOJIS);
        } else {
            currentRecents;
        };

        prefs.edit().putString(KEY_RECENT_EMOJIS, limitedRecents.joinToString(",")).apply();
    }

    fun getRecentEmojis(context: Context): List<String> {
        val prefs = getSharedPreferences(context);
        val savedString = prefs.getString(KEY_RECENT_EMOJIS, null);
        return savedString?.split(",")?.filter { it.isNotEmpty() } ?: emptyList();
    }
}
