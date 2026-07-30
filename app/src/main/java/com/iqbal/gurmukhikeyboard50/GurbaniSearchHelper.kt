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

    fun getGurbaniSpannable(lines: List<String>, isLarivaar: Boolean, folderName: String, dynamicColor: DynamicColor? = null, highlightColor: DynamicColor? = null, customTypeface: Typeface? = null, pageNumberColor: Int = Color.parseColor("#0D47A1")): Pair<SpannableStringBuilder, Map<String, Int>> {
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

            val start = builder.length

            // Transform text efficiently
            var lineText = if (isLarivaar) originalLine.replace(whiteSpaceRegex, ZWSP) else originalLine
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

            // Detect verse end using pre-compiled regex
            val hasNumber = verseNumberRegex.containsMatchIn(processedLine)

            if (i < lines.size - 1 && !lines[i+1].startsWith("PAGENO:")) {
                builder.append(separator)
            }
            val end = builder.length

            // Apply Coloring based on current highlight state
            if (shouldHighlight) {
                val highlightSpan = if (highlightColor != null) SharedColorSpan(highlightColor) else ForegroundColorSpan(royalBlue)
                builder.setSpan(highlightSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (dynamicColor != null) {
                builder.setSpan(SharedColorSpan(dynamicColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            // Apply Vishram coloring
            for (vIdx in vishramIndices) {
                val absIdx = start + vIdx
                if (absIdx < builder.length) {
                    builder.setSpan(ForegroundColorSpan(vishramColor), absIdx, absIdx + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
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
        override fun updateDrawState(ds: TextPaint) { ds.typeface = typeface }
        override fun updateMeasureState(paint: TextPaint) { paint.typeface = typeface }
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
        } catch (e: Exception) { Log.e("GurbaniSearch", "Search failed: ${e.message}") }
        return results.distinctBy { it.shabadId }
    }

    fun fetchFullShabad(shabadId: String): ShabadResponse {
        val lines = mutableListOf<String>()
        var prevId: String? = null
        var nextId: String? = null
        try {
            val url = "https://api.gurbaninow.com/v2/shabad/$shabadId"
            val request = Request.Builder().url(url).header("User-Agent", "GurmukhiKeyboard/1.0").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ShabadResponse(emptyList(), null, null)
                val json = JSONObject(response.body?.string() ?: "")
                
                // Extract navigation info
                if (json.has("shabadinfo")) {
                    val info = json.getJSONObject("shabadinfo")
                    if (info.has("navigation")) {
                        val nav = info.getJSONObject("navigation")
                        prevId = nav.optJSONObject("previous")?.optString("id")
                        nextId = nav.optJSONObject("next")?.optString("id")
                    }
                }

                if (json.has("shabad")) {
                    val shabadArray = json.getJSONArray("shabad")
                    for (i in 0 until shabadArray.length()) {
                        val lineEntry = shabadArray.getJSONObject(i)
                        if (lineEntry.has("line")) {
                            val lineObj = lineEntry.getJSONObject("line")
                            if (lineObj.has("gurmukhi")) {
                                val gurmukhi = lineObj.getJSONObject("gurmukhi")
                                lines.add(gurmukhi.getString("unicode"))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GurbaniSearch", "Error fetching full shabad: ${e.message}")
        }
        return ShabadResponse(lines, prevId, nextId)
    }

    data class SearchItem(val text: String, val shabadId: String)
    data class ShabadResponse(val lines: List<String>, val prevId: String?, val nextId: String?)
}
