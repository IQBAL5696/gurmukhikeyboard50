package com.iqbal.gurmukhikeyboard50

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_RECORD_AUDIO = 200
        private const val REQUEST_NOTIFICATION_PERMISSION = 201
    }

    private lateinit var tvStatus: TextView
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable Edge-to-Edge for Android 15 compatibility
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Handle Window Insets to avoid UI overlap with system bars
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvStatus = findViewById(R.id.tvStatus)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        // Enable Keyboard ਬਟਨ
        findViewById<Button>(R.id.btnEnableKeyboard).setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }

        // Select Keyboard ਬਟਨ
        findViewById<Button>(R.id.btnSelectKeyboard).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        // ਨਿੱਤਨੇਮ ਸ਼ਾਰਟਕੱਟ
        findViewById<Button>(R.id.btnAddNitnemShortcut).setOnClickListener {
            addNitnemShortcut()
        }

        // ਜੰਤਰੀ ਸ਼ਾਰਟਕੱਟ
        findViewById<Button>(R.id.btnAddCalendarShortcut).setOnClickListener {
            addCalendarShortcut()
        }

        // ਕੈਲਕੁਲੇਟਰ ਸ਼ਾਰਟਕੱਟ ਜੋੜੋ
        findViewById<Button>(R.id.btnOpenCalculator).setOnClickListener {
            addCalculatorShortcut()
        }

        // ਪ੍ਰਾਈਵੇਸੀ ਪਾਲਿਸੀ
        findViewById<Button>(R.id.btnPrivacyPolicy).setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        // ਐਪ ਸ਼ੇਅਰ ਕਰੋ
        findViewById<Button>(R.id.btnShareApp).setOnClickListener {
            shareApp()
        }

        // ਸੈਟਿੰਗਾਂ (Vibrate, Sound, Popup)
        setupToggleListener(findViewById(R.id.switch_vibrate), "vibrate_on_keypress")
        setupToggleListener(findViewById(R.id.switch_sound), "sound_on_keypress")
        setupToggleListener(findViewById(R.id.switch_popup), "popup_on_keypress")

        checkNotificationPermission()

        // Handle Back Button Confirmation
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmationDialog()
            }
        })
    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit App")
            .setMessage("ਕੀ ਤੁਸੀਂ ਐਪ ਬੰਦ ਕਰਨਾ ਚਾਹੁੰਦੇ ਹੋ?")
            .setPositiveButton("ਹਾਂ (Yes)") { _, _ ->
                finish()
            }
            .setNegativeButton("ਨਹੀਂ (No)", null)
            .show()
    }

    private fun addNitnemShortcut() {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val shortcutIntent = Intent(this, NitnemActivity::class.java).apply { action = Intent.ACTION_VIEW }
            val pinShortcutInfo = ShortcutInfoCompat.Builder(this, "nitnem_home_shortcut")
                .setShortLabel("Nitnem")
                .setIcon(IconCompat.createWithResource(this, R.drawable.ik_onkar_svg))
                .setIntent(shortcutIntent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(this, pinShortcutInfo, null)
        }
    }

    private fun addCalendarShortcut() {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val shortcutIntent = Intent(this, CalendarActivity::class.java).apply { action = Intent.ACTION_VIEW }
            val pinShortcutInfo = ShortcutInfoCompat.Builder(this, "calendar_home_shortcut")
                .setShortLabel("Calendar")
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_calender))
                .setIntent(shortcutIntent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(this, pinShortcutInfo, null)
        }
    }

    private fun addCalculatorShortcut() {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val shortcutIntent = Intent(this, CalculatorActivity::class.java).apply { action = Intent.ACTION_VIEW }
            val pinShortcutInfo = ShortcutInfoCompat.Builder(this, "calculator_home_shortcut")
                .setShortLabel("Calculator")
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_calculator))
                .setIntent(shortcutIntent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(this, pinShortcutInfo, null)
        }
    }

    private fun setupToggleListener(switch: SwitchMaterial, prefKey: String) {
        switch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(prefKey, isChecked).apply()
            // ਕੀਬੋਰਡ ਨੂੰ ਦੱਸਣਾ ਕਿ ਸੈਟਿੰਗ ਬਦਲ ਗਈ ਹੈ
            sendBroadcast(Intent(ImeConstants.ACTION_SETTINGS_CHANGED))
        }
    }

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check out this Gurmukhi Keyboard app: https://play.google.com/store/apps/details?id=$packageName")
        }
        startActivity(Intent.createChooser(shareIntent, "Share App via"))
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATION_PERMISSION)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        ReviewHelper.showReviewIfNeeded(this)
    }

    private fun updateStatus() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val isEnabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val currentIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val isSelected = currentIme?.contains(packageName) == true

        val status = StringBuilder()
        status.append("ਕੀਬੋਰਡ ਚਾਲੂ ਹੈ: ${if(isEnabled) "ਹਾਂ" else "ਨਹੀਂ"}\n")
        status.append("ਕੀਬੋਰਡ ਚੁਣਿਆ ਹੋਇਆ ਹੈ: ${if(isSelected) "ਹਾਂ" else "ਨਹੀਂ"}")
        
        tvStatus.text = status.toString()
    }
}
