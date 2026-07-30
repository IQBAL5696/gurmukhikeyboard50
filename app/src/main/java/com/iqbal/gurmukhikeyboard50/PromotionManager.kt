package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

object PromotionManager {

    private const val KEYBOARD_PKG = "com.iqbal.gurmukhikeyboard50"
    private const val NITNEM_PKG = "com.iqbal.nitnem.punjabi"
    private const val CALENDAR_PKG = "com.iqbal.nanakshahi.calendar"

    fun addPromotionButton(context: Context, parentLayout: LinearLayout) {
        val currentPkg = context.packageName

        // Determine which app to promote based on current package
        val (promoText, promoPkg) = when (currentPkg) {
            NITNEM_PKG -> "Download Punjabi Keyboard" to KEYBOARD_PKG
            CALENDAR_PKG -> "Download Nitnem Gutka" to NITNEM_PKG
            KEYBOARD_PKG -> return // Already the full app
            else -> return
        }

        if (!isAppInstalled(context, promoPkg)) {
            val btn = Button(context).apply {
                text = promoText
                textSize = 12f
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.rounded_button_bg)
                setTextColor(android.graphics.Color.WHITE)
                setOnClickListener {
                    openPlayStore(context, promoPkg)
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(16, 16, 16, 16)
                }
            }
            parentLayout.addView(btn)
        }
    }

    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun openPlayStore(context: Context, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
