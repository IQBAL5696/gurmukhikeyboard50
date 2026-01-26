
package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class AiHelper(private val context: Context) {

    private val autoCorrectHelper = AutoCorrectHelper(context)
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    companion object {
        private const val TAG = "AiHelper"
        private const val NETWORK_TIMEOUT = 5000 // 5 seconds
    }

    private val synonyms = mapOf(
        "happy" to "joyful, cheerful, content, elated",
        "sad" to "unhappy, sorrowful, dejected, melancholy",
        "good" to "fine, excellent, great, superb",
        "bad" to "poor, terrible, awful, dreadful"
    )

    private val jokes = listOf(
        "Why don't scientists trust atoms? Because they make up everything!",
        "Why did the scarecrow win an award? Because he was outstanding in his field!",
        "What do you call a fake noodle? An Impasta!"
    )

    suspend fun getAiResponse(text: String): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext "Please type something."

        val originalText = text.trim()
        val normalizedText = originalText.lowercase(Locale.getDefault())

        // Priority 1: Local Commands (Including Correction)
        val localResponse = getLocalCommandResponse(normalizedText, originalText)
        if (localResponse != null) return@withContext localResponse

        // Priority 2: Use API Key if available
        val apiKey = sharedPreferences.getString(ImeConstants.PREF_AI_API_KEY, "")
        if (!apiKey.isNullOrBlank()) {
            val apiResponse = callExternalAiApi(originalText, apiKey)
            if (apiResponse != null) return@withContext apiResponse
        }

        // Priority 3: Fallback to Wikipedia
        if (!isNetworkAvailable()) {
            return@withContext "📡 No internet connection available."
        }

        return@withContext searchWikipedia(originalText) ?: "I couldn't find a direct answer. Try asking 'correct grammar: [your text]' or 'joke'."
    }

    private suspend fun callExternalAiApi(prompt: String, apiKey: String): String? {
        return try {
            val url = URL("https://api.openai.com/v1/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true

            val jsonBody = JSONObject().apply {
                put("model", "gpt-3.5-turbo")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", "You are a helpful writing assistant. If the user asks to 'correct grammar', fix their text. If they ask to 'continue writing', add a few sentences.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("max_tokens", 200)
            }

            connection.outputStream.use { it.write(jsonBody.toString().toByteArray()) }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                jsonResponse.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun searchWikipedia(query: String): String? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&format=json"
            val searchResponse = URL(searchUrl).readText()
            val searchJson = JSONObject(searchResponse)
            val searchResults = searchJson.optJSONObject("query")?.optJSONArray("search")

            if (searchResults != null && searchResults.length() > 0) {
                val topResultTitle = searchResults.getJSONObject(0).getString("title")
                val extractUrl = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&exintro&explaintext&titles=${URLEncoder.encode(topResultTitle, "UTF-8")}&format=json"
                val extractResponse = URL(extractUrl).readText()
                val extractJson = JSONObject(extractResponse)
                val pages = extractJson.getJSONObject("query").getJSONObject("pages")
                val pageId = pages.keys().next()
                pages.getJSONObject(pageId).optString("extract").takeIf { it.isNotEmpty() }
            } else null
        } catch (e: Exception) { null }
    }

    private suspend fun getLocalCommandResponse(normalizedText: String, originalText: String): String? {
        return when {
            normalizedText.startsWith("correct grammar:") -> {
                val content = originalText.substringAfter(":").trim()
                autoCorrectHelper.checkGrammarOffline(content)
            }
            normalizedText.startsWith("correct word:") -> {
                val word = originalText.substringAfter(":").trim()
                autoCorrectHelper.autoCorrectWord(word)
            }
            normalizedText == "joke" -> "😂 ${jokes.random()}"
            normalizedText.contains("who are you") -> "I am your AI Keyboard Assistant with Auto-Correction features."
            else -> null
        }
    }
}
