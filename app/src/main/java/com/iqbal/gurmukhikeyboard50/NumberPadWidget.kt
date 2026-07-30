package com.iqbal.gurmukhikeyboard50

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.preference.PreferenceManager

class NumberPadWidget : AppWidgetProvider() {

    companion object {
        private const val ACTION_CLICK = "com.iqbal.gurmukhikeyboard50.WIDGET_CLICK"
        private const val EXTRA_VALUE = "extra_value"
        private const val PREF_PREFIX = "widget_calc_"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_number_pad)
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val currentText = prefs.getString("$PREF_PREFIX$appWidgetId", "0") ?: "0"

            views.setTextViewText(R.id.txt_display, currentText)

            // Setup click listeners for all buttons
            val buttons = mapOf(
                R.id.btn_0 to "0", R.id.btn_1 to "1", R.id.btn_2 to "2",
                R.id.btn_3 to "3", R.id.btn_4 to "4", R.id.btn_5 to "5",
                R.id.btn_6 to "6", R.id.btn_7 to "7", R.id.btn_8 to "8",
                R.id.btn_9 to "9", R.id.btn_dot to ".", R.id.btn_add to "+",
                R.id.btn_sub to "-", R.id.btn_mul to "*", R.id.btn_div to "/",
                R.id.btn_equal to "=", R.id.btn_clear to "C", R.id.btn_del to "DEL",
                R.id.btn_y to "y", R.id.btn_m to "m", R.id.btn_d to "d",
                R.id.btn_h to "h", R.id.btn_s to "s", R.id.btn_percent to "%",
                R.id.btn_space to " "
            )

            for ((id, value) in buttons) {
                val intent = Intent(context, NumberPadWidget::class.java).apply {
                    action = ACTION_CLICK
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    putExtra(EXTRA_VALUE, value)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    id + appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(id, pi)
            }

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
        if (intent.action == ACTION_CLICK) {
            val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val value = intent.getStringExtra(EXTRA_VALUE) ?: return

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                handleButtonClick(context, appWidgetId, value)
            }
        }
    }

    private fun handleButtonClick(context: Context, appWidgetId: Int, value: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = "$PREF_PREFIX$appWidgetId"
        var current = prefs.getString(key, "0") ?: "0"

        when (value) {
            "C" -> current = "0"
            "DEL" -> {
                current = if (current.length <= 1) "0" else current.dropLast(1)
            }
            "=" -> {
                val result = CalculatorHelper.evaluate(current)
                if (result != null && result.contains("=")) {
                    current = result.substringAfter("=")
                }
            }
            "%" -> {
                // Basic percentage logic if not handled by evaluate
                if (current != "0" && !current.contains("%")) {
                    current += "%"
                }
                val result = CalculatorHelper.evaluate(current)
                if (result != null && result.contains("=")) {
                    current = result.substringAfter("=")
                }
            }
            " " -> {
                if (current != "0") {
                    current += " "
                }
            }
            else -> {
                if (current == "0" && value !in "+-*/.ymdhs%") {
                    current = value
                } else {
                    current += value
                }
            }
        }

        prefs.edit().putString(key, current).apply()

        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        for (id in appWidgetIds) {
            editor.remove("$PREF_PREFIX$id")
        }
        editor.apply()
    }
}
