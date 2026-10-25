package com.iqbal.gurmukhikeyboard50

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_RECORD_AUDIO = 200
        private const val REQUEST_NOTIFICATION_PERMISSION = 201
    }

    private lateinit var tvStatus: TextView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var updateHelper: UpdateHelper

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
        updateHelper = UpdateHelper(this)
        updateHelper.checkForUpdate()

        // Enable Keyboard ਬਟਨ
        findViewById<android.view.View>(R.id.btnEnableKeyboard).setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }

        // Select Keyboard ਬਟਨ
        findViewById<android.view.View>(R.id.btnSelectKeyboard).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        // ਨਿੱਤਨੇਮ ਸ਼ਾਰਟਕੱਟ (Nitnem)
        findViewById<View>(R.id.btnAddNitnemShortcut).setOnClickListener {
            addNitnemShortcut()
        }

        // ਜੰਤਰੀ ਸ਼ਾਰਟਕੱਟ (Calendar)
        findViewById<View>(R.id.btnAddCalendarShortcut).setOnClickListener {
            addCalendarShortcut()
        }

        // ਕੈਲਕੁਲੇਟਰ ਸ਼ਾਰਟਕੱਟ (Calculator)
        findViewById<View>(R.id.btnOpenCalculator).setOnClickListener {
            addCalculatorShortcut()
        }

        // ਇਮੇਜ ਤੋਂ ਟੈਕਸਟ ਸ਼ਾਰਟਕੱਟ (Image to Text)
        findViewById<View>(R.id.btnOpenImageToText)?.setOnClickListener {
            addImageToTextShortcut()
        }

        // ਟਰਾਂਸਲੇਟਰ ਮੈਨੇਜਰ ਸ਼ਾਰਟਕੱਟ (Translator Manager - All Languages)
        findViewById<View>(R.id.btnOpenTranslatorManager)?.setOnClickListener {
            addTranslatorManagerShortcut()
        }

        // ਪੀ.ਡੀ.ਐੱਫ. ਰੀਡਰ ਅਤੇ ਪੁਸਤਕਾਂ ਸ਼ਾਰਟਕੱਟ (PDF Reader & Books)
        findViewById<View>(R.id.btnOpenPdfReader)?.setOnClickListener {
            addPdfReaderShortcut()
        }

        // ਐਕਸਲ ਫਾਈਲ ਓਪਨਰ ਸ਼ਾਰਟਕੱਟ (Excel Viewer)
        findViewById<View>(R.id.btnOpenExcelViewer)?.setOnClickListener {
            addExcelViewerShortcut()
        }

        // ਲਾਈਵ ਦਰਬਾਰ ਸਾਹਿਬ ਸ਼ਾਰਟਕੱਟ (Live Darbar Sahib)
        findViewById<View>(R.id.btnOpenLiveDarbarSahib)?.setOnClickListener {
            addLiveDarbarSahibShortcut()
        }

        // ਪ੍ਰਾਈਵੇਸੀ ਪਾਲਿਸੀ
        findViewById<android.view.View>(R.id.btnPrivacyPolicy).setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        // ਐਪ ਸ਼ੇਅਰ ਕਰੋ
        findViewById<android.view.View>(R.id.btnShareApp).setOnClickListener {
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

    private fun getShortcutIcon(resId: Int): IconCompat {
        return try {
            val drawable = ContextCompat.getDrawable(this, resId)
            if (drawable != null) {
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(108),
                    drawable.intrinsicHeight.coerceAtLeast(108),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                IconCompat.createWithBitmap(bitmap)
            } else {
                IconCompat.createWithResource(this, resId)
            }
        } catch (e: Exception) {
            IconCompat.createWithResource(this, resId)
        }
    }

    private fun addNitnemShortcut() {
        val shortcutIntent = Intent(this, NitnemActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val pinShortcutInfo = ShortcutInfoCompat.Builder(this, "nitnem_home_shortcut")
                .setShortLabel("ਨਿਤਨੇਮ")
                .setLongLabel("ਨਿਤਨੇਮ ਗੁਟਕਾ ਸਾਹਿਬ")
                .setIcon(getShortcutIcon(R.drawable.ik_onkar_svg))
                .setIntent(shortcutIntent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(this, pinShortcutInfo, null)
        }

        sendLegacyShortcutBroadcast("ਨਿਤਨੇਮ", R.drawable.ik_onkar_svg, shortcutIntent)
        showShortcutAddedDialog("ਨਿਤਨੇਮ", shortcutIntent)
    }

    private fun addCalendarShortcut() {
        val shortcutIntent = Intent(this, CalendarActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val pinShortcutInfo = ShortcutInfoCompat.Builder(this, "calendar_home_shortcut")
                .setShortLabel("ਜੰਤਰੀ")
                .setLongLabel("ਨਾਨਕਸ਼ਾਹੀ ਜੰਤਰੀ")
                .setIcon(getShortcutIcon(R.drawable.ic_calender))
                .setIntent(shortcutIntent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(this, pinShortcutInfo, null)
        }

        sendLegacyShortcutBroadcast("ਜੰਤਰੀ", R.drawable.ic_calender, shortcutIntent)
        showShortcutAddedDialog("ਜੰਤਰੀ (Calendar)", shortcutIntent)
    }

    private fun addCalculatorShortcut() {
        val shortcutIntent = Intent(this, CalculatorActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val pinShortcutInfo = ShortcutInfoCompat.Builder(this, "calculator_home_shortcut")
                .setShortLabel("ਕੈਲਕੁਲੇਟਰ")
                .setLongLabel("ਪੰਜਾਬੀ ਕੈਲਕੁਲੇਟਰ")
                .setIcon(getShortcutIcon(R.drawable.ic_calculator))
                .setIntent(shortcutIntent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(this, pinShortcutInfo, null)
        }

        sendLegacyShortcutBroadcast("ਕੈਲਕੁਲੇਟਰ", R.drawable.ic_calculator, shortcutIntent)
        showShortcutAddedDialog("ਕੈਲਕੁਲੇਟਰ", shortcutIntent)
    }

    private fun addImageToTextShortcut() {
        val shortcutIntent = Intent(this, ImageToTextActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val pinShortcutInfo = ShortcutInfoCompat.Builder(this, "image_to_text_home_shortcut")
                .setShortLabel("ਇਮੇਜ ਤੋਂ ਟੈਕਸਟ")
                .setLongLabel("ਇਮੇਜ ਤੋਂ ਟੈਕਸਟ (Image to Text)")
                .setIcon(getShortcutIcon(R.drawable.ic_camera))
                .setIntent(shortcutIntent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(this, pinShortcutInfo, null)
        }

        sendLegacyShortcutBroadcast("ਇਮੇਜ ਤੋਂ ਟੈਕਸਟ", R.drawable.ic_camera, shortcutIntent)
        showShortcutAddedDialog("ਇਮੇਜ ਤੋਂ ਟੈਕਸਟ (Image to Text)", shortcutIntent)
    }

    private fun addTranslatorManagerShortcut() {
        val shortcutIntent = Intent(this, TranslatorActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val pinShortcutInfo = ShortcutInfoCompat.Builder(this, "translator_manager_home_shortcut")
                .setShortLabel("ਅਨੁਵਾਦਕ")
                .setLongLabel("ਅਨੁਵਾਦਕ ਮੈਨੇਜਰ (All Languages)")
                .setIcon(getShortcutIcon(R.drawable.ic_translate))
                .setIntent(shortcutIntent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(this, pinShortcutInfo, null)
        }

        sendLegacyShortcutBroadcast("ਅਨੁਵਾਦਕ ਮੈਨੇਜਰ", R.drawable.ic_translate, shortcutIntent)
        showShortcutAddedDialog("ਅਨੁਵਾਦਕ ਮੈਨੇਜਰ (Translator Manager)", shortcutIntent)
    }

    private fun addLiveDarbarSahibShortcut() {
        val shortcutIntent = Intent(this, LiveDarbarSahibActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val pinShortcutInfo = ShortcutInfoCompat.Builder(this, "live_darbar_sahib_home_shortcut")
                .setShortLabel("ਲਾਈਵ ਦਰਬਾਰ ਸਾਹਿਬ")
                .setLongLabel("ਸ੍ਰੀ ਹਰਿਮੰਦਰ ਸਾਹਿਬ ਲਾਈਵ ਕੀਰਤਨ")
                .setIcon(getShortcutIcon(R.drawable.ik_onkar_svg))
                .setIntent(shortcutIntent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(this, pinShortcutInfo, null)
        }

        sendLegacyShortcutBroadcast("ਲਾਈਵ ਦਰਬਾਰ ਸਾਹਿਬ", R.drawable.ik_onkar_svg, shortcutIntent)
        showShortcutAddedDialog("ਲਾਈਵ ਦਰਬਾਰ ਸਾਹਿਬ (Live Darbar Sahib)", shortcutIntent)
    }

    private fun addPdfReaderShortcut() {
        val shortcutIntent = Intent(this, NitnemActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("open_pdf_books", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val pinShortcutInfo = ShortcutInfoCompat.Builder(this, "pdf_reader_home_shortcut")
                .setShortLabel("ਪੀ.ਡੀ.ਐੱਫ. ਰੀਡਰ")
                .setLongLabel("ਪੀ.ਡੀ.ਐੱਫ. ਰੀਡਰ ਅਤੇ ਪੁਸਤਕਾਂ")
                .setIcon(getShortcutIcon(R.drawable.ic_library))
                .setIntent(shortcutIntent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(this, pinShortcutInfo, null)
        }

        sendLegacyShortcutBroadcast("ਪੀ.ਡੀ.ਐੱਫ. ਰੀਡਰ", R.drawable.ic_library, shortcutIntent)
        showShortcutAddedDialog("ਪੀ.ਡੀ.ਐੱਫ. ਰੀਡਰ (PDF Reader & Books)", shortcutIntent)
    }

    private fun addExcelViewerShortcut() {
        val shortcutIntent = Intent(this, ExcelViewerActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val pinShortcutInfo = ShortcutInfoCompat.Builder(this, "excel_viewer_home_shortcut")
                .setShortLabel("ਐਕਸਲ ਓਪਨਰ")
                .setLongLabel("ਐਕਸਲ ਫਾਈਲ ਓਪਨਰ (Excel Viewer)")
                .setIcon(getShortcutIcon(R.drawable.ic_library))
                .setIntent(shortcutIntent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(this, pinShortcutInfo, null)
        }

        sendLegacyShortcutBroadcast("ਐਕਸਲ ਓਪਨਰ", R.drawable.ic_library, shortcutIntent)
        showShortcutAddedDialog("ਐਕਸਲ ਫਾਈਲ ਓਪਨਰ (Excel Viewer)", shortcutIntent)
    }

    private fun sendLegacyShortcutBroadcast(name: String, iconRes: Int, targetIntent: Intent) {
        try {
            val addIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, targetIntent)
                putExtra(Intent.EXTRA_SHORTCUT_NAME, name)
                putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(this@MainActivity, iconRes))
                putExtra("duplicate", false)
            }
            sendBroadcast(addIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showShortcutAddedDialog(featureName: String, openIntent: Intent) {
        AlertDialog.Builder(this)
            .setTitle("$featureName ਸ਼ਾਰਟਕੱਟ")
            .setMessage("ਸ਼ਾਰਟਕੱਟ ਫੋਨ ਦੀ ਹੋਮ ਸਕ੍ਰੀਨ 'ਤੇ ਜੋੜਨ ਦੀ ਬੇਨਤੀ ਭੇਜੀ ਗਈ ਹੈ।\n\nਜੇਕਰ ਹੋਮ ਸਕ੍ਰੀਨ 'ਤੇ ਆਈਕਨ ਨਾ ਬਣੇ, ਤਾਂ ਫੋਨ ਸੈਟਿੰਗਜ਼ (App Permissions) ਵਿੱਚ 'Add Home Screen Shortcuts' ਇਨੇਬਲ ਕਰੋ।")
            .setPositiveButton("ਠੀਕ ਹੈ (OK)", null)
            .setNeutralButton("ਖੋਲ੍ਹੋ (Open Now)") { _, _ ->
                startActivity(openIntent)
            }
            .show()
    }

    private fun setupToggleListener(switch: SwitchCompat, prefKey: String) {
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
        updateHelper.handleOnResume()
        updateStatus()
        ReviewHelper.showReviewIfNeeded(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updateHelper.handleActivityResult(requestCode, resultCode, data)
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
