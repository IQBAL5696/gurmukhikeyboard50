package com.iqbal.gurmukhikeyboard50

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NitnemActivity : AppCompatActivity() {
    private lateinit var nitnemEngine: NitnemEngine
    private lateinit var updateHelper: UpdateHelper

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
        
        // Ensure the entire app window respects system bars (starts below status bar)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        
        // Set window background and status bar style based on Nitnem theme
        val isDarkMode = getSharedPreferences("NitnemPrefs", MODE_PRIVATE).getBoolean("is_dark_mode", false)
        val bgColor = if (isDarkMode) Color.BLACK else Color.parseColor("#FFF3E0")
        window.setBackgroundDrawable(ColorDrawable(bgColor))
        
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDarkMode
        insetsController.isAppearanceLightNavigationBars = !isDarkMode

        updateHelper = UpdateHelper(this)
        updateHelper.checkForUpdate()

        // Load the Nitnem panel layout
        val nitnemView = LayoutInflater.from(this).inflate(R.layout.nitnem_panel_layout, null)
        
        // Wrap nitnemView in a FrameLayout to handle system bar insets correctly.
        // This ensures the background frame of the main menu is pushed above the navigation bar.
        val wrapper = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            addView(nitnemView)
        }
        setContentView(wrapper)

        ViewCompat.setOnApplyWindowInsetsListener(wrapper) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Only set bottom and side padding to push the framed menu up.
            // Keep top padding at 0 so the top frame touches the edge.
            v.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }

        // Initialize the dedicated Nitnem Engine for standalone use
        // The engine now handles background coloring based on saved theme (Dark/Light)
        nitnemEngine = NitnemEngine(this) { finish() }
        nitnemEngine.setupNitnemPanel(nitnemView)

        // Set the professional title for the standalone app
        if (packageName.startsWith("com.iqbal.nitnem.punjabi")) {
            title = "Nitnem Gutka Sahib Punjabi: Audio, Text, Daily Banis"
        }

        // Add promotion for the keyboard app in the standalone Nitnem app
        val promoContainer = nitnemView.findViewById<LinearLayout>(R.id.promo_container)
        if (promoContainer != null) {
            PromotionManager.addPromotionButton(this, promoContainer)
        }

        if (intent?.getBooleanExtra("open_pdf_books", false) == true) {
            nitnemView.post {
                nitnemEngine.showBooksDialog()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop any background audio when activity is destroyed
        if (::nitnemEngine.isInitialized) {
            nitnemEngine.stopMusic()
        }
    }

    override fun onResume() {
        super.onResume()
        updateHelper.handleOnResume()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updateHelper.handleActivityResult(requestCode, resultCode, data)
    }
}
