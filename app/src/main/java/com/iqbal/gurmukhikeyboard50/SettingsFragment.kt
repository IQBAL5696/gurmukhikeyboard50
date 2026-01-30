package com.iqbal.gurmukhikeyboard50

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.launch
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
                // When "Custom Photo" is selected, automatically launch picker if no image exists
                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                val imagePath = sharedPrefs.getString(ImeConstants.PREF_CUSTOM_BACKGROUND_IMAGE, null)
                if (imagePath.isNullOrEmpty()) {
                    pickImageLauncher.launch("image/*")
                }
            }
            true
        }

        // New: Handle direct button click for selecting image
        val selectImagePref = findPreference<Preference>("pref_select_custom_image")
        selectImagePref?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            pickImageLauncher.launch("image/*")
            true
        }

        val manageWordsPref = findPreference<Preference>("pref_manage_learned_words")
        manageWordsPref?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val intent = Intent(requireContext(), LearnedWordsActivity::class.java)
            startActivity(intent)
            true
        }

        val clearDictionaryPref = findPreference<Preference>("pref_clear_dictionary")
        clearDictionaryPref?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            clearLearnedWords()
            true
        }
    }

    private fun clearLearnedWords() {
        val databaseHelper = DatabaseHelper(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                databaseHelper.deleteAllLearnedWords()
                Toast.makeText(requireContext(), "ਸਿੱਖੇ ਹੋਏ ਸ਼ਬਦ ਮਿਟਾ ਦਿੱਤੇ ਗਏ ਹਨ", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error clearing dictionary", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveImageToInternalStorage(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bitmap == null) {
                Toast.makeText(requireContext(), "ਫੋਟੋ ਲੋਡ ਨਹੀਂ ਹੋ ਸਕੀ", Toast.LENGTH_SHORT).show()
                return
            }

            val file = File(requireContext().filesDir, "custom_keyboard_bg.jpg")
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            
            // Save the absolute path
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            sharedPrefs.edit().putString(ImeConstants.PREF_CUSTOM_BACKGROUND_IMAGE, file.absolutePath).apply()
            
            Toast.makeText(requireContext(), "ਫੋਟੋ ਸੈੱਟ ਹੋ ਗਈ ਹੈ!", Toast.LENGTH_SHORT).show()
            
            // Force keyboard to refresh if it's currently showing
            val intent = Intent(ImeConstants.ACTION_SETTINGS_CHANGED)
            requireContext().sendBroadcast(intent)
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "ਫੋਟੋ ਸੇਵ ਕਰਨ ਵਿੱਚ ਗਲਤੀ ਆਈ", Toast.LENGTH_SHORT).show()
        }
    }
}
