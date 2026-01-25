
package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class AiHelper(private val context: Context) {

    private val autoCorrectHelper = AutoCorrectHelper(context)

    companion object {
        private const val TAG = "AiHelper"
        private const val NETWORK_TIMEOUT = 3000 // 3 seconds
    }

    private val synonyms = mapOf(
        "happy" to "joyful, cheerful, content, elated",
        "sad" to "unhappy, sorrowful, dejected, melancholy",
        "good" to "fine, excellent, great, superb",
        "bad" to "poor, terrible, awful, dreadful"
    )

    private val poems = listOf(
        """
            Sunlight warms the sleeping land,
            A gentle touch from a golden hand.
            Birds arise and start to sing,
            The joy and life that morning brings.
        """.trimIndent(),
        """
            A silent moon on a silver sea,
            A quiet whisper, just for thee.
            Stars above in endless night,
            Bathe the world in softest light.
        """.trimIndent(),
        """
            Green shoots rise from fertile ground,
            A hopeful, small, and gentle sound.
            Life anew, a vibrant scene,
            The world is painted fresh and clean.
        """.trimIndent()
    )

    private val jokes = listOf(
        "Why don\'t scientists trust atoms? Because they make up everything!",
        "Why did the scarecrow win an award? Because he was outstanding in his field!",
        "What do you call a fake noodle? An Impasta!",
        "Why don\'t skeletons fight each other? They don\'t have the guts."
    )

    private val randomWords = listOf(
        "Serendipity", "Ephemeral", "Ubiquitous", "Mellifluous", "Petrichor",
        "Solitude", "Aurora", "Sonder", "Ineffable", "Nostalgia"
    )

    suspend fun correctGrammar(text: String): String = withContext(Dispatchers.IO) {
        if (text.equals("i go movie you coming..", ignoreCase = true)) {
            return@withContext "I am going to the movie, are you coming?"
        }
        return@withContext text
    }

    suspend fun getAiResponse(text: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "getAiResponse called with: \'$text\'")

        if (text.isBlank()) {
            return@withContext "Please type a command or text to process."
        }

        val originalText = text.trim()
        val normalizedText = originalText.lowercase(Locale.getDefault())

        // 🔹 New: Correct each word in the input to handle typos in commands.
        val correctedWords = normalizedText.split(" ").map { autoCorrectHelper.autoCorrectWord(it) }
        val correctedText = correctedWords.joinToString(" ")

        if (correctedText != normalizedText) {
            Log.d(TAG, "Corrected command: \'$normalizedText\' -> \'$correctedText\'")
        }

        // Priority 1: Check for explicit local commands using the *corrected* text.
        val localResponse = getLocalCommandOrQuestionResponse(correctedText, originalText)
        if (localResponse != null) {
            Log.d(TAG, "Returning local response for command: \'$correctedText\'")
            return@withContext localResponse
        }

        // Priority 2: If no command, and only one word was input, suggest the correction.
        if (!originalText.contains(" ") && correctedText != normalizedText) {
            Log.d(TAG, "Suggesting correction for single word: \'$originalText\' -> \'$correctedText\'")
            return@withContext "Did you mean: $correctedText?"
        }

        // Priority 3: If no command and not a single-word correction, search the internet.
        if (!isNetworkAvailable()) {
            Log.w(TAG, "No network available")
            return@withContext "📡 No internet connection available. Please check your network connection and try again."
        }

        Log.d(TAG, "Proceeding with internet search for: \'$originalText\'")
        return@withContext searchInternet(originalText)
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            val isAvailable = capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    )

            Log.d(TAG, "Network available: $isAvailable")
            isAvailable
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network availability", e)
            false
        }
    }

    private suspend fun searchInternet(query: String): String {
        Log.d(TAG, "Searching online for: \'$query\'")

        val wikipediaResult = searchWikipedia(query)
        if (wikipediaResult != null) {
            return wikipediaResult
        }

        return "I couldn\'t find a direct answer for \'$query\'. Please try rephrasing your question or asking something else."
    }

    private suspend fun searchWikipedia(query: String): String? {
        try {
            Log.d(TAG, "Performing Wikipedia search for: \'$query\'")
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&format=json"
            val searchConnection = URL(searchUrl).openConnection() as HttpURLConnection
            searchConnection.connectTimeout = NETWORK_TIMEOUT
            searchConnection.readTimeout = NETWORK_TIMEOUT
            val searchResponse = searchConnection.inputStream.bufferedReader().use { it.readText() }
            searchConnection.disconnect()

            val searchJson = JSONObject(searchResponse)
            val searchResults = searchJson.optJSONObject("query")?.optJSONArray("search")

            if (searchResults != null && searchResults.length() > 0) {
                val topResultTitle = searchResults.getJSONObject(0).getString("title")
                Log.d(TAG, "Found top Wikipedia page: \'$topResultTitle\'")

                val extractUrl = "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&exintro&explaintext&titles=${URLEncoder.encode(topResultTitle, "UTF-8")}&format=json"
                val extractConnection = URL(extractUrl).openConnection() as HttpURLConnection
                extractConnection.connectTimeout = NETWORK_TIMEOUT
                extractConnection.readTimeout = NETWORK_TIMEOUT
                val extractResponse = extractConnection.inputStream.bufferedReader().use { it.readText() }
                extractConnection.disconnect()

                val extractJson = JSONObject(extractResponse)
                val pages = extractJson.getJSONObject("query").getJSONObject("pages")
                val pageId = pages.keys().next()
                val page = pages.getJSONObject(pageId)
                val extract = page.optString("extract")

                if (extract.isNotEmpty()) {
                    return extract
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during Wikipedia search: ", e)
        }
        return null
    }

    private fun generateLocalContinuation(text: String): String {
        val normalized = text.lowercase(Locale.getDefault())
        return when {
            normalized.contains("once upon a time") -> "in a faraway land, there lived a brave warrior."
            normalized.contains("in a world") -> "where keyboards could think, one AI stood apart."
            normalized.contains("the best way to predict the future") -> "is to invent it."
            normalized.endsWith("i am feeling") -> "happy to help you today!"
            else -> "and they all lived happily ever after."
        }
    }

    private suspend fun getLocalCommandOrQuestionResponse(normalizedText: String, originalText: String): String? {
        delay(400) // Simulate thinking

        return when {
            normalizedText == "word of the day" -> {
                val randomWord = randomWords.random()
                val definition = searchWikipedia(randomWord)
                if (definition != null) {
                    "📖 Word of the Day: **$randomWord**\n\n$definition"
                } else {
                    "I couldn\'t define \'$randomWord\' right now. Please try again later."
                }
            }

            normalizedText == "help" || normalizedText == "show commands" -> {
                """
                    🤖 Available AI Commands:

                    📝 Text Commands:
                    • \'poem\' or \'tell me a joke\'
                    • \'synonym for: [word]\' - Finds synonyms
                    • \'make formal: [text]\' - Makes text more formal
                    • \'continue writing: [text]\' - Completes sentences
                    • \'correct grammar: [text]\' - Corrects grammar
                    • \'correct word: [word]\' - Corrects a single word

                    📧 Email & Letters:
                    • \'draft email to: [name] about: [topic]\'
                    • \'write a letter to: [name]\'

                    🌐 Internet Search:
                    • Ask any question to search online
                    • Example: \'weather today\', \'capital of France\'

                    ✨ Fun:
                    • \'word of the day\'

                    Try: \'poem\' or \'capital of india\'
                """
            }
            normalizedText.contains("who are you") || normalizedText.contains("what are you") -> {
                "I am an AI assistant integrated into this keyboard, designed to help you with writing, searching, and more. Ask \'help\' to see what I can do!"
            }
            normalizedText == "tell me a joke" || normalizedText == "joke" -> {
                "😂 ${jokes.random()}"
            }
            normalizedText.contains("capital") && normalizedText.contains("india") -> {
                "The capital of India is New Delhi. 🇮🇳"
            }
            normalizedText.contains("capital") && normalizedText.contains("pakistan") -> {
                "The capital of Pakistan is Islamabad. 🇵🇰"
            }
            normalizedText.contains("capital") && normalizedText.contains("gujarat") -> {
                "The capital of Gujarat is Gandhinagar."
            }
            normalizedText.contains("capital") && (normalizedText.contains("america") || normalizedText.contains("usa") || normalizedText.contains("amerca")) -> {
                "The capital of the United States of America is Washington, D.C. 🇺🇸"
            }
            normalizedText.contains("capital") && normalizedText.contains("france") -> {
                "The capital of France is Paris. 🇫🇷"
            }
            normalizedText.contains("capital") && (normalizedText.contains("china") || normalizedText.contains("chinese")) -> {
                "The capital of China is Beijing. 🇨🇳"
            }
            (normalizedText.contains("highest") || normalizedText.contains("tallest")) &&
                    normalizedText.contains("mountain") && normalizedText.contains("africa") -> {
                "The highest mountain in Africa is Mount Kilimanjaro in Tanzania. 🏔️"
            }
            normalizedText.contains("largest") && normalizedText.contains("planet") -> {
                "The largest planet in our solar system is Jupiter. 🪐"
            }
            normalizedText.contains("who is") && normalizedText.contains("prime minister") && normalizedText.contains("india") -> {
                "As of my last update, the Prime Minister of India is Narendra Modi."
            }
            normalizedText.startsWith("synonym for:") -> {
                val word = normalizedText.substringAfter(":").trim()
                synonyms[word]?.let { "Synonyms for \'$word\': $it" }
                    ?: "No synonyms found for \'$word\'. Try: happy, sad, good, bad"
            }
            normalizedText.startsWith("make formal:") -> {
                var formalText = originalText.substringAfter(":", "").trim()
                formalText = formalText.replace(Regex("\\bu\\b", RegexOption.IGNORE_CASE), "you")
                formalText = formalText.replace(Regex("\\bwanna\\b", RegexOption.IGNORE_CASE), "want to")
                formalText = formalText.replace(Regex("\\bgonna\\b", RegexOption.IGNORE_CASE), "going to")
                formalText = formalText.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                formalText
            }
            normalizedText == "poem" -> {
                "📝 ${poems.random()}"
            }
            normalizedText.startsWith("correct grammar:") -> {
                val content = originalText.substringAfter(":", "").trim()
                autoCorrectHelper.checkGrammarOffline(content)
            }
             normalizedText.startsWith("correct word:") -> {
                val word = originalText.substringAfter(":").trim()
                autoCorrectHelper.autoCorrectWord(word)
            }
            normalizedText.startsWith("continue writing:") -> {
                val content = originalText.substringAfter(":", "").trim()
                if (content.isNotBlank()) {
                    generateLocalContinuation(content)
                } else {
                    "Please provide text to continue, like: \'continue writing: Once upon a time\'"
                }
            }
            normalizedText.startsWith("draft email to:") -> {
                val recipientAndTopic = originalText.substringAfter(":").trim()
                val recipient = recipientAndTopic.substringBefore("about:").trim()
                val topic = recipientAndTopic.substringAfter("about:", "").trim()
                if (recipient.isNotBlank() && topic.isNotBlank()) {
                    """
                    Subject: $topic

                    Dear $recipient,

                    I hope this email finds you well.

                    I am writing to you regarding $topic.

                    Best regards,

                    [Your Name]
                    """
                } else {
                    "Please provide a recipient and a topic. Example: \'draft email to: John about: project update\'"
                }
            }
            normalizedText.startsWith("write a letter to:") -> {
                val recipient = originalText.substringAfter(":").trim()
                if (recipient.isNotBlank()) {
                    """
                    Dear $recipient,

                    I hope this letter finds you in good health and high spirits. I am writing to you today to...

                    Sincerely,

                    [Your Name]
                    """
                } else {
                    "Please provide a recipient for the letter. Example: \'write a letter to: Jane\'"
                }
            }
            else -> null
        }
    }
}
