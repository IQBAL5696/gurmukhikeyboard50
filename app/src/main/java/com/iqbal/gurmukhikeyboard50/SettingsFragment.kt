package com.iqbal.gurmukhikeyboard50

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.io.File
import java.io.FileOutputStream

class SettingsFragment : PreferenceFragmentCompat() {

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            saveImageToInternalStorage(it)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.keyboard_preferences, rootKey)

        val themePreference = findPreference<ListPreference>("pref_keyboard_theme")
        themePreference?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            if (newValue == "custom") {
                pickImageLauncher.launch("image/*")
            }
            true
        }
    }

    private fun saveImageToInternalStorage(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val file = File(requireContext().filesDir, "custom_keyboard_bg.jpg")
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            Toast.makeText(requireContext(), "Photo saved! Please restart keyboard.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Failed to save photo", Toast.LENGTH_SHORT).show()
        }
    }
}
