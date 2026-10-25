package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.min

class AutoCorrectHelper(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)

    private val englishDictionary = setOf(
        "i",", YOU"
    )

    private val commonPunjabiCorrections = mapOf(
        "ਸ਼ਹ" to "ਸ਼ਹਿਰ",
        "ਮੈ" to "ਮੈਂ",
        "ੳਥੇ" to "ਉਥੇ",
        "ਸਿਖ" to "ਸਿੱਖ",
        "ਸਿਖਾ" to "ਸਿੱਖਾਂ",
    )

    private val builtInDictionary = listOf(
        "ਉਥੇ ਗਿਆ", "ਗਿਆ", "ਸ਼ਹਿਰ", "ਜ਼ਿੰਦਗੀ", "ਫ਼ੈਸਲਾ", "ਖ਼ਬਰ", "ਦੁਨੀਆ", "ਸਰਕਾਰ", "ਕਾਨੂੰਨ", "ਮਦਦ", "ਸਵਾਲ", "ਜਵਾਬ", "ਤਾਰੀਖ", "ਵਕੀਲ", "ਇਨਸਾਫ਼", "ਮੁਬਾਰਕ", "ਸ਼ੁਕਰੀਆ", "ਮਿਹਰਬਾਨੀ", "ਖ਼ਾਸ", "ਫ਼ਾਇਦਾ", "ਨੁਕਸਾਨ", "ਹਜ਼ਾਰ", "ਖ਼ੂਨ", "ਜ਼ਮੀਨ", "ਦੋਸਤ", "ਦੁਸ਼ਮਣ", "ਇਸ਼ਕ", "ਮੁਹੱਬਤ", "ਸਫ਼ਰ", "ਮੁਸਾਫ਼ਿਰ", "ਤਾਕਤ", "ਹਿੰਮਤ", "ਕੋਸ਼ਿਸ਼", "ਇਰਾਦਾ", "ਅਸਰ", "ਨਤੀਜਾ", "ਤਜਰਬਾ", "ਖ਼ਿਆਲ", "ਉਮੀਦ", "ਕਿਸਮਤ", "ਗ਼ਰੀਬ", "ਅਮੀਰ", "ਸ਼ਾਹ", "ਵਜ਼ੀਰ", "ਫ਼ੌਜ", "ਸਿਪਾਹੀ", "ਜੰਗ", "ਅਮਨ", "ਗੁਲਾਮ", "ਆਜ਼ਾਦ", "ਕੈਦ", "ਰਿਹਾਈ", "ਕਿਤਾਬ", "ਕਲਮ", "ਕਾਗਜ਼", "ਮੇਜ਼", "ਕੁਰਸੀ", "ਅਦਾਲਤ", "ਕਚਹਿਰੀ", "ਜੁਰਮ", "ਗੁਨਾਹ", "ਵਿਸ਼ਵਾਸ", "ਹਕੀਕਤ", "ਮਸ਼ਹੂਰ", "ਕਿਸਮ", "ਬਾਜ਼ਾਰ", "ਖ਼ਰੀਦਣਾ", "ਫ਼ਰਜ਼", "ਗ਼ਲਤੀ", "ਖ਼ੁਸ਼ੀ", "ਗ਼ਮੀ", "ਦਰਦ", "ਆਰਾਮ", "ਸੁਨੇਹਾ", "ਤੌਲੀਆ", "ਸਾਬਣ", "ਕਮੀਜ਼", "ਸ਼ੀਸ਼ਾ", "ਕਿਰਾਇਆ", "ਮਾਲਕ", "ਨੌਕਰ", "ਸ਼ੱਕ", "ਇਤਬਾਰ", "ਬੇਇੱਜ਼ਤੀ", "ਮਿਹਨਤ", "ਗ਼ੈਰਤ", "ਅਣਖ", "ਖ਼ਾਨਦਾਨ", "ਸ਼ਰਮ"
    )

    suspend fun autoCorrectWord(input: String): String = withContext(Dispatchers.IO) {
        if (input.isBlank()) return@withContext input
        
        // Fast path for specific Punjabi typos
        commonPunjabiCorrections[input]?.let { return@withContext it }
        
        // Safety: Do not auto-correct single characters to prevent matra/letter swapping
        if (input.length == 1) return@withContext input
        
        val cleanInput = input.lowercase()
        val isGurmukhiInput = input.any { it in '\u0A00'..'\u0A7F' }

        // If it's already a perfect match in our English dictionary, leave it
        if (!isGurmukhiInput && englishDictionary.contains(cleanInput)) return@withContext if (cleanInput == "i") "I" else input

        val dbCandidates = dbHelper.getCorrectionCandidates(input)
        
        // 0. Include all words from common_paragraphs.txt (Main Source)
        val mainSourceWords = PunjabiNextWordLM.getAllMainSourceWords()
        
        // Filter candidates to ensure the first character matches for Gurmukhi (Strictness)
        val filteredBuiltIn = if (isGurmukhiInput) {
            builtInDictionary.filter { it.startsWith(input.first()) }
        } else builtInDictionary
        
        val filteredMainSource = if (isGurmukhiInput) {
            mainSourceWords.filter { it.startsWith(input.first()) }
        } else mainSourceWords

        var allCandidates = (dbCandidates + filteredBuiltIn + filteredMainSource + englishDictionary).distinct()
        
        // Safety: If the word is already valid, don't auto-correct it
        if (allCandidates.contains(input)) return@withContext input

        // Immediate prefix match for short inputs
        if (isGurmukhiInput && input.length >= 1) {
            val prefixMatch = allCandidates.find { it.startsWith(input) }
            if (prefixMatch != null && prefixMatch.length > input.length) {
                // Check if it's a high-confidence match (e.g. only matras or 1 char difference)
                val distance = levenshteinDistance(input, prefixMatch)
                if (distance <= 2) return@withContext prefixMatch
            }
        }
        
        // Fallback: If no candidates match the first character, try with a broader set
        if (isGurmukhiInput && allCandidates.size < 5) {
            allCandidates = (allCandidates + builtInDictionary.take(100) + mainSourceWords.take(100)).distinct()
        }

        if (allCandidates.isEmpty()) return@withContext input

        // 1. Try Matra-less matching for Gurmukhi (e.g., "ਸਵਧਨ" -> "ਸਾਵਧਾਨ")
        if (isGurmukhiInput) {
            val inputSkeleton = stripMatras(input)
            if (inputSkeleton.length >= 2) { 
                val matches = allCandidates.filter { stripMatras(it) == inputSkeleton }
                if (matches.isNotEmpty()) {
                    // Pick the best match (e.g. closest in length or exact prefix if input has some matras)
                    return@withContext matches.minByOrNull { levenshteinDistance(input, it) } ?: input
                }
            }
        }

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
        // And for Gurmukhi, ensure the first character is the same to avoid "ਭਲ" -> "ਫਲ"
        // Also ensure that the bestMatch actually starts with the input prefix for Gurmukhi to be strict
        val firstCharMatch = if (isGurmukhiInput) bestMatch.startsWith(input.first()) else true
        val isPrefixMatch = if (isGurmukhiInput) bestMatch.startsWith(input) else true

        // Extremely strict check for Gurmukhi: first character must match unless it's a known correction
        if (isGurmukhiInput && !firstCharMatch && !commonPunjabiCorrections.containsKey(input)) {
            return@withContext input
        }

        return@withContext if (firstCharMatch && (bestDistance == 1 || (bestDistance == 2 && input.length > 3))) {
            // For Gurmukhi, only auto-correct if it's a known correction or a strict prefix match
            if (isGurmukhiInput && !isPrefixMatch && !commonPunjabiCorrections.containsKey(input)) {
                input 
            } else {
                if (bestMatch.lowercase() == "i") "I" else bestMatch
            }
        } else input
    }

    fun isCommonCorrection(input: String): Boolean {
        return commonPunjabiCorrections.containsKey(input)
    }

    fun isMatraCorrection(input: String, corrected: String): Boolean {
        if (input == corrected) return false
        return stripMatras(input) == stripMatras(corrected)
    }

    fun stripMatras(word: String): String {
        val matras = setOf(
            '\u0A3E', '\u0A3F', '\u0A40', '\u0A41', '\u0A42', '\u0A47', '\u0A48', '\u0A4B', '\u0A4C', // ਾ ਿ ੀ ੁ ੂ ੇ ੈ ੋ ੌ
            '\u0A02', '\u0A70', '\u0A71', // ਂ ੰ ੱ
            '\u0A4D', // ੍ (halant)
            '\u0A3C'  // ਼ (nukta)
        )
        // Also handle cases where user might have typed partial letters or slightly different vowel forms
        return word.filter { it !in matras }
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
