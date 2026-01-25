package com.iqbal;

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object DictionaryHelper {
    private const val TAG = "DictionaryHelper"

    // Convert paragraph.txt (from assets) -> gurmukhi_dictionary.txt (in internal storage)
    fun convertParagraphToDictionary(context: Context): Boolean {
        return try {
            // 1. Read paragraph.txt from assets
            val reader = BufferedReader(
                InputStreamReader(context.getAssets().open("paragraph.txt"), "UTF-8")
            )

            val paragraphBuilder = StringBuilder()
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                paragraphBuilder.append(line)
                    .append(" \n") // Preserve newlines from paragraph as spaces for word splitting
            }
            reader.close()

            val paragraph = paragraphBuilder.toString()

            // 2. Split into words (handles spaces and newlines due to the append above)
            val words =
                paragraph.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            // 3. Remove duplicates and trim
            val unique: MutableSet<String> = LinkedHashSet<String>()
            for (w in words) {
                val trimmedWord = w.trim { it <= ' ' }
                if (!trimmedWord.isEmpty()) {
                    unique.add(trimmedWord)
                }
            }

            // 4. Save into app’s files dir as gurmukhi_dictionary.txt
            val writer = BufferedWriter(
                OutputStreamWriter(
                    context.openFileOutput("gurmukhi_dictionary.txt", Context.MODE_PRIVATE), "UTF-8"
                )
            )

            for (w in unique) {
                writer.write(w)
                writer.newLine() // Write each unique word on a new line
            }

            writer.close()
            Log.d(TAG, "Successfully converted paragraph.txt to gurmukhi_dictionary.txt in internal storage.")
            true // Return true on success
        } catch (e: IOException) {
            Log.e(TAG, "Error during dictionary conversion:", e)
            false // Return false on IOException
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during dictionary conversion:", e)
            false // Return false on any other exception
        }
    }

    private const val MAX_SUGGESTIONS = 15 // Limit re-added and set to 15

    fun getSuggestions(context: Context, partialWord: String?): MutableList<String?> {
        val suggestions: MutableList<String?> = ArrayList<String?>()
        if (partialWord == null || partialWord.trim { it <= ' ' }.isEmpty()) {
            return suggestions // Return empty list if partialWord is empty or just whitespace
        }

        val trimmedPartialWord = partialWord.trim { it <= ' ' }
        var fis: FileInputStream? = null
        var reader: BufferedReader? = null

        try {
            fis = context.openFileInput("gurmukhi_dictionary.txt")
            reader = BufferedReader(InputStreamReader(fis, "UTF-8"))
            var line: String?
            while ((reader.readLine()
                    .also { line = it }) != null && suggestions.size < MAX_SUGGESTIONS
            ) { // Limit re-added here
                if (line!!.startsWith(trimmedPartialWord)) {
                    suggestions.add(line)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading suggestions from gurmukhi_dictionary.txt:", e) 
        } finally {
            try {
                if (reader != null) {
                    reader.close()
                }
                if (fis != null) {
                    fis.close()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error closing streams in getSuggestions:", e)
            }
        }
        return suggestions
    }
}
