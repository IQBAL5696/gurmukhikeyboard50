package com.iqbal.gurmukhikeyboard50

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class CalendarActivity : AppCompatActivity() {
    private lateinit var updateHelper: UpdateHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        val isDark = CalendarThemeHelper.isDarkMode(this)
        val bgColor = if (isDark) Color.parseColor("#121212") else Color.parseColor("#FFF3E0")

        // Ensure window fits system windows and has the correct background color
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.setBackgroundDrawable(ColorDrawable(bgColor))
        
        // Update status bar icons for dark/light background
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark

        updateHelper = UpdateHelper(this)
        updateHelper.checkForUpdate()
        
        // Use a FrameLayout as container with the theme background color
        val root = FrameLayout(this)
        root.id = View.generateViewId()
        root.setBackgroundColor(bgColor)
        setContentView(root)

        // Handle Window Insets
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Set the professional title for the standalone app
        if (packageName.startsWith("com.iqbal.nanakshahi.calendar")) {
            title = "Nanakshahi Calendar 2026 Punjabi – Sikh Jantri"
        }
        
        // Use the existing NanakshahiCalendarPanel
        val panel = NanakshahiCalendarPanel(
            context = this,
            onDismiss = { finish() },
            onInsertDate = { /* No-op in Activity mode */ }
        )
        
        root.addView(panel.view)

        // Add cross-promotion if in standalone mode
        val promoContainer = panel.view.findViewById<LinearLayout>(R.id.promo_container)
        if (promoContainer != null) {
            PromotionManager.addPromotionButton(this, promoContainer)
        }
    }

    fun updateActivityTheme() {
        val isDark = CalendarThemeHelper.isDarkMode(this)
        val bgColor = if (isDark) Color.parseColor("#121212") else Color.parseColor("#FFF3E0")
        window.setBackgroundDrawable(ColorDrawable(bgColor))
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
        findViewById<View>(android.R.id.content)?.setBackgroundColor(bgColor)
    }

    override fun onResume() {
        super.onResume()
        updateActivityTheme()
        updateHelper.handleOnResume()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updateHelper.handleActivityResult(requestCode, resultCode, data)
    }
}
