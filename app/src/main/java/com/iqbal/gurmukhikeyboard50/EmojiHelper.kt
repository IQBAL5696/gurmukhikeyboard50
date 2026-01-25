package com.iqbal.gurmukhikeyboard50

import android.content.Context

object EmojiHelper {

    const val RECENT_CATEGORY_KEY = "🕒"

    fun getEmojiCategories(): List<String> {
        return EmojiData.categories.keys.toList()
    }

    fun getEmojisForCategory(categoryName: String, context: Context): List<String> {
        return if (categoryName == RECENT_CATEGORY_KEY) {
            RecentEmojiManager.getRecentEmojis(context)
        } else {
            EmojiData.categories[categoryName] ?: emptyList()
        }
    }

    fun getAllEmojisWithHeaders(context: Context): List<Any> {
        val items = mutableListOf<Any>()
        for (category in getEmojiCategories()) {
            items.add(category)
            items.addAll(getEmojisForCategory(category, context))
        }
        return items
    }

    fun getPositionForCategory(context: Context, category: String?): Int {
        if (category == null) return 0
        val items = getAllEmojisWithHeaders(context)
        return items.indexOf(category)
    }

    fun getCategoryForPosition(context: Context, position: Int): String? {
        val items = getAllEmojisWithHeaders(context)
        if (position < 0 || position >= items.size) return null
        for (i in position downTo 0) {
            val item = items[i]
            if (item is String && getEmojiCategories().contains(item)) {
                return item as String
            }
        }
        return null
    }
}
