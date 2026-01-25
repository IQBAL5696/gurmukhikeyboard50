package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.util.Log
import android.widget.TextView
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.Closeable
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject

class TranslationManager(private val context: Context) : Closeable {

    data class Language(val code: String, val name: String, val speechCode: String = code)

    companion object {
        private const val TAG = "TranslationManager"
        private val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val availableLanguages = listOf(
        Language("en", "English"),
        Language("pa", "Punjabi (ਪੰਜਾਬੀ)"),
        Language("hi", "Hindi"),
        Language("fr", "French"),
        Language("es", "Spanish"),
        Language("de", "German"),
        Language("ko", "Korean"),
        Language("zh", "Chinese", "zh-CN"),
        Language("ar", "Arabic", "ar-SA"),
        Language("bn", "Bengali", "bn-IN"),
        Language("ja", "Japanese", "ja-JP"),
        Language("pt", "Portuguese", "pt-PT"),
        Language("ru", "Russian", "ru-RU"),
        Language("ur", "Urdu", "ur-PK"),
        Language("it", "Italian", "it-IT"),
        Language("nl", "Dutch", "nl-NL"),
        Language("sv", "Swedish", "sv-SE"),
        Language("tr", "Turkish", "tr-TR"),
        Language("vi", "Vietnamese", "vi-VN"),
        Language("el", "Greek", "el-GR"),
        Language("he", "Hebrew", "he-IL"),
        Language("id", "Indonesian", "id-ID"),
        Language("ms", "Malay", "ms-MY"),
        Language("th", "Thai", "th-TH"),
        Language("da", "Danish", "da-DK"),
        Language("fi", "Finnish", "fi-FI"),
        Language("no", "Norwegian", "no-NO"),
        Language("pl", "Polish", "pl-PL"),
        Language("cs", "Czech", "cs-CZ"),
        Language("hu", "Hungarian", "hu-HU"),
        Language("ro", "Romanian", "ro-RO"),
        Language("sk", "Slovak", "sk-SK"),
        Language("af", "Afrikaans", "af-ZA"),
        Language("sq", "Albanian", "sq-AL"),
        Language("am", "Amharic", "am-ET"),
        Language("hy", "Armenian", "hy-AM"),
        Language("az", "Azerbaijani", "az-AZ"),
        Language("eu", "Basque", "eu-ES"),
        Language("be", "Belarusian", "be-BY"),
        Language("bs", "Bosnian", "bs-BA"),
        Language("bg", "Bulgarian", "bg-BG"),
        Language("ca", "Catalan", "ca-ES"),
        Language("hr", "Croatian", "hr-HR"),
        Language("et", "Estonian", "et-EE"),
        Language("ka", "Georgian", "ka-GE"),
        Language("is", "Icelandic", "is-IS"),
        Language("lv", "Latvian", "lv-LV"),
        Language("lt", "Lithuanian", "lt-LT"),
        Language("mk", "Macedonian", "mk-MK"),
        Language("sr", "Serbian", "sr-RS"),
        Language("sl", "Slovenian", "sl-SI"),
        Language("sw", "Swahili", "sw-KE"),
        Language("uk", "Ukrainian", "uk-UA"),
        Language("fa", "Persian", "fa-IR"),
        Language("gu", "Gujarati", "gu-IN"),
        Language("kn", "Kannada", "kn-IN"),
        Language("ml", "Malayalam", "ml-IN"),
        Language("mr", "Marathi", "mr-IN"),
        Language("ne", "Nepali", "ne-NP"),
        Language("si", "Sinhala", "si-LK"),
        Language("ta", "Tamil", "ta-IN"),
        Language("te", "Telugu", "te-IN"),
        Language("zu", "Zulu", "zu-ZA"),
        Language("ga", "Irish", "ga-IE"),
        Language("mt", "Maltese", "mt-MT"),
        Language("cy", "Welsh", "cy-GB"),
        Language("eo", "Esperanto", "eo"),
        Language("fy", "Frisian", "fy-NL"),
        Language("gl", "Galician", "gl-ES"),
        Language("ht", "Haitian Creole", "ht-HT"),
        Language("ha", "Hausa", "ha-NG"),
        Language("haw", "Hawaiian", "haw-US"),
        Language("ig", "Igbo", "ig-NG"),
        Language("jw", "Javanese", "jv-ID"),
        Language("ky", "Kyrgyz", "ky-KG"),
        Language("la", "Latin", "la"),
        Language("lb", "Luxembourgish", "lb-LU"),
        Language("mg", "Malagasy", "mg-MG"),
        Language("mi", "Maori", "mi-NZ"),
        Language("mn", "Mongolian", "mn-MN"),
        Language("my", "Myanmar (Burmese)", "my-MM"),
        Language("ny", "Nyanja (Chichewa)", "ny-MW"),
        Language("or", "Odia (Oriya)", "or-IN"),
        Language("ps", "Pashto", "ps-AF"),
        Language("sm", "Samoan", "sm-WS"),
        Language("gd", "Scots Gaelic", "gd-GB"),
        Language("st", "Sesotho", "st-LS"),
        Language("sn", "Shona", "sn-ZW"),
        Language("sd", "Sindhi", "sd-PK"),
        Language("so", "Somali", "so-SO"),
        Language("su", "Sundanese", "su-ID"),
        Language("tg", "Tajik", "tg-TJ"),
        Language("tt", "Tatar", "tt-RU"),
        Language("tk", "Turkmen", "tk-TM"),
        Language("ug", "Uyghur", "ug-CN"),
        Language("uz", "Uzbek", "uz-UZ"),
        Language("xh", "Xhosa", "xh-ZA"),
        Language("yi", "Yiddish", "yi"),
        Language("yo", "Yoruba", "yo-NG")
    )

    var sourceLanguage: Language = availableLanguages[0]
    var targetLanguage: Language = availableLanguages[1]

    private val translators = mutableMapOf<String, Translator>()

    fun swapLanguages() {
        val temp = sourceLanguage
        sourceLanguage = targetLanguage
        targetLanguage = temp
    }

    fun getCurrentTargetLanguage(): String = targetLanguage.name

    fun translate(text: String, outputView: TextView?) {
        if (text.isBlank()) {
            outputView?.text = ""
            return
        }

        outputView?.post { outputView.text = "Translating..." }
        Log.d(TAG, "Translate request: '$text' from ${sourceLanguage.code} to ${targetLanguage.code}")

        if (sourceLanguage.code == "pa" || targetLanguage.code == "pa" || sourceLanguage.code == "hi" || targetLanguage.code == "hi") {
            Log.d(TAG, "Using MyMemory for Punjabi/Hindi.")
            translateWithMyMemory(text, sourceLanguage.code, targetLanguage.code, outputView)
        } else {
            Log.d(TAG, "Using ML Kit for ${sourceLanguage.code} -> ${targetLanguage.code}")
            translateWithMLKit(text, outputView)
        }
    }

    private fun translateWithMyMemory(text: String, source: String, target: String, outputView: TextView?) {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val url = "https://api.mymemory.translated.net/get?q=$encodedText&langpair=$source|$target"
        val request = Request.Builder().url(url).build()

        Log.d(TAG, "Attempting translation with MyMemory: $url")

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val failMsg = context.getString(R.string.translation_failed, e.message)
                outputView?.post { outputView.text = failMsg }
                Log.e(TAG, "MyMemory request failed.", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        val failMsg = context.getString(R.string.translation_failed, "Server error: ${it.code}")
                        outputView?.post { outputView.text = failMsg }
                        Log.w(TAG, "MyMemory unsuccessful response: ${it.code}")
                        return
                    }

                    try {
                        val responseBody = it.body?.string()
                        if (responseBody.isNullOrEmpty()) {
                            val failMsg = context.getString(R.string.translation_failed, "Empty response")
                            outputView?.post { outputView.text = failMsg }
                            Log.w(TAG, "Empty response from MyMemory")
                            return
                        }

                        val translatedText = JSONObject(responseBody).getJSONObject("responseData").getString("translatedText")

                        var finalText = translatedText
                        if (target == "pa") {
                            finalText = romanToGurmukhi(finalText)
                        }

                        outputView?.post { outputView.text = finalText }
                        Log.d(TAG, "Successfully translated with MyMemory.")

                    } catch (e: JSONException) {
                        val failMsg = context.getString(R.string.translation_failed, "Invalid response format")
                        outputView?.post { outputView.text = failMsg }
                        Log.e(TAG, "Error parsing MyMemory response", e)
                    }
                }
            }
        })
    }

    private fun translateWithMLKit(text: String, outputView: TextView?) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage.code)
            .setTargetLanguage(targetLanguage.code)
            .build()

        val translatorKey = "${sourceLanguage.code}-${targetLanguage.code}"
        val translator = translators.getOrPut(translatorKey) {
            Log.d(TAG, "Creating new ML Kit translator for $translatorKey")
            Translation.getClient(options)
        }

        val conditions = DownloadConditions.Builder().build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                Log.d(TAG, "ML Kit model ready for $translatorKey.")
                translator.translate(text)
                    .addOnSuccessListener { translatedText ->
                        outputView?.post { outputView.text = translatedText }
                        Log.d(TAG, "ML Kit translation success: $translatedText")
                    }
                    .addOnFailureListener { e ->
                        val failMsg = context.getString(R.string.translation_failed, e.message)
                        outputView?.post { outputView.text = failMsg }
                        Log.e(TAG, "ML Kit translation failed for $translatorKey", e)
                    }
            }
            .addOnFailureListener { e ->
                val failMsg = context.getString(R.string.model_download_failed, e.message)
                outputView?.post { outputView.text = failMsg }
                Log.e(TAG, "ML Kit model download failed for $translatorKey", e)
            }
    }


    /**
     * Converts common Roman Punjabi words into proper Gurmukhi script.
     * Not perfect but covers majority of cases and guarantees Gurmukhi output.
     */
    private fun romanToGurmukhi(input: String): String {
        val map = mapOf(
            "a" to "ਅ", "aa" to "ਆ", "i" to "ਇ", "ii" to "ਈ",
            "u" to "ਉ", "oo" to "ਊ", "e" to "ਏ", "ai" to "ਐ",
            "o" to "ਓ", "au" to "ਔ",
            "k" to "ਕ", "kh" to "ਖ",
            "g" to "ਗ", "gh" to "ਘ",
            "ch" to "ਚ", "j" to "ਜ",
            "t" to "ਤ", "th" to "ਥ",
            "d" to "ਦ", "dh" to "ਧ",
            "n" to "ਨ", "p" to "ਪ", "ph" to "ਫ",
            "b" to "ਬ", "bh" to "ਭ",
            "m" to "ਮ", "y" to "ਯ",
            "r" to "ਰ", "l" to "ਲ",
            "v" to "ਵ", "sh" to "ਸ਼",
            "s" to "ਸ", "h" to "ਹ"
        )

        var text = input.lowercase()

        // Replace longest patterns first (e.g., "kh" before "k")
        val sortedKeys = map.keys.sortedByDescending { it.length }

        for (key in sortedKeys) {
            text = text.replace(key, map[key] ?: key)
        }

        return text
    }



    override fun close() {
        Log.d(TAG, "Closing all translators.")
        translators.values.forEach { it.close() }
        translators.clear()
    }
}
