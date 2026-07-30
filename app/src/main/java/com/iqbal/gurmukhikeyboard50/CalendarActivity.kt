package com.iqbal.gurmukhikeyboard50

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class CalendarActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        // Use a FrameLayout as container
        val root = FrameLayout(this)
        root.id = View.generateViewId()
        setContentView(root)

        // Handle Window Insets
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Set the professional title for the standalone app
        if (packageName == "com.iqbal.nanakshahi.calendar") {
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
}
