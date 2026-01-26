package com.iqbal.gurmukhikeyboard50

object EmojiSearchHelper {
    private val emojiMap = mapOf(
        "ਖੁਸ਼" to "😊", "happy" to "😊",
        "ਗੁੱਸਾ" to "😡", "angry" to "😡",
        "ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ" to "🙏", "hello" to "👋",
        "ਪਿਆਰ" to "❤️", "love" to "❤️",
        "ਹੱਸ" to "😂", "laugh" to "😂",
        "ਉਦਾਸ" to "😢", "sad" to "😢",
        "ਵਾਹ" to "😮", "wow" to "😮",
        "ਠੀਕ" to "👍", "ok" to "👍", "good" to "👍",
        "ਧੰਨਵਾਦ" to "🙏", "thanks" to "🙏",
        "ਫੁੱਲ" to "🌹", "flower" to "🌹",
        "ਦਿਲ" to "💖", "heart" to "💖"
    )

    fun searchEmoji(text: String): String? {
        return emojiMap[text.lowercase()]
    }
}
