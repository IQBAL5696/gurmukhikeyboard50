package com.iqbal.gurmukhikeyboard50

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.preference.PreferenceManager
import java.util.Calendar
import java.util.TimeZone

class CalendarWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        var yearOffset = prefs.getInt("widget_year_offset", 0)

        when (action) {
            ACTION_PREV_YEAR -> {
                yearOffset--
                prefs.edit().putInt("widget_year_offset", yearOffset).apply()
                refreshAllWidgets(context)
            }
            ACTION_NEXT_YEAR -> {
                yearOffset++
                prefs.edit().putInt("widget_year_offset", yearOffset).apply()
                refreshAllWidgets(context)
            }
            Intent.ACTION_TIME_TICK, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> {
                refreshAllWidgets(context)
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE, "com.iqbal.gurmukhikeyboard50.UPDATE_WIDGET" -> {
                refreshAllWidgets(context)
            }
        }
    }

    private fun refreshAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, CalendarWidget::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName)
        
        for (id in ids) {
            updateAppWidget(context, appWidgetManager, id)
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.calendarList)
    }

    companion object {
        const val ACTION_PREV_YEAR = "com.iqbal.gurmukhikeyboard50.ACTION_PREV_YEAR"
        const val ACTION_NEXT_YEAR = "com.iqbal.gurmukhikeyboard50.ACTION_NEXT_YEAR"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_two_month)
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val yearOffset = prefs.getInt("widget_year_offset", 0)

            val now = Calendar.getInstance()
            val d = now.get(Calendar.DAY_OF_MONTH)
            val m = now.get(Calendar.MONTH) + 1
            val currentYear = now.get(Calendar.YEAR)
            val targetYear = currentYear + yearOffset
            
            val nsDate = NanakshahiCalendar.getNanakshahiDate(context, d, m, targetYear)
            val jd = NanakshahiCalendar.julianDayFromFields(targetYear, m, d.toDouble())
            val loc = NanakshahiCalendar.LocationConfig.AMRITSAR
            val sr = NanakshahiCalendar.calculateSunriseJD(jd, loc.lat, loc.lon)
            val tR = NanakshahiCalendar.getTithiResultFromJD(context, sr, loc.lat, loc.lon)
            val wd = NanakshahiCalendar.getWeekdayFromJD(jd)
            val wdName = NanakshahiCalendar.weekdayNamePunjabi(wd)
            
            val gregMonths = listOf("ਜਨਵਰੀ", "ਫਰਵਰੀ", "ਮਾਰਚ", "ਅਪ੍ਰੈਲ", "ਮਈ", "ਜੂਨ", "ਜੁਲਾਈ", "ਅਗਸਤ", "ਸਤੰਬਰ", "ਅਕਤੂਬਰ", "ਨਵੰਬਰ", "ਦਸੰਬਰ")
            val gregName = gregMonths[m - 1]
            val targetYearStr = NanakshahiCalendar.toGurmukhiNumber(targetYear)

            // Header: ਵੀਰਵਾਰ, ੨੦੨੬, ੨੬ ਫਰਵਰੀ, ਫੱਗਣ ੧੫...
            val fullInfo = "$wdName, $targetYearStr, ${NanakshahiCalendar.toGurmukhiNumber(d)} $gregName, ${nsDate.month} ${NanakshahiCalendar.toGurmukhiNumber(nsDate.day)}, ${tR.paksha} ${tR.tithiDisplay}"
            views.setTextViewText(R.id.txtHeaderFullInfo, fullInfo)

            views.setOnClickPendingIntent(R.id.btnPrevYear, PendingIntent.getBroadcast(context, 0, Intent(context, CalendarWidget::class.java).apply { action = ACTION_PREV_YEAR }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.btnNextYear, PendingIntent.getBroadcast(context, 1, Intent(context, CalendarWidget::class.java).apply { action = ACTION_NEXT_YEAR }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

            val selectedTzId = prefs.getString("pref_widget_timezone", "America/Toronto") ?: "America/Toronto"
            val secondaryTz = TimeZone.getTimeZone(selectedTzId)
            
            val isLocalDay = NanakshahiCalendar.isDaytimeAtLocation(31.62, 74.87, TimeZone.getDefault())
            views.setTextViewText(R.id.widgetIconLocal, if (isLocalDay) "☀️" else "🌙")
            
            val sCoords = if (selectedTzId.contains("Toronto")) Pair(45.42, -75.69) else Pair(31.62, 74.87)
            val isSecondaryDay = NanakshahiCalendar.isDaytimeAtLocation(sCoords.first, sCoords.second, secondaryTz)
            views.setTextViewText(R.id.widgetIconSecondary, if (isSecondaryDay) "☀️" else "🌙")

            views.setString(R.id.widgetTimeOttawa, "setTimeZone", selectedTzId)

            val activityIntent = Intent(context, CalendarActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, System.currentTimeMillis().toInt(), activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.txtHeaderFullInfo, pendingIntent)
            views.setPendingIntentTemplate(R.id.calendarList, pendingIntent)

            val serviceIntent = Intent(context, WidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.calendarList, serviceIntent)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
