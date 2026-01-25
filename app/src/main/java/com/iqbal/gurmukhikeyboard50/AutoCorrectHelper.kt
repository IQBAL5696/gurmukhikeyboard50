package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.min

class AutoCorrectHelper(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)

    private val properNouns = setOf("iqbal", "singh", "kaur", "guru", "nanak", "gobind", "sahib")

    // Expanded built-in list for better out-of-the-box corrections.
    private val builtInDictionary = listOf(
        // Common Punjabi Words
        "ਪਿਆਰ", "ਵਾਹਿਗੁਰੂ", "ਗੁਰਮੁਖੀ", "ਸਤਿਨਾਮ", "ਪੰਜਾਬ", "ਖਾਲਸਾ", "ਸਿੰਘ", "ਕੌਰ",
        "ਅੰਮ੍ਰਿਤਸਰ", "ਚੰਡੀਗੜ੍ਹ", "ਪੰਜਾਬੀ", "ਗੁਰੂ", "ਨਾਨਕ", "ਗੋਬਿੰਦ", "ਹਰਿਮੰਦਰ", "ਸਾਹਿਬ",

        // Common Names
        "ਹਰਪ੍ਰੀਤ", "ਮਨਪ੍ਰੀਤ", "ਸੁਖਪ੍ਰੀਤ", "ਗੁਰਪ੍ਰੀਤ", "ਜਸਪ੍ਰੀਤ", "ਅਮਰਪ੍ਰੀਤ",
        "ਹਰਿੰਦਰ", "ਪਰਮਵੀਰ", "ਜਸਵਿੰਦਰ", "ਸੁਖਵਿੰਦਰ", "ਮਨਜਿੰਦਰ", "ਰਵਿੰਦਰ",

        // Common English Words
        "love", "hello", "world", "keyboard", "android", "singh", "kaur", "punjabi",
        "what", "when", "where", "who", "why", "how", "the", "and", "for", "you", "note", "health",

        // AI Command Keywords
        "help", "show", "commands", "poem", "tell", "joke", "synonym", "formal", "continue",
        "writing", "correct", "grammar", "word", "draft", "email", "letter", "capital",
        "weather", "today", "who", "what", "where", "when", "why", "how"
    )

    // 🔹 Main Function: Suggest corrected version of a word
    suspend fun autoCorrectWord(input: String): String = withContext(Dispatchers.IO) {
        if (input.isBlank()) return@withContext input

        // 1. Get candidates from the database
        val dbCandidates = dbHelper.getCorrectionCandidates(input)

        // 2. Combine database candidates with the built-in list
        val allCandidates = (dbCandidates + builtInDictionary).distinct()

        if (allCandidates.isEmpty()) {
            return@withContext input
        }

        var bestMatch = input
        var bestDistance = Int.MAX_VALUE

        for (word in allCandidates) {
            val distance = levenshteinDistance(input.lowercase(Locale.getDefault()), word.lowercase(Locale.getDefault()))
            if (distance < bestDistance) {
                bestDistance = distance
                bestMatch = word
            }
        }

        // Correct if distance is 1, or if distance is 2 for longer words.
        val result = if (bestDistance == 1 || (bestDistance == 2 && input.length > 4)) {
            Log.d("AutoCorrect", "Corrected '$input' -> '$bestMatch' (distance: $bestDistance)")
            bestMatch
        } else {
            input
        }
        return@withContext result
    }

    suspend fun checkGrammarOffline(sentence: String): String = withContext(Dispatchers.Default) {
        if (sentence.isBlank()) {
            return@withContext sentence
        }
        var corrected = sentence.trim()

        // Rule 1: Capitalize the first letter of the sentence.
        if (corrected.isNotEmpty()) {
            corrected = corrected.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }

        // Rule 2: Replace common informalities and typos.
        corrected = corrected.replace(Regex("\bi\b"), "I")
        corrected = corrected.replace(Regex("\bu\b", RegexOption.IGNORE_CASE), "you")
        corrected = corrected.replace(Regex("\bur\b", RegexOption.IGNORE_CASE), "your")
        corrected = corrected.replace(Regex("\bwanna\b", RegexOption.IGNORE_CASE), "want to")
        corrected = corrected.replace(Regex("\bgonna\b", RegexOption.IGNORE_CASE), "going to")
        corrected = corrected.replace(Regex("\bgotta\b", RegexOption.IGNORE_CASE), "have to")
        corrected = corrected.replace(Regex("\blemme\b", RegexOption.IGNORE_CASE), "let me")
        corrected = corrected.replace(Regex("\bshould of\b", RegexOption.IGNORE_CASE), "should have")
        corrected = corrected.replace(Regex("\bcould of\b", RegexOption.IGNORE_CASE), "could have")
        corrected = corrected.replace(Regex("\bwould of\b", RegexOption.IGNORE_CASE), "would have")

        // Rule 3: Capitalize proper nouns like names.
        val words = corrected.split(" ").map { word ->
            val cleanWord = word.replace(Regex("[^a-zA-Z]"), "").lowercase(Locale.getDefault())
            if (properNouns.contains(cleanWord)) {
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            } else {
                word
            }
        }
        corrected = words.joinToString(" ")

        // Rule 4: Ensure there's a space after a comma, if it's missing.
        corrected = corrected.replace(Regex(",([^\\s])"), ", $1")

        Log.d("GrammarCheck", "Checked '$sentence' -> '$corrected'")

        // Return original if no changes, to avoid unnecessary modifications
        return@withContext if (corrected != sentence.trim()) corrected else sentence
    }

    fun autoCapitalize(word: String, textBeforeCursor: String): String {
        val trimmedTextBefore = textBeforeCursor.trim()
        val isFirstWord = trimmedTextBefore.isEmpty() ||
                trimmedTextBefore.endsWith(".") ||
                trimmedTextBefore.endsWith("?") ||
                trimmedTextBefore.endsWith("!")

        // Rule: "i" -> "I"
        if (word.equals("i", ignoreCase = true)) {
            return "I"
        }

        val cleanWord = word.replace(Regex("[^a-zA-Z]"), "").lowercase(Locale.getDefault())
        // Rule: Capitalize proper nouns
        if (properNouns.contains(cleanWord)) {
            return word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

        // Rule: Capitalize first word of a sentence
        if (isFirstWord) {
            return word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

        return word
    }


    // 🔹 Levenshtein algorithm (edit distance)
    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }

        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}
