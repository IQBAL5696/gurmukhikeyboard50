package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object DictionaryHelper {
    private const val TAG = "DictionaryHelper"
    private const val DICTIONARY_FILE = "gurmukhi_dictionary.txt"

    fun convertParagraphToDictionary(context: Context): Boolean {
        val file = context.getFileStreamPath(DICTIONARY_FILE)
        if (file.exists() && file.length() > 0) {
            return true
        }

        return try {
            val reader = BufferedReader(
                InputStreamReader(context.getAssets().open("paragraph.txt"), "UTF-8")
            )

            val paragraphBuilder = StringBuilder()
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                paragraphBuilder.append(line).append(" ")
            }
            reader.close()

            val words = paragraphBuilder.toString().split("\\s+".toRegex())
                .dropLastWhile { it.isEmpty() }.toTypedArray()

            val unique = mutableSetOf<String>()
            for (w in words) {
                val trimmedWord = w.trim()
                if (trimmedWord.isNotEmpty()) {
                    unique.add(trimmedWord)
                }
            }

            saveFullDictionary(context, unique.toList())
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error converting dictionary:", e)
            false
        }
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
        val trimmedWord = word.trim()
        if (trimmedWord.length < 2) return
        
        try {
            val writer = BufferedWriter(
                OutputStreamWriter(context.openFileOutput(DICTIONARY_FILE, Context.MODE_APPEND), "UTF-8")
            )
            writer.write(trimmedWord)
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

    fun deleteWordFromDictionary(context: Context, wordToDelete: String) {
        val allWords = getAllDictionaryWords(context).toMutableList()
        allWords.remove(wordToDelete)
        saveFullDictionary(context, allWords.reversed())
    }
}
