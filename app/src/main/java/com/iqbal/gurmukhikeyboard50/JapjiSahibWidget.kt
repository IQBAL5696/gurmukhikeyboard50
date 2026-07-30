package com.iqbal.gurmukhikeyboard50

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.preference.PreferenceManager

class JapjiSahibWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.iqbal.gurmukhikeyboard50.JAPJI_REFRESH"
        const val ACTION_PLAY_PAUSE = "com.iqbal.gurmukhikeyboard50.JAPJI_PLAY_PAUSE"
        const val ACTION_AUTO_ADVANCE = "com.iqbal.gurmukhikeyboard50.JAPJI_AUTO_ADVANCE"
        const val ACTION_TO_TOP = "com.iqbal.gurmukhikeyboard50.JAPJI_TO_TOP"
        const val ACTION_FONT_INC = "com.iqbal.gurmukhikeyboard50.JAPJI_FONT_INC"
        const val ACTION_FONT_DEC = "com.iqbal.gurmukhikeyboard50.JAPJI_FONT_DEC"
        const val ACTION_LARIVAAR = "com.iqbal.gurmukhikeyboard50.JAPJI_LARIVAAR"
        const val ACTION_TOUCH_DOWN = "com.iqbal.gurmukhikeyboard50.JAPJI_TOUCH_DOWN"
        const val ACTION_TOUCH_UP = "com.iqbal.gurmukhikeyboard50.JAPJI_TOUCH_UP"
        
        private const val PREF_SCROLL_ACTIVE = "japji_scroll_active"
        private const val PREF_SCROLL_INDEX = "japji_scroll_index"
        private const val PREF_FONT_SIZE = "japji_widget_font_size"
        private const val PREF_LARIVAAR = "japji_larivaar"
        private const val PREF_IS_TOUCHED = "japji_is_touched"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_japji_sahib)
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val isScrolling = prefs.getBoolean(PREF_SCROLL_ACTIVE, false)
            val currentIndex = prefs.getInt(PREF_SCROLL_INDEX, 0)
            val fontSize = prefs.getFloat(PREF_FONT_SIZE, 18f)
            val isLarivaar = prefs.getBoolean(PREF_LARIVAAR, false)

            // Set Data Adapter
            val serviceIntent = Intent(context, JapjiWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(this.toUri(Intent.URI_INTENT_SCHEME) + "?font=$fontSize&larivaar=$isLarivaar")
            }
            views.setRemoteAdapter(R.id.japji_list_view, serviceIntent)
            views.setEmptyView(R.id.japji_list_view, R.id.japji_empty_view)
            
            // Set scroll position
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setScrollPosition(R.id.japji_list_view, currentIndex)
            } else {
                views.setInt(R.id.japji_list_view, "setSelection", currentIndex)
            }

            // Larivaar Button Style
            views.setInt(R.id.btn_larivaar_japji, "setBackgroundColor", 
                if (isLarivaar) Color.parseColor("#4CAF50") else Color.parseColor("#EF6C00"))

            // Play/Pause Icon
            views.setImageViewResource(R.id.btn_play_pause_japji, 
                if (isScrolling) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)

            // Template for item clicks
            val touchIntent = Intent(context, JapjiSahibWidget::class.java).apply { action = ACTION_TOUCH_DOWN }
            val touchPI = PendingIntent.getBroadcast(context, 701, touchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setPendingIntentTemplate(R.id.japji_list_view, touchPI)

            // Other Buttons
            val toTopPI = PendingIntent.getBroadcast(context, 401, Intent(context, JapjiSahibWidget::class.java).apply { action = ACTION_TO_TOP }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.btn_to_top_japji, toTopPI)

            val playPausePI = PendingIntent.getBroadcast(context, 101, Intent(context, JapjiSahibWidget::class.java).apply { action = ACTION_PLAY_PAUSE }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.btn_play_pause_japji, playPausePI)

            val fontIncPI = PendingIntent.getBroadcast(context, 501, Intent(context, JapjiSahibWidget::class.java).apply { action = ACTION_FONT_INC }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.btn_font_inc_japji, fontIncPI)

            val fontDecPI = PendingIntent.getBroadcast(context, 502, Intent(context, JapjiSahibWidget::class.java).apply { action = ACTION_FONT_DEC }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.btn_font_dec_japji, fontDecPI)

            val refreshPI = PendingIntent.getBroadcast(context, 201, Intent(context, JapjiSahibWidget::class.java).apply { action = ACTION_REFRESH }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.btn_refresh_japji, refreshPI)
            
            val larivaarPI = PendingIntent.getBroadcast(context, 601, Intent(context, JapjiSahibWidget::class.java).apply { action = ACTION_LARIVAAR }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.btn_larivaar_japji, larivaarPI)
            
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        
        when (intent.action) {
            ACTION_TOUCH_DOWN -> {
                val isScrolling = prefs.getBoolean(PREF_SCROLL_ACTIVE, false)
                if (isScrolling) {
                    prefs.edit().putBoolean(PREF_SCROLL_ACTIVE, false).apply()
                    cancelAdvance(context)
                } else {
                    prefs.edit().putBoolean(PREF_SCROLL_ACTIVE, true).apply()
                    scheduleNextAdvance(context)
                }
                refreshWidgets(context)
            }
            ACTION_LARIVAAR -> {
                val current = prefs.getBoolean(PREF_LARIVAAR, false)
                prefs.edit().putBoolean(PREF_LARIVAAR, !current).apply()
                refreshWidgets(context)
            }
            ACTION_TO_TOP -> {
                prefs.edit().putInt(PREF_SCROLL_INDEX, 0).putBoolean(PREF_SCROLL_ACTIVE, false).apply()
                cancelAdvance(context)
                refreshWidgets(context)
            }
            ACTION_FONT_INC -> {
                val current = prefs.getFloat(PREF_FONT_SIZE, 18f)
                prefs.edit().putFloat(PREF_FONT_SIZE, (current + 2).coerceAtMost(40f)).apply()
                refreshWidgets(context)
            }
            ACTION_FONT_DEC -> {
                val current = prefs.getFloat(PREF_FONT_SIZE, 18f)
                prefs.edit().putFloat(PREF_FONT_SIZE, (current - 2).coerceAtLeast(12f)).apply()
                refreshWidgets(context)
            }
            ACTION_REFRESH -> {
                refreshWidgets(context)
            }
            ACTION_PLAY_PAUSE -> {
                val isScrolling = !prefs.getBoolean(PREF_SCROLL_ACTIVE, false)
                prefs.edit().putBoolean(PREF_SCROLL_ACTIVE, isScrolling).apply()
                if (isScrolling) scheduleNextAdvance(context) else cancelAdvance(context)
                refreshWidgets(context)
            }
            ACTION_AUTO_ADVANCE -> {
                if (prefs.getBoolean(PREF_SCROLL_ACTIVE, false)) {
                    val nextIndex = prefs.getInt(PREF_SCROLL_INDEX, 0) + 1
                    prefs.edit().putInt(PREF_SCROLL_INDEX, nextIndex).apply()
                    refreshWidgets(context)
                    scheduleNextAdvance(context)
                }
            }
        }
    }

    private fun scheduleNextAdvance(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, JapjiSahibWidget::class.java).apply { action = ACTION_AUTO_ADVANCE }
        val pi = PendingIntent.getBroadcast(context, 301, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val triggerTime = SystemClock.elapsedRealtime() + 5000
        
        // Changed to inexact alarm to avoid USE_EXACT_ALARM permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pi)
        else am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime, pi)
    }

    private fun cancelAdvance(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, JapjiSahibWidget::class.java).apply { action = ACTION_AUTO_ADVANCE }
        val pi = PendingIntent.getBroadcast(context, 301, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
    }

    private fun refreshWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, JapjiSahibWidget::class.java))
        for (id in ids) updateAppWidget(context, appWidgetManager, id)
        appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.japji_list_view)
    }
}
