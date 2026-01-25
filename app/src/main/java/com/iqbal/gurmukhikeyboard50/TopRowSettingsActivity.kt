package com.iqbal.gurmukhikeyboard50

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class TopRowSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_top_row_settings)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.top_row_settings_fragment_container, TopRowSettingsFragment())
                .commit()
        }
    }
}