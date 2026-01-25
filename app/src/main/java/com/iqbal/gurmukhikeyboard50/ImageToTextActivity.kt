package com.iqbal.gurmukhikeyboard50

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ImageToTextActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var editText: EditText
    private lateinit var translatedTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var toggleLanguageButton: Button
    private var translator: Translator? = null
    private var isPunjabiToEnglish = true // ✅ toggle direction

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val imageUri = result.data?.data
                val imageBitmap: Bitmap? = imageUri?.let {
                    if (Build.VERSION.SDK_INT < 28) {
                        MediaStore.Images.Media.getBitmap(contentResolver, it)
                    } else {
                        val source = ImageDecoder.createSource(contentResolver, it)
                        ImageDecoder.decodeBitmap(source)
                    }
                }
                if (imageBitmap != null) {
                    imageView.setImageBitmap(imageBitmap)
                    recognizePunjabiText(imageBitmap)
                } else {
                    Toast.makeText(this, "Could not load image", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_to_text)
        Log.d("ImageToTextActivity", "onCreate called")

        imageView = findViewById(R.id.image_view)
        editText = findViewById(R.id.edit_text)
        translatedTextView = findViewById(R.id.translated_text_view)
        progressBar = findViewById(R.id.progress_bar)

        val selectImageButton: Button = findViewById(R.id.select_image_button)
        val translateButton: Button = findViewById(R.id.translate_button)
        toggleLanguageButton = findViewById(R.id.toggle_language_button)

        selectImageButton.setOnClickListener {
            val intent =
                Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImage.launch(intent)
        }

        translateButton.setOnClickListener { translateText() }

        toggleLanguageButton.setOnClickListener {
            isPunjabiToEnglish = !isPunjabiToEnglish
            setupTranslator()
            toggleLanguageButton.text =
                if (isPunjabiToEnglish) "Punjabi → English" else "English → Punjabi"
        }

        setupTranslator()
        toggleLanguageButton.text = "Punjabi → English"
    }

    // ✅ ML Kit OCR (supports Gurmukhi text)
    private fun recognizePunjabiText(imageBitmap: Bitmap) {
        progressBar.visibility = View.VISIBLE
        val image = InputImage.fromBitmap(imageBitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                progressBar.visibility = View.GONE
                editText.setText(visionText.text)
                if (visionText.text.isBlank()) {
                    Toast.makeText(
                        this,
                        "No Punjabi text detected. Try clearer Gurmukhi image.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this, "Text extracted successfully", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this,
                    "Text recognition failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ✅ Translator setup (toggles direction)
    private fun setupTranslator() {
        translator?.close() // close previous
        val options = if (isPunjabiToEnglish) {
            TranslatorOptions.Builder()
                .setSourceLanguage("pa")
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
        } else {
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage("pa")
                .build()
        }
        translator = Translation.getClient(options)
    }

    private fun translateText() {
        val text = editText.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "No text to translate", Toast.LENGTH_SHORT).show()
            return
        }

        translator?.let { translator ->
            progressBar.visibility = View.VISIBLE
            val conditions = DownloadConditions.Builder().build()

            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    translator.translate(text)
                        .addOnSuccessListener { translatedText ->
                            progressBar.visibility = View.GONE
                            translatedTextView.text = translatedText
                            Toast.makeText(this, "Translation complete", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            progressBar.visibility = View.GONE
                            Toast.makeText(
                                this,
                                "Translation failed: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this,
                        "Model download failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        } ?: Toast.makeText(this, "Translator not initialized", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        translator?.close()
    }
}
