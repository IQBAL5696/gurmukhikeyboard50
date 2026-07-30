package com.iqbal.gurmukhikeyboard50

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NitnemActivity : AppCompatActivity() {
    private lateinit var nitnemEngine: NitnemEngine

    private val pickPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            lifecycleScope.launch {
                val fileName = "imported_${System.currentTimeMillis()}.pdf"
                val path = withContext(Dispatchers.IO) {
                    PdfManager.importPdf(this@NitnemActivity, it, fileName)
                }
                if (path != null) {
                    val book = BookEntity(name = "ਮੇਰੀ ਪੁਸਤਕ (${System.currentTimeMillis()})", path = path, fileName = fileName, isCustom = true)
                    AppDatabase.getDatabase(this@NitnemActivity).bookDao().insertBook(book)
                    Toast.makeText(this@NitnemActivity, "ਪੁਸਤਕ ਸਫਲਤਾਪੂਰਵਕ ਜੋੜੀ ਗਈ", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@NitnemActivity, "ਪੁਸਤਕ ਜੋੜਨ ਵਿੱਚ ਫੇਲ ਹੋਇਆ", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun openFilePicker() {
        try {
            pickPdfLauncher.launch("application/pdf")
        } catch (e: Exception) {
            Toast.makeText(this, "File picker error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable Edge-to-Edge
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Load the Nitnem panel layout
        val nitnemView = LayoutInflater.from(this).inflate(R.layout.nitnem_panel_layout, null)
        setContentView(nitnemView)

        // Handle Window Insets - Remove top padding to allow frame to reach the edge
        ViewCompat.setOnApplyWindowInsetsListener(nitnemView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize the dedicated Nitnem Engine for standalone use
        // The engine now handles background coloring based on saved theme (Dark/Light)
        nitnemEngine = NitnemEngine(this) { finish() }
        nitnemEngine.setupNitnemPanel(nitnemView)

        // Set the professional title for the standalone app
        if (packageName == "com.iqbal.nitnem.punjabi") {
            title = "Nitnem Gutka Sahib Punjabi: Audio, Text, Daily Banis"
        }

        // Add promotion for the keyboard app in the standalone Nitnem app
        val promoContainer = nitnemView.findViewById<LinearLayout>(R.id.promo_container)
        if (promoContainer != null) {
            PromotionManager.addPromotionButton(this, promoContainer)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop any background audio when activity is destroyed
        if (::nitnemEngine.isInitialized) {
            nitnemEngine.stopMusic()
        }
    }
}
