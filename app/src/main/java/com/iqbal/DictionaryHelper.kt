package com.iqbal

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.FileInputStream

object DictionaryHelper {
    private const val TAG = "DictionaryHelper"
    private const val DICTIONARY_FILE = "gurmukhi_dictionary.txt"

    fun convertParagraphToDictionary(context: Context): Boolean {
        val file = context.getFileStreamPath(DICTIONARY_FILE)
        if (file.exists() && file.length() > 0) {
            sanitizeExistingDictionary(context)
            return true
        }

        return try {
            val reader = BufferedReader(
                InputStreamReader(context.getAssets().open("paragraph.txt"), "UTF-8")
            )

            val paragraphBuilder = StringBuilder()
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                paragraphBuilder.append(line).append(" \n")
            }
            reader.close()

            // Split by whitespace and all punctuation/digits to isolate pure words
            val rawWords = paragraphBuilder.toString().split("[\\s,;.:=()\"'।॥/?!\\-\\[\\]{}<>*+_\\\\|0-9੦-੯]+".toRegex())
            
            val unique = LinkedHashSet<String>()
            for (w in rawWords) {
                val cleaned = cleanWord(w)
                if (isValidWord(cleaned)) {
                    unique.add(cleaned)
                }
            }

            saveFullDictionary(context, unique.toList())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error during dictionary conversion:", e)
            false
        }
    }

    private fun sanitizeExistingDictionary(context: Context) {
        try {
            val words = getAllDictionaryWords(context)
            val cleaned = words.map { cleanWord(it) }.filter { isValidWord(it) }.distinct()
            saveFullDictionary(context, cleaned.reversed())
        } catch (e: Exception) {
            Log.e(TAG, "Error sanitizing dictionary", e)
        }
    }

    fun cleanWord(word: String): String {
        // Remove any unwanted punctuation, symbols, or digits from anywhere in the word
        return word.trim().replace("['\"।॥/?!,.=()\\-\\[\\]{}:;<>*+_\\\\|0-9੦-੯]+".toRegex(), "")
    }

    fun isValidWord(word: String): Boolean {
        if (word.length < 2) return false
        
        // Final check: should not contain any digits or restricted symbols
        val hasDigits = word.any { it.isDigit() || "੦੧੨੩੪੫੬੭੮੯".contains(it) }
        if (hasDigits) return false
        
        val hasSymbols = word.any { ",.=()!?;:\"'।॥/\\-[]{}<>*+_|".contains(it) }
        if (hasSymbols) return false
        
        return true
    }

    private fun saveFullDictionary(context: Context, words: List<String>) {
        try {
            val writer = BufferedWriter(
                OutputStreamWriter(context.openFileOutput(DICTIONARY_FILE, Context.MODE_PRIVATE), "UTF-8")
            )
            for (w in words) {
                writer.write(w)
                writer.newLine()
            }
            writer.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error saving dictionary", e)
        }
    }

    fun addWordToDictionary(context: Context, word: String) {
        val cleaned = cleanWord(word)
        if (!isValidWord(cleaned)) return
        
        try {
            val writer = BufferedWriter(
                OutputStreamWriter(context.openFileOutput(DICTIONARY_FILE, Context.MODE_APPEND), "UTF-8")
            )
            writer.write(cleaned)
            writer.newLine()
            writer.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error adding word", e)
        }
    }

    fun getAllDictionaryWords(context: Context): List<String> {
        val words = mutableListOf<String>()
        try {
            val fis = context.openFileInput(DICTIONARY_FILE)
            val reader = BufferedReader(InputStreamReader(fis, "UTF-8"))
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                words.add(line!!)
            }
            reader.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading words", e)
        }
        return words.reversed().distinct() // Most recent first
    }

    private const val MAX_SUGGESTIONS = 15

    fun getSuggestions(context: Context, partialWord: String?): MutableList<String?> {
        val suggestions: MutableList<String?> = ArrayList()
        if (partialWord.isNullOrBlank()) return suggestions

        val trimmedPartial = partialWord.trim()
        var fis: FileInputStream? = null
        var reader: BufferedReader? = null

        try {
            fis = context.openFileInput(DICTIONARY_FILE)
            reader = BufferedReader(InputStreamReader(fis, "UTF-8"))
            var line: String?
            while ((reader.readLine().also { line = it }) != null && suggestions.size < MAX_SUGGESTIONS) {
                val cleanedLine = cleanWord(line!!)
                if (cleanedLine.startsWith(trimmedPartial)) {
                    if (!suggestions.contains(cleanedLine)) {
                        suggestions.add(cleanedLine)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading suggestions:", e)
        } finally {
            try {
                reader?.close()
                fis?.close()
            } catch (e: IOException) {}
        }
        return suggestions
    }
}
