package com.iqbal.gurmukhikeyboard50

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar

class KeyboardSettingsActivity : AppCompatActivity() {

    private lateinit var updateHelper: UpdateHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keyboard_settings)

        val rootLayout: View = findViewById(R.id.keyboard_settings_root)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_fragment_container, SettingsFragment())
                .commit()
        }

        val tvAppVersion: TextView = findViewById(R.id.tv_app_version)
        val updateDot: View = findViewById(R.id.update_dot)
        
        try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                packageManager.getPackageInfo(packageName, 0)
            }
            tvAppVersion.text = "App Version: ${packageInfo.versionName}"
        } catch (e: PackageManager.NameNotFoundException) {
            tvAppVersion.text = "App Version: Not available"
        }

        // Check for updates
        updateHelper = UpdateHelper(this)
        updateHelper.checkForUpdate()

        updateDot.setOnClickListener {
            updateHelper.checkForUpdate()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        updateHelper.handleActivityResult(requestCode, resultCode, data)
        if (requestCode == 999) { // 999 is the request code in UpdateHelper
            if (resultCode != RESULT_OK) {
                Toast.makeText(this, "ਅਪਡੇਟ ਰੱਦ ਕਰ ਦਿੱਤੀ ਗਈ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateHelper.handleOnResume()
    }
}
