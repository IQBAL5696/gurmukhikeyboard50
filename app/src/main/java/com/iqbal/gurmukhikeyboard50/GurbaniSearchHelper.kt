package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.MetricAffectingSpan
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.text.style.StyleSpan
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import java.io.BufferedReader
import java.io.InputStreamReader

object GurbaniSearchHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val ZWSP = "\u200B" // Zero Width Space for Larivaar wrapping

    // Pre-compiled regex for performance
    private val onlyNumberRegex = Regex("^[੦-੯0-9]+$")
    private val spacesAroundNumberRegex = Regex("\\s{2,}[੦-੯0-9]+\\s{2,}")
    private val findNumberRegex = Regex("([੦-੯0-9]+)")
    private val ikOnkarWrapRegex = Regex("ੴ[$ZWSP\\s]+")
    private val whiteSpaceRegex = Regex("\\s+")
    private val verseNumberRegex = Regex("॥[^॥]*[੦-੯0-9][^॥]*॥")

    class DynamicColor(var color: Int)

    class SharedColorSpan(val dynamicColor: DynamicColor) : CharacterStyle(), UpdateAppearance {
        override fun updateDrawState(tp: TextPaint) {
            tp.color = dynamicColor.color
        }
    }

    fun getGurbaniLines(context: Context, folderName: String): List<String> {
        val lines = mutableListOf<String>()
        try {
            val path = "gurbani/$folderName/content.txt"
            context.assets.open(path).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val originalLine = line!!
                        val trimmed = originalLine.trim()
                        if (trimmed.isNotEmpty()) {
                            if (trimmed.matches(onlyNumberRegex) || originalLine.contains(spacesAroundNumberRegex)) {
                                val numMatch = findNumberRegex.find(trimmed)
                                if (numMatch != null) {
                                    val pageNum = toGurmukhi(numMatch.groupValues[1])
                                    lines.add("PAGENO:$pageNum")
                                    continue
                                }
                            }
                            lines.add(trimmed)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GurbaniSearch", "Error reading local lines: ${e.message}")
        }
        return lines
    }

    /**
     * Optimized sentence splitting for long Gurbani texts.
     * Fixed to group dandas properly for headers like ॥ ਜਪੁ ॥.
     */
    fun splitIntoSentences(paragraphs: List<String>): List<String> {
        val processed = ArrayList<String>(paragraphs.size * 2)
        for (para in paragraphs) {
            if (para.startsWith("PAGENO:")) {
                processed.add(para)
                continue
            }

            var start = 0
            var i = 0
            while (i < para.length) {
                if (para[i] == '॥') {
                    // Optimization: Only split if there is content before this danda.
                    // This prevents breaking headers like "॥ ਜਪੁ ॥" into multiple parts.
                    val contentBefore = para.substring(start, i).any { !it.isWhitespace() }

                    if (contentBefore) {
                        // Peek ahead to group verse numbers like "॥ ੧ ॥" with the sentence
                        var end = i
                        var j = i + 1
                        var foundSecondDanda = false
                        while (j < para.length) {
                            val c = para[j]
                            if (c.isWhitespace() || c in '੦'..'੯' || c in '0'..'9') {
                                j++
                            } else if (c == '॥') {
                                end = j
                                j++
                                foundSecondDanda = true
                            } else {
                                break
                            }
                        }

                        val sentence = para.substring(start, end + 1).trim()
                        if (sentence.isNotEmpty()) processed.add(sentence)

                        start = if (foundSecondDanda) j else i + 1
                        i = start - 1
                    }
                }
                i++
            }
            if (start < para.length) {
                val last = para.substring(start).trim()
                if (last.isNotEmpty()) processed.add(last)
            }
        }
        return processed
    }

    fun getGurbaniSpannable(lines: List<String>, isLarivaar: Boolean, folderName: String, showArth: Boolean = true, showEnglish: Boolean = false, dynamicColor: DynamicColor? = null, highlightColor: DynamicColor? = null, customTypeface: Typeface? = null, pageNumberColor: Int = Color.parseColor("#0D47A1")): Pair<SpannableStringBuilder, Map<String, Int>> {
        val builder = SpannableStringBuilder()
        val separator = if (isLarivaar) ZWSP else "\n"
        val pageOffsets = mutableMapOf<String, Int>()
        val vishramColor = Color.parseColor("#3E2723")
        var shouldHighlight = true
        val royalBlue = Color.parseColor("#0D47A1") // Default Highlight

        for (i in lines.indices) {
            val originalLine = lines[i]
            if (originalLine.startsWith("PAGENO:")) {
                val pageNum = originalLine.substring(7)
                if (builder.isNotEmpty() && builder.last() != '\n') builder.append("\n")
                val pageStart = builder.length
                builder.append("\n --- ਅੰਗ ").append(pageNum).append(" --- \n")
                pageOffsets[pageNum] = pageStart

                builder.setSpan(ForegroundColorSpan(pageNumberColor), pageStart, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), pageStart, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                continue
            }

            val lineParts = originalLine.split("\n")
            val mainVerse = lineParts[0]

            val start = builder.length

            // Transform text efficiently
            var lineText = if (isLarivaar) mainVerse.replace(whiteSpaceRegex, ZWSP) else mainVerse
            if (lineText.indexOf('ੴ') != -1) {
                lineText = lineText.replace(ikOnkarWrapRegex, "ੴ")
            }

            // Identify Vishrams (commas to spaces)
            val processedLine = StringBuilder(lineText.length)
            val vishramIndices = mutableListOf<Int>()
            for (char in lineText) {
                if (char == ',') {
                    vishramIndices.add(processedLine.length)
                    processedLine.append(' ')
                } else {
                    processedLine.append(char)
                }
            }

            builder.append(processedLine)
            val verseEnd = builder.length

            // Apply Coloring to main verse based on current highlight state
            if (shouldHighlight) {
                val highlightSpan = if (highlightColor != null) SharedColorSpan(highlightColor) else ForegroundColorSpan(royalBlue)
                builder.setSpan(highlightSpan, start, verseEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (dynamicColor != null) {
                builder.setSpan(SharedColorSpan(dynamicColor), start, verseEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            // Apply Vishram coloring
            for (vIdx in vishramIndices) {
                val absIdx = start + vIdx
                if (absIdx < builder.length) {
                    builder.setSpan(ForegroundColorSpan(vishramColor), absIdx, absIdx + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            // Append Arths / Translations separately with distinct colors
            for (pIdx in 1 until lineParts.size) {
                val part = lineParts[pIdx].trim()
                if (part.isEmpty()) continue

                if (part.startsWith("ਅਰਥ:")) {
                    if (!showArth) continue
                    builder.append("\n")
                    val arthStart = builder.length
                    builder.append(part)
                    val arthEnd = builder.length

                    val arthColor = Color.parseColor("#E65100") // Kesari Orange
                    builder.setSpan(ForegroundColorSpan(arthColor), arthStart, arthEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    val headerEnd = minOf(arthStart + 5, arthEnd)
                    builder.setSpan(StyleSpan(Typeface.BOLD), arthStart, headerEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else if (part.startsWith("Translation:")) {
                    if (!showEnglish) continue
                    builder.append("\n")
                    val arthStart = builder.length
                    builder.append(part)
                    val arthEnd = builder.length

                    val enColor = Color.parseColor("#1565C0") // Soft Blue
                    builder.setSpan(ForegroundColorSpan(enColor), arthStart, arthEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    builder.setSpan(StyleSpan(Typeface.ITALIC), arthStart, arthEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            // Detect verse end using pre-compiled regex
            val hasNumber = verseNumberRegex.containsMatchIn(processedLine)

            if (i < lines.size - 1 && !lines[i+1].startsWith("PAGENO:")) {
                builder.append(separator)
            }

            // Toggle highlight state only when a verse number is encountered.
            if (hasNumber) {
                shouldHighlight = !shouldHighlight
            }
        }

        if (customTypeface != null) {
            builder.setSpan(CustomTypefaceSpan(customTypeface), 0, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return Pair(builder, pageOffsets)
    }

    fun toGurmukhi(s: String): String {
        val gurmukhiDigits = charArrayOf('੦', '੧', '੨', '੩', '੪', '੫', '੬', '੭', '੮', '੯')
        return s.map { if (it in '0'..'9') gurmukhiDigits[it - '0'] else it }.joinToString("")
    }

    class CustomTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(ds: TextPaint) {
            ds.typeface = Typeface.create(typeface, Typeface.NORMAL)
            ds.isFakeBoldText = false
        }
        override fun updateMeasureState(paint: TextPaint) {
            paint.typeface = Typeface.create(typeface, Typeface.NORMAL)
            paint.isFakeBoldText = false
        }
    }

    fun searchGurbani(query: String): List<SearchItem> {
        if (query.isBlank()) return emptyList()
        val cleanedQuery = query.replace(" ", "")
        val results = mutableListOf<SearchItem>()
        try {
            val encodedQuery = URLEncoder.encode(cleanedQuery, "UTF-8")
            val url = "https://api.gurbaninow.com/v2/search/$encodedQuery?searchtype=1"
            val request = Request.Builder().url(url).header("User-Agent", "GurmukhiKeyboard/1.0").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val json = JSONObject(response.body?.string() ?: "")
                if (json.has("shabads")) {
                    val shabads = json.getJSONArray("shabads")
                    for (i in 0 until minOf(shabads.length(), 40)) {
                        val shabadEntry = shabads.getJSONObject(i)

                        val shabadObj = if (shabadEntry.has("shabad")) shabadEntry.getJSONObject("shabad") else null
                        val shabadId = shabadObj?.optString("shabadid") ?: ""

                        val gurmukhi = shabadObj?.optJSONObject("gurmukhi")
                        val lineText = gurmukhi?.optString("unicode") ?: ""

                        if (lineText.isNotEmpty() && shabadId.isNotEmpty()) {
                            results.add(SearchItem(lineText, shabadId))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("GurbaniSearch", "Search failed: ${e.message}")
        }
        return results.distinctBy { it.shabadId }
    }

    private fun extractTranslationString(obj: JSONObject?): String {
        if (obj == null) return ""
        val unicode = obj.optString("unicode", "")
        if (unicode.isNotBlank()) return unicode
        val trans = obj.optString("translation", "")
        if (trans.isNotBlank()) return trans
        val gurbani = obj.optString("gurbani", "")
        if (gurbani.isNotBlank()) return gurbani
        return ""
    }

    fun fetchFullShabad(shabadId: String): ShabadResponse {
        val lines = mutableListOf<String>()
        var prevId: String? = null
        var nextId: String? = null
        try {
            val gurbaninowUrl = "https://api.gurbaninow.com/v2/shabad/$shabadId"
            val baniDbUrl = "https://api.banidb.com/v2/shabads/$shabadId"

            var success = false
            for (url in listOf(gurbaninowUrl, baniDbUrl)) {
                if (success) break
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "GurmukhiKeyboard/1.0")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use
                        val responseBody = response.body?.string() ?: ""
                        if (responseBody.isBlank()) return@use

                        Log.d("GurbaniAPI", "GurbaniAPI BODY: $responseBody")

                        val json = JSONObject(responseBody)

                        // Extract navigation info (prev/next Shabad ID)
                        if (json.has("shabadinfo")) {
                            val info = json.getJSONObject("shabadinfo")
                            if (info.has("navigation")) {
                                val nav = info.getJSONObject("navigation")
                                if (prevId == null) prevId = nav.optJSONObject("previous")?.optString("id")
                                if (nextId == null) nextId = nav.optJSONObject("next")?.optString("id")
                            }
                        }

                        val shabadArray = when {
                            json.has("shabad") -> json.getJSONArray("shabad")
                            json.has("verses") -> json.getJSONArray("verses")
                            else -> null
                        }

                        if (shabadArray != null && shabadArray.length() > 0) {
                            for (i in 0 until shabadArray.length()) {
                                val lineEntry = shabadArray.getJSONObject(i)
                                val lineObj = when {
                                    lineEntry.has("line") -> lineEntry.getJSONObject("line")
                                    lineEntry.has("verse") -> lineEntry.getJSONObject("verse")
                                    else -> lineEntry
                                }

                                // 1. Extract Gurmukhi Verse Line
                                val verseUnicode = when {
                                    lineObj.has("gurmukhi") -> {
                                        val gObj = lineObj.optJSONObject("gurmukhi")
                                        gObj?.optString("unicode")?.ifBlank { gObj.optString("akhar") } ?: lineObj.optString("gurmukhi")
                                    }
                                    lineObj.has("verse") -> {
                                        val vObj = lineObj.optJSONObject("verse")
                                        vObj?.optString("unicode")?.ifBlank { vObj.optString("akhar") } ?: lineObj.optString("verse")
                                    }
                                    else -> lineObj.optString("unicode", "")
                                }

                                if (verseUnicode.isBlank()) continue

                                // 2. Extract Punjabi Arth & English Translation
                                var punjabiArth = ""
                                var englishTrans = ""

                                if (lineObj.has("translation")) {
                                    val transObj = lineObj.optJSONObject("translation")
                                    if (transObj != null) {
                                        // Punjabi Arth parsing (matches exact JSON: translation -> punjabi -> default -> unicode)
                                        if (transObj.has("punjabi")) {
                                            val puVal = transObj.opt("punjabi")
                                            if (puVal is JSONObject) {
                                                val defVal = puVal.opt("default")
                                                if (defVal is JSONObject) {
                                                    punjabiArth = defVal.optString("unicode").ifBlank { defVal.optString("akhar") }
                                                } else if (defVal is String) {
                                                    punjabiArth = defVal
                                                } else {
                                                    punjabiArth = puVal.optString("unicode").ifBlank { puVal.optString("akhar") }
                                                }
                                            } else if (puVal is String) {
                                                punjabiArth = puVal
                                            }
                                        }
                                        if (punjabiArth.isBlank() && transObj.has("pu")) {
                                            val puObj = transObj.optJSONObject("pu")
                                            if (puObj != null) {
                                                punjabiArth = extractTranslationString(puObj.optJSONObject("ss"))
                                                if (punjabiArth.isBlank()) punjabiArth = extractTranslationString(puObj.optJSONObject("ft"))
                                                if (punjabiArth.isBlank()) punjabiArth = extractTranslationString(puObj.optJSONObject("bdb"))
                                                if (punjabiArth.isBlank()) punjabiArth = extractTranslationString(puObj.optJSONObject("ms"))
                                                if (punjabiArth.isBlank()) punjabiArth = puObj.optString("unicode", "")
                                            } else {
                                                punjabiArth = transObj.optString("pu", "")
                                            }
                                        }

                                        // English Translation parsing (matches exact JSON: translation -> english -> default)
                                        if (transObj.has("english")) {
                                            val enVal = transObj.opt("english")
                                            if (enVal is JSONObject) {
                                                val defVal = enVal.opt("default")
                                                if (defVal is JSONObject) {
                                                    englishTrans = defVal.optString("unicode").ifBlank { defVal.optString("akhar") }
                                                } else if (defVal is String) {
                                                    englishTrans = defVal
                                                } else {
                                                    englishTrans = enVal.optString("unicode").ifBlank { enVal.optString("akhar") }
                                                }
                                            } else if (enVal is String) {
                                                englishTrans = enVal
                                            }
                                        }
                                        if (englishTrans.isBlank() && transObj.has("en")) {
                                            val enObj = transObj.optJSONObject("en")
                                            if (enObj != null) {
                                                englishTrans = extractTranslationString(enObj.optJSONObject("prd"))
                                                if (englishTrans.isBlank()) englishTrans = extractTranslationString(enObj.optJSONObject("ssk"))
                                                if (englishTrans.isBlank()) englishTrans = extractTranslationString(enObj.optJSONObject("bms"))
                                                if (englishTrans.isBlank()) englishTrans = enObj.optString("unicode", "")
                                            } else {
                                                englishTrans = transObj.optString("en", "")
                                            }
                                        }
                                    }
                                } else if (lineObj.has("translations")) {
                                    val transVal = lineObj.get("translations")
                                    if (transVal is JSONObject) {
                                        punjabiArth = transVal.optString("pu", "")
                                        englishTrans = transVal.optString("en", "")
                                    } else if (transVal is org.json.JSONArray) {
                                        for (k in 0 until transVal.length()) {
                                            val tObj = transVal.optJSONObject(k) ?: continue
                                            val lang = tObj.optString("language", tObj.optString("lang"))
                                            val textVal = tObj.optString("translation", tObj.optString("unicode"))
                                            val sourceId = tObj.optString("sourceId", tObj.optString("source"))

                                            if ((lang == "pu" || sourceId in listOf("ss", "ft", "bdb", "ms")) && punjabiArth.isBlank()) {
                                                punjabiArth = textVal
                                            } else if ((lang == "en" || sourceId in listOf("prd", "ssk", "bms")) && englishTrans.isBlank()) {
                                                englishTrans = textVal
                                            }
                                        }
                                    }
                                }

                                // 3. Build Formatted Line with Gurmukhi Verse + Optional Arths
                                val sb = StringBuilder(verseUnicode)
                                if (punjabiArth.isNotBlank()) {
                                    sb.append("\nਅਰਥ: ").append(punjabiArth)
                                }
                                if (englishTrans.isNotBlank()) {
                                    sb.append("\nTranslation: ").append(englishTrans)
                                }
                                lines.add(sb.toString())
                            }
                            if (lines.isNotEmpty()) success = true
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("GurbaniSearch", "Failed fetching from $url: ${e.message}")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("GurbaniSearch", "Error fetching full shabad: ${e.message}")
        }
        return ShabadResponse(lines, prevId, nextId)
    }

    data class SearchItem(val text: String, val shabadId: String)
    data class ShabadResponse(val lines: List<String>, val prevId: String?, val nextId: String?)
}

object GurbaniMeaningHelper {
    private var fullVerseMeaningsMap: Map<String, String>? = null
    private var wordMeaningsMap: Map<String, String>? = null

    fun loadFullVerseMeanings(context: Context): Map<String, String> {
        if (fullVerseMeaningsMap != null) return fullVerseMeaningsMap!!
        val map = mutableMapOf<String, String>()
        try {
            context.assets.open("daily_educational_verses.json").use { input ->
                val jsonStr = input.bufferedReader(Charsets.UTF_8).readText()
                val jsonArr = org.json.JSONArray(jsonStr)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val v = obj.optString("verse").trim()
                    val m = obj.optString("meaning").trim()
                    if (v.isNotEmpty() && m.isNotEmpty()) {
                        map[v] = m
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GurbaniMeaning", "Error loading daily_educational_verses.json", e)
        }
        fullVerseMeaningsMap = map
        return map
    }

    fun loadWordMeanings(context: Context): Map<String, String> {
        if (wordMeaningsMap != null) return wordMeaningsMap!!
        val map = mutableMapOf<String, String>()
        try {
            context.assets.open("gurbani_meanings.json").use { input ->
                val jsonStr = input.bufferedReader(Charsets.UTF_8).readText()
                val jsonObj = JSONObject(jsonStr)
                val keys = jsonObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = jsonObj.getString(k)
                }
            }
        } catch (e: Exception) {
            Log.e("GurbaniMeaning", "Error loading gurbani_meanings.json", e)
        }
        wordMeaningsMap = map
        return map
    }

    fun getLineMeanings(context: Context, verseLine: String): String {
        if (verseLine.startsWith("PAGENO:") || verseLine.contains("\nਅਰਥ:")) return ""
        val cleanVerse = verseLine.trim()

        // 1. Priority 1: Full Sentence Punjabi Line Arth from educational verses database
        val fullMap = loadFullVerseMeanings(context)
        val directMeaning = fullMap[cleanVerse] ?: fullMap.entries.firstOrNull { cleanVerse.contains(it.key) || it.key.contains(cleanVerse) }?.value
        if (!directMeaning.isNullOrBlank()) {
            return "\nਅਰਥ: " + directMeaning
        }

        // 2. Priority 2: Key word meanings from dictionary
        val meanings = loadWordMeanings(context)
        val words = cleanVerse.split(Regex("\\s+"))
            .map { it.trim('॥', '।', ',', '.', ';', ':', '(', ')', '[', ']', '{', '}', '—', '-') }
            .filter { it.isNotEmpty() }

        val found = mutableListOf<String>()
        for (w in words) {
            val m = meanings[w]
            if (m != null && !found.any { it.startsWith("$w:") }) {
                found.add("$w: $m")
            }
        }
        return if (found.isNotEmpty()) "\nਅਰਥ: " + found.joinToString(" | ") else ""
    }
}
