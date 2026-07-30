package com.iqbal.gurmukhikeyboard50

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SettingsFragment : PreferenceFragmentCompat() {

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != ImeConstants.PREF_CUSTOM_BACKGROUND_IMAGE) {
            refreshKeyboard()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val imageUri = result.data?.data
            if (imageUri != null) {
                launchCropIntent(imageUri)
            }
        }
    }

    private val cropImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val file = File(requireContext().filesDir, "custom_keyboard_bg.jpg")
            if (file.exists()) {
                refreshKeyboard()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun launchCropIntent(uri: Uri) {
        try {
            val outputFile = File(requireContext().filesDir, "custom_keyboard_bg.jpg")
            val outputUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", outputFile)

            // Calculate exact keyboard aspect ratio based on screen size
            val displayMetrics = requireContext().resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            // Estimated keyboard height (usually around 40-45% of screen height in portrait)
            val estimatedKeyboardHeight = (displayMetrics.heightPixels * 0.42).toInt()

            val intent = Intent("com.android.camera.action.CROP").apply {
                setDataAndType(uri, "image/*")
                putExtra("crop", "true")
                // Use the calculated ratio
                putExtra("aspectX", screenWidth)
                putExtra("aspectY", estimatedKeyboardHeight)
                putExtra("outputX", screenWidth)
                putExtra("outputY", estimatedKeyboardHeight)
                putExtra("scale", true)
                putExtra("return-data", false)
                putExtra(MediaStore.EXTRA_OUTPUT, outputUri)
                putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            val resolveInfo = requireContext().packageManager.queryIntentActivities(intent, 0)
            if (resolveInfo.isNotEmpty()) {
                cropImageLauncher.launch(intent)
            } else {
                saveImageToInternalStorage(uri)
            }
        } catch (e: Exception) {
            saveImageToInternalStorage(uri)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.keyboard_preferences, rootKey)

        val timezonePreference = findPreference<ListPreference>("pref_widget_timezone")
        timezonePreference?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
            updateCalendarWidget()
            true
        }

        val themePreference = findPreference<ListPreference>("pref_keyboard_theme")
        themePreference?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            if (newValue == "custom") {
                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                val imagePath = sharedPrefs.getString(ImeConstants.PREF_CUSTOM_BACKGROUND_IMAGE, null)
                if (imagePath.isNullOrEmpty()) {
                    openPicker()
                }
            }
            true
        }

        findPreference<Preference>("pref_select_custom_image")?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            openPicker()
            true
        }

        findPreference<Preference>("pref_manage_learned_words")?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivity(Intent(requireContext(), LearnedWordsActivity::class.java))
            true
        }

        findPreference<Preference>("pref_clear_dictionary")?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            clearLearnedWords()
            true
        }

        findPreference<Preference>("pref_manage_shortcuts")?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivity(Intent(requireContext(), ShortcutsActivity::class.java))
            true
        }

        findPreference<Preference>("pref_toolbar_settings")?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivity(Intent(requireContext(), TopRowSettingsActivity::class.java))
            true
        }

        findPreference<Preference>("pref_privacy_policy")?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            startActivity(Intent(requireContext(), PrivacyPolicyActivity::class.java))
            true
        }
    }

    private fun updateCalendarWidget() {
        val intent = Intent(requireContext(), CalendarWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val ids = AppWidgetManager.getInstance(requireContext())
            .getAppWidgetIds(ComponentName(requireContext(), CalendarWidget::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        requireContext().sendBroadcast(intent)
    }

    private fun openPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
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
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "ਫ਼ੋਟੋ ਲੋਡ ਨਹੀਂ ਹੋ ਸਕੀ", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Calculate Keyboard Aspect Ratio
                val displayMetrics = requireContext().resources.displayMetrics
                val targetWidth = displayMetrics.widthPixels
                val targetHeight = (displayMetrics.heightPixels * 0.42).toInt()
                val targetAspect = targetWidth.toFloat() / targetHeight

                val width = originalBitmap.width
                val height = originalBitmap.height

                var newWidth = width
                var newHeight = height

                if (width.toFloat() / height > targetAspect) {
                    newWidth = (height * targetAspect).toInt()
                } else {
                    newHeight = (width / targetAspect).toInt()
                }

                val startX = (width - newWidth) / 2
                val startY = (height - newHeight) / 2

                val croppedBitmap = Bitmap.createBitmap(originalBitmap, startX, startY, newWidth, newHeight)

                val file = File(requireContext().filesDir, "custom_keyboard_bg.jpg")
                val outputStream = FileOutputStream(file)
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                outputStream.flush()
                outputStream.close()

                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
                sharedPrefs.edit().putString(ImeConstants.PREF_CUSTOM_BACKGROUND_IMAGE, file.absolutePath).apply()

                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "ਫ਼ੋਟੋ ਸੈੱਟ ਹੋ ਗਈ ਹੈ!", Toast.LENGTH_SHORT).show()
                    refreshKeyboard()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "ਫ਼ੋਟੋ ਸੇਵ ਕਰਨ ਵਿੱਚ ਗਲਤੀ ਆਈ", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun refreshKeyboard() {
        val intent = Intent(ImeConstants.ACTION_SETTINGS_CHANGED)
        requireContext().sendBroadcast(intent)
    }
}
