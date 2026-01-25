package com.iqbal.gurmukhikeyboard50

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_RECORD_AUDIO = 200
    }

    private lateinit var tvStatus: TextView
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        findViewById<Button>(R.id.btnEnableKeyboard).setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnSelectKeyboard).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        findViewById<Button>(R.id.btnGrantMicPermission).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            } else {
                Toast.makeText(this, "Microphone permission already granted", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnPrivacyPolicy).setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        findViewById<Button>(R.id.btnShareApp).setOnClickListener {
            shareApp()
        }

        // Setting up the toggle listeners
        setupToggleListener(findViewById(R.id.switch_vibrate), "vibrate_on_keypress")
        setupToggleListener(findViewById(R.id.switch_sound), "sound_on_keypress")
        setupToggleListener(findViewById(R.id.switch_popup), "popup_on_keypress")
    }

    private fun shareApp() {
        val appPackageName = packageName
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            val shareMessage = "Check out this Gurmukhi Keyboard app: https://play.google.com/store/apps/details?id=$appPackageName"
            putExtra(Intent.EXTRA_TEXT, shareMessage)
        }
        startActivity(Intent.createChooser(shareIntent, "Share App via"))
    }

    private fun setupToggleListener(switch: SwitchMaterial, prefKey: String) {
        switch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(prefKey, isChecked).apply()
        }
    }

    private fun updateToggleButtons() {
        findViewById<SwitchMaterial>(R.id.switch_vibrate).isChecked = sharedPreferences.getBoolean("vibrate_on_keypress", true)
        findViewById<SwitchMaterial>(R.id.switch_sound).isChecked = sharedPreferences.getBoolean("sound_on_keypress", true)
        findViewById<SwitchMaterial>(R.id.switch_popup).isChecked = sharedPreferences.getBoolean("popup_on_keypress", true)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateToggleButtons()
    }

    private fun updateStatus() {
        val isEnabled = isInputMethodEnabled()
        val micPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        val statusText = "Keyboard Enabled: $isEnabled\nMic Permission: $micPermission"
        tvStatus.text = statusText
    }

    private fun isInputMethodEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val imi = imm.enabledInputMethodList.find { it.packageName == packageName }
        return imi != null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Microphone permission denied", Toast.LENGTH_SHORT).show()
                }
                updateStatus()
            }
        }
    }
}
