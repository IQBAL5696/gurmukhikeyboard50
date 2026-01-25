package com.iqbal.gurmukhikeyboard50

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class TopRowSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.top_row_preferences, rootKey)
    }
}