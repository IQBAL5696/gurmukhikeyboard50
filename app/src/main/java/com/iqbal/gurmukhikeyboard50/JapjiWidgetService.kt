package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.preference.PreferenceManager

class JapjiWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return JapjiRemoteViewsFactory(this.applicationContext)
    }
}

class JapjiRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var lines: List<String> = listOf()
    private var fontSize: Float = 18f
    private var isLarivaar: Boolean = false

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        fontSize = prefs.getFloat("japji_widget_font_size", 18f)
        isLarivaar = prefs.getBoolean("japji_larivaar", false)
        
        lines = try {
            val rawParagraphs = GurbaniSearchHelper.getGurbaniLines(context, "japji_sahib")
            GurbaniSearchHelper.splitIntoSentences(rawParagraphs)
        } catch (e: Exception) {
            listOf("ਪਾਠ ਲੋਡ ਨਹੀਂ ਹੋ ਸਕਿਆ।")
        }
        
        if (isLarivaar) {
            lines = lines.map { it.replace(" ", "") }
        }
    }

    override fun onDestroy() {
        lines = listOf()
    }

    override fun getCount(): Int = lines.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.item_japji_line)
        if (position < lines.size) {
            val currentLine = lines[position]
            val spannable = SpannableString(currentLine)
            views.setTextViewTextSize(R.id.txt_line, TypedValue.COMPLEX_UNIT_SP, fontSize)

            var shouldColor = false
            if (position == 0) shouldColor = true
            if (position > 0) {
                val prevLine = lines[position - 1].trim()
                if (prevLine.contains(Regex("[੦-੯]+\\s*॥$"))) {
                    shouldColor = true
                }
            }
            
            if (shouldColor) {
                spannable.setSpan(ForegroundColorSpan(Color.parseColor("#4CAF50")), 0, currentLine.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(StyleSpan(Typeface.BOLD), 0, currentLine.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                spannable.setSpan(ForegroundColorSpan(Color.parseColor("#212121")), 0, currentLine.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            
            views.setTextViewText(R.id.txt_line, spannable)
            
            // Fill-in intent to handle touch/click for pausing
            val fillInIntent = Intent()
            views.setOnClickFillInIntent(R.id.txt_line, fillInIntent)
        }
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
