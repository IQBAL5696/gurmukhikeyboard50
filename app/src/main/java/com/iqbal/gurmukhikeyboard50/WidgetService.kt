package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.preference.PreferenceManager
import java.util.Calendar

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return CalendarRemoteViewsFactory(this.applicationContext)
    }
}

class CalendarRemoteViewsFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private val items = mutableListOf<WidgetRow>()

    data class WidgetRow(
        val type: Int, // 0: Title, 1: WeekHeader, 2: Days
        val text: String? = null,
        val days: List<NanakshahiCalendar.MonthlyDayCell>? = null
    )

    override fun onCreate() {}

    override fun onDataSetChanged() {
        items.clear()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val yearOffset = prefs.getInt("widget_year_offset", 0)
        
        val now = Calendar.getInstance(NanakshahiCalendar.currentTimeZone)
        now.add(Calendar.YEAR, yearOffset)
        
        // Loop for 12 months of the selected year
        for (mIdx in 0 until 12) {
            addMonthToItems(now.get(Calendar.YEAR), mIdx + 1)
        }
    }

    private fun addMonthToItems(year: Int, month: Int) {
        val nsDate = NanakshahiCalendar.getNanakshahiDate(context, 1, month, year)
        val gregMonths = listOf("ਜਨਵਰੀ", "ਫਰਵਰੀ", "ਮਾਰਚ", "ਅਪ੍ਰੈਲ", "ਮਈ", "ਜੂਨ", "ਜੁਲਾਈ", "ਅਗਸਤ", "ਸਤੰਬਰ", "ਅਕਤੂਬਰ", "ਨਵੰਬਰ", "ਦਸੰਬਰ")
        val gregName = gregMonths[month - 1]
        
        // 1. Title Row
        items.add(WidgetRow(0, "${nsDate.month} ${NanakshahiCalendar.toGurmukhiNanakshahiYear(nsDate.year)} ($gregName)"))
        
        // 2. Week Header Row
        items.add(WidgetRow(1))
        
        // 3. Day Rows (Filter only weeks that have dates of THIS month)
        val allDays = NanakshahiCalendar.generateMonthlyCalendar(context, month, year, NanakshahiCalendar.LocationConfig.AMRITSAR)
        allDays.chunked(7).forEach { weekDays ->
            if (weekDays.any { it.isCurrentMonth }) {
                items.add(WidgetRow(2, days = weekDays))
            }
        }
    }

    override fun onDestroy() { items.clear() }
    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= items.size) return RemoteViews(context.packageName, R.layout.widget_calendar_week_item)
        
        val item = items[position]
        return when (item.type) {
            0 -> {
                val views = RemoteViews(context.packageName, R.layout.widget_month_title_row)
                views.setTextViewText(R.id.titleText, item.text)
                views
            }
            1 -> {
                RemoteViews(context.packageName, R.layout.widget_week_header_row)
            }
            else -> {
                val views = RemoteViews(context.packageName, R.layout.widget_calendar_week_item)
                item.days?.forEachIndexed { index, day ->
                    val viewId = context.resources.getIdentifier("day${index + 1}", "id", context.packageName)
                    if (day.day != null) {
                        var dayDisplay = NanakshahiCalendar.toGurmukhiNumber(day.day)
                        
                        // Append Indicators
                        val icons = StringBuilder()
                        if (day.isSangrand) icons.append("🌾")
                        if (day.isPunia) icons.append("🌕")
                        if (day.isMasaya) icons.append("🌑")
                        if (day.gurpurabName != null) icons.append("🚩")
                        
                        if (icons.isNotEmpty()) {
                            dayDisplay += "\n$icons"
                        }

                        views.setTextViewText(viewId, dayDisplay)
                        
                        // Colors
                        if (day.isToday) {
                            views.setTextColor(viewId, Color.RED)
                        } else if (!day.isCurrentMonth) {
                            views.setTextColor(viewId, Color.parseColor("#CCCCCC"))
                        } else {
                            views.setTextColor(viewId, Color.BLACK)
                        }
                    } else {
                        views.setTextViewText(viewId, "")
                    }
                }
                
                // Add fill-in intent for corners/items click template
                val fillInIntent = Intent()
                views.setOnClickFillInIntent(R.id.day1, fillInIntent) // Just to trigger the template
                
                views
            }
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 3
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
}
