package com.iqbal.gurmukhikeyboard50

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import androidx.preference.PreferenceManager

object ReviewHelper {

    private const val PREF_USAGE_COUNT = "pref_usage_count"
    private const val PREF_REVIEW_SHOWN = "pref_review_shown"

    // 🔥 Better threshold (20–30 best for keyboards)
    private const val USAGE_THRESHOLD = 25

    fun incrementUsage(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean(PREF_REVIEW_SHOWN, false)) return

        val count = prefs.getInt(PREF_USAGE_COUNT, 0) + 1
        prefs.edit().putInt(PREF_USAGE_COUNT, count).apply()
    }

    fun showReviewIfNeeded(activity: Activity) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)

        if (prefs.getBoolean(PREF_REVIEW_SHOWN, false)) return

        val count = prefs.getInt(PREF_USAGE_COUNT, 0)

        if (count >= USAGE_THRESHOLD) {

            val manager = ReviewManagerFactory.create(activity)
            val request = manager.requestReviewFlow()

            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {

                    val reviewInfo = task.result
                    val flow = manager.launchReviewFlow(activity, reviewInfo)

                    flow.addOnCompleteListener {
                        prefs.edit().putBoolean(PREF_REVIEW_SHOWN, true).apply()
                    }

                } else {
                    Log.e("ReviewHelper", "Review API failed")

                    // 🔥 FALLBACK → Open Play Store
                    openPlayStore(activity)
                }
            }
        }
    }

    // 🔥 Fallback method
    private fun openPlayStore(context: Context) {
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=com.iqbal.gurmukhikeyboard50")
            )
            intent.setPackage("com.android.vending")
            context.startActivity(intent)
        } catch (e: Exception) {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.iqbal.gurmukhikeyboard50")
                )
            )
        }
    }
}
