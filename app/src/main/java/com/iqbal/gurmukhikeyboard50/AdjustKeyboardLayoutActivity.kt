package com.iqbal.gurmukhikeyboard50

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

class AdjustKeyboardLayoutActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    private val fontSizeMin = 35
    private val fontSizeMax = 71

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_adjust_keyboard_layout)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        val fontSizeSeekBar = findViewById<SeekBar>(R.id.seekbar_font_size)
        val fontSizeValueTv = findViewById<TextView>(R.id.tv_font_size_value)

        // Load and set current values
        val currentFontSize = sharedPreferences.getInt("font_size_abs", 50)

        fontSizeSeekBar.progress = ((currentFontSize - fontSizeMin).toFloat() / (fontSizeMax - fontSizeMin) * 100).toInt()

        fontSizeValueTv.text = currentFontSize.toString()

        // Listeners
        fontSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = fontSizeMin + ((fontSizeMax - fontSizeMin) * (progress / 100f)).toInt()
                fontSizeValueTv.text = value.toString()
                sharedPreferences.edit().putInt("font_size_abs", value).apply()
                // Notify the keyboard service that the settings have changed
                sendSettingsChangedBroadcast()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun sendSettingsChangedBroadcast() {
        val intent = Intent(ImeConstants.ACTION_SETTINGS_CHANGED)
        sendBroadcast(intent)
    }
}
