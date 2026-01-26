package com.iqbal.gurmukhikeyboard50

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

class AIConfigActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_config)

        val etKey = findViewById<EditText>(R.id.et_ai_api_key)
        val btnSave = findViewById<Button>(R.id.btn_save_ai_key)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        etKey.setText(prefs.getString(ImeConstants.PREF_AI_API_KEY, ""))

        btnSave.setOnClickListener {
            val key = etKey.text.toString().trim()
            if (key.isNotEmpty()) {
                prefs.edit().putString(ImeConstants.PREF_AI_API_KEY, key).apply()
                Toast.makeText(this, "API Key ਸੇਵ ਹੋ ਗਈ ਹੈ", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "ਕਿਰਪਾ ਕਰਕੇ Key ਭਰੋ", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
