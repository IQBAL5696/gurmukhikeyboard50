package com.iqbal.gurmukhikeyboard50

import android.content.Context
import androidx.preference.PreferenceManager
import java.io.BufferedReader
import java.io.InputStreamReader

object PunjabiNextWordLM {

    private val mainSourceMap = mutableMapOf<String, List<String>>()
    private val userLearnedMap = mutableMapOf<String, List<String>>()
    private val mainSourceWords = mutableSetOf<String>()
    private val userWords = mutableSetOf<String>()
    private val deletedWordsSet = mutableSetOf<String>()
    private const val PREF_DELETED_WORDS = "pref_deleted_words_set"
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        loadDeletedWords(context)
        loadMainSource(context)
        isInitialized = true
    }

    private fun loadDeletedWords(context: Context) {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val savedSet = prefs.getStringSet(PREF_DELETED_WORDS, null)
            if (!savedSet.isNullOrEmpty()) {
                deletedWordsSet.addAll(savedSet)
            }
        } catch (e: Exception) { }
    }

    private fun loadMainSource(context: Context) {
        try {
            context.assets.open("common_paragraphs.txt").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmedLine = line!!.trim()
                        if (trimmedLine.isNotEmpty()) {
                            val words = trimmedLine.split(Regex("\\s+")).map { 
                                it.trim('|', '॥', ',', '.', '!', '?', ';', ':', '"', '\'', '(', ')', '[', ']', '{', '}', '—', '-') 
                            }.filter { it.isNotEmpty() && !deletedWordsSet.contains(it) }
                            
                            mainSourceWords.addAll(words)
                            
                            // Reconstruct line for processing pairs
                            processLine(words.joinToString(" "), mainSourceMap)
                        }
                    }
                }
            }
        } catch (e: Exception) { }
    }

    fun deleteWord(context: Context, word: String) {
        if (word.isBlank()) return
        val clean = word.trim()
        deletedWordsSet.add(clean)
        mainSourceWords.remove(clean)
        userWords.remove(clean)

        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val currentSet = prefs.getStringSet(PREF_DELETED_WORDS, null)?.toMutableSet() ?: mutableSetOf()
            currentSet.add(clean)
            prefs.edit().putStringSet(PREF_DELETED_WORDS, currentSet).apply()
        } catch (e: Exception) { }

        mainSourceMap.remove(clean)
        userLearnedMap.remove(clean)

        for (entry in mainSourceMap.entries) {
            if (entry.value.contains(clean)) {
                entry.setValue(entry.value.filter { it != clean })
            }
        }
        for (entry in userLearnedMap.entries) {
            if (entry.value.contains(clean)) {
                entry.setValue(entry.value.filter { it != clean })
            }
        }
    }

    fun learnSequence(context: Context, prevWord: String, currentWord: String) {
        if (prevWord.isEmpty() || currentWord.isEmpty()) return
        if (deletedWordsSet.contains(currentWord) || deletedWordsSet.contains(prevWord)) return
        
        val currentPredictions = userLearnedMap[prevWord]?.toMutableList() ?: mutableListOf()
        if (!currentPredictions.contains(currentWord)) {
            currentPredictions.add(0, currentWord) // Newest first
            userLearnedMap[prevWord] = currentPredictions.take(20)
        }
        userWords.add(currentWord)
    }

    private fun processLine(line: String, targetMap: MutableMap<String, List<String>>) {
        val words = line.trim().split(Regex("\\s+"))
        if (words.size < 2) return
        for (i in 0 until words.size - 1) {
            val currentWord = words[i]
            val nextWord = words[i + 1]
            if (currentWord.isEmpty() || nextWord.isEmpty()) continue
            if (deletedWordsSet.contains(currentWord) || deletedWordsSet.contains(nextWord)) continue
            
            val currentPredictions = targetMap[currentWord]?.toMutableList() ?: mutableListOf()
            if (!currentPredictions.contains(nextWord)) {
                currentPredictions.add(nextWord)
                targetMap[currentWord] = currentPredictions.take(30)
            }
        }
    }

    fun predict(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        val lastWord = input.trim().split(Regex("\\s+")).last()
        
        val userPreds = userLearnedMap[lastWord] ?: emptyList()
        val mainPreds = mainSourceMap[lastWord] ?: emptyList()
        
        return (userPreds + mainPreds).filter { !deletedWordsSet.contains(it) }.distinct().take(20)
    }

    fun isInMainSource(word: String): Boolean {
        if (deletedWordsSet.contains(word)) return false
        return mainSourceWords.contains(word) || userWords.contains(word)
    }

    fun getMainSourceCompletions(prefix: String): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val userCompletions = userWords.filter { it.startsWith(prefix) && !deletedWordsSet.contains(it) }
        val mainCompletions = mainSourceWords.filter { it.startsWith(prefix) && !deletedWordsSet.contains(it) }
        return (userCompletions + mainCompletions).distinct().take(15)
    }

    fun getAllMainSourceWords(): Set<String> = (mainSourceWords + userWords) - deletedWordsSet
}
