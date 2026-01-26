package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.min

class AutoCorrectHelper(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)

    private val properNouns = setOf("iqbal", "singh", "kaur", "guru", "nanak", "gobind", "sahib", "ludhiana")

    // Enhanced dictionary for common English corrections
    private val englishDictionary = setOf(
        "i", "am", "going", "to", "school", "the", "is", "on", "at", "where", "what", "how",
        "and", "but", "for", "with", "have", "you", "they", "we", "he", "she", "it", "my",
        "name", "from", "come", "coming", "go", "went", "gone", "will", "shall", "are", "were"
    )

    private val builtInDictionary = listOf(
        "ਮੈਂ", "ਤੂੰ", "ਉਹ", "ਅਸੀਂ", "ਤੁਸੀਂ", "ਮੇਰਾ", "ਤੇਰਾ", "ਸਾਡਾ", "ਇਹ", "ਕੀ", "ਕਿਉਂ", "ਕਦੋਂ", "ਕਿੱਥੋਂ", "ਕਿੱਥੇ", "ਕੌਣ", "ਹਾਂ", "ਨਹੀਂ", "ਠੀਕ", "ਗੱਲ", "ਗੱਲਾਂ",
        "ਵਾਹਿਗੁਰੂ", "ਗੁਰਮੁਖੀ", "ਸਤਿਨਾਮ", "ਪੰਜਾਬ", "ਖਾਲਸਾ", "ਹਰਿਮੰਦਰ", "ਸਾਹਿਬ", "ਗੁਰੂ", "ਨਾਨਕ", "ਗੋਬਿੰਦ", "ਸਿਮਰਨ", "ਸੇਵਾ", "ਅਰਦਾਸ", "ਨਿਤਨੇਮ", "ਹਵਾਲਾ", "ਬਾਣੀ"
    )

    suspend fun autoCorrectWord(input: String): String = withContext(Dispatchers.IO) {
        if (input.isBlank()) return@withContext input
        val cleanInput = input.lowercase()
        
        // If it's already a perfect match in our English dictionary, leave it
        if (englishDictionary.contains(cleanInput)) return@withContext if (cleanInput == "i") "I" else input
        
        val dbCandidates = dbHelper.getCorrectionCandidates(input)
        val allCandidates = (dbCandidates + builtInDictionary + englishDictionary).distinct()
        if (allCandidates.isEmpty()) return@withContext input

        var bestMatch = input
        var bestDistance = Int.MAX_VALUE

        for (word in allCandidates) {
            val distance = levenshteinDistance(cleanInput, word.lowercase())
            if (distance < bestDistance) {
                bestDistance = distance
                bestMatch = word
            }
        }

        // Return best match if it's very close (1-2 characters difference)
        return@withContext if (bestDistance == 1 || (bestDistance == 2 && input.length > 3)) {
            if (bestMatch.lowercase() == "i") "I" else bestMatch
        } else input
    }

    suspend fun checkGrammarOffline(sentence: String): String = withContext(Dispatchers.Default) {
        if (sentence.isBlank()) return@withContext sentence
        
        val words = sentence.trim().split(Regex("\\s+"))
        val correctedWords = words.map { word ->
            autoCorrectWord(word)
        }
        var corrected = correctedWords.joinToString(" ")

        // Roman-to-Gurmukhi mappings
        val mappings = mapOf(
            "main" to "ਮੈਂ", "tu" to "ਤੂੰ", "oh" to "ਉਹ", "asi" to "ਅਸੀਂ", "tusi" to "ਤੁਸੀਂ",
            "mera" to "ਮੇਰਾ", "tera" to "ਤੇਰਾ", "sada" to "ਸਾਡਾ", "eh" to "ਇਹ", "ki" to "ਕੀ"
        )

        mappings.forEach { (roman, gurmukhi) ->
            corrected = corrected.replace(Regex("\\b$roman\\b", RegexOption.IGNORE_CASE), gurmukhi)
        }

        if (corrected.isNotEmpty()) {
            corrected = corrected.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        
        // Final polish for 'I'
        corrected = corrected.replace(Regex("\\bi\\b", RegexOption.IGNORE_CASE), "I")

        return@withContext corrected
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }
}
