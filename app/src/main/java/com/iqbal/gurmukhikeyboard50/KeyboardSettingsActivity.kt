package com.iqbal.gurmukhikeyboard50

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Window
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class KeyboardSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE) // Hide ActionBar/Toolbar
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keyboard_settings)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_fragment_container, SettingsFragment())
                .commit()
        }

        val btnAdjustLayout: Button = findViewById(R.id.btn_adjust_layout)
        btnAdjustLayout.setOnClickListener {
            val intent = Intent(this, AdjustKeyboardLayoutActivity::class.java)
            startActivity(intent)
        }

        val btnPrivacyPolicy: Button = findViewById(R.id.btn_privacy_policy)
        btnPrivacyPolicy.setOnClickListener {
            val intent = Intent(this, PrivacyPolicyActivity::class.java)
            startActivity(intent)
        }

        val tvAppVersion: TextView = findViewById(R.id.tv_app_version)
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            tvAppVersion.text = "App Version: ${packageInfo.versionName}"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("KeyboardSettings", "Error getting package info: ", e)
            tvAppVersion.text = "App Version: Not available"
        }
    }
}