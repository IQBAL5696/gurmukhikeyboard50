package com.iqbal.gurmukhikeyboard50

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class TranslatorActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var translationManager: TranslationManager
    private lateinit var sourceSpinner: Spinner
    private lateinit var targetSpinner: Spinner
    private lateinit var etSourceText: EditText
    private lateinit var tvTranslatedResult: TextView
    private lateinit var progressBar: ProgressBar
    private var tts: TextToSpeech? = null

    private val speechResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!matches.isNullOrEmpty()) {
                    etSourceText.setText(matches[0])
                    performTranslation()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translator)

        translationManager = TranslationManager(this)
        tts = TextToSpeech(this, this)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        sourceSpinner = findViewById(R.id.source_language_spinner)
        targetSpinner = findViewById(R.id.target_language_spinner)
        etSourceText = findViewById(R.id.et_source_text)
        tvTranslatedResult = findViewById(R.id.tv_translated_result)
        progressBar = findViewById(R.id.progress_bar)

        val btnSwap = findViewById<ImageButton>(R.id.btn_swap_languages)
        val btnClear = findViewById<ImageButton>(R.id.btn_clear_input)
        val btnTranslate = findViewById<Button>(R.id.btn_translate_now)
        val btnVoice = findViewById<ImageButton>(R.id.btn_voice_input)
        val btnSpeak = findViewById<ImageButton>(R.id.btn_speak_output)
        val btnCopy = findViewById<ImageButton>(R.id.btn_copy_output)
        val btnShare = findViewById<ImageButton>(R.id.btn_share_output)

        setupSpinners()

        btnSwap.setOnClickListener {
            translationManager.swapLanguages()
            updateSpinnerSelections()
            performTranslation()
        }

        btnClear.setOnClickListener {
            etSourceText.setText("")
            tvTranslatedResult.text = ""
        }

        btnTranslate.setOnClickListener {
            performTranslation()
        }

        btnVoice.setOnClickListener {
            startVoiceInput()
        }

        btnSpeak.setOnClickListener {
            speakOutput()
        }

        btnCopy.setOnClickListener {
            copyOutput()
        }

        btnShare.setOnClickListener {
            shareOutput()
        }

        etSourceText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrBlank()) {
                    tvTranslatedResult.text = ""
                } else {
                    performTranslation()
                }
            }
        })
    }

    private fun setupSpinners() {
        val languageNames = translationManager.availableLanguages.map { it.name }
        val adapter = ArrayAdapter(this, R.layout.spinner_item, languageNames)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)

        sourceSpinner.adapter = adapter
        targetSpinner.adapter = adapter

        // Set default: Punjabi -> English if available
        val punjabiIndex = translationManager.availableLanguages.indexOfFirst { it.code == "pa" }
        val englishIndex = translationManager.availableLanguages.indexOfFirst { it.code == "en" }

        if (punjabiIndex != -1) sourceSpinner.setSelection(punjabiIndex)
        if (englishIndex != -1) targetSpinner.setSelection(englishIndex)

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedLang = translationManager.availableLanguages[position]
                if (parent?.id == R.id.source_language_spinner) {
                    translationManager.sourceLanguage = selectedLang
                } else if (parent?.id == R.id.target_language_spinner) {
                    translationManager.targetLanguage = selectedLang
                }
                performTranslation()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        sourceSpinner.onItemSelectedListener = listener
        targetSpinner.onItemSelectedListener = listener
    }

    private fun updateSpinnerSelections() {
        val srcIndex = translationManager.availableLanguages.indexOf(translationManager.sourceLanguage)
        val tgtIndex = translationManager.availableLanguages.indexOf(translationManager.targetLanguage)
        if (srcIndex != -1) sourceSpinner.setSelection(srcIndex)
        if (tgtIndex != -1) targetSpinner.setSelection(tgtIndex)
    }

    private fun performTranslation() {
        val text = etSourceText.text.toString().trim()
        if (text.isEmpty()) {
            tvTranslatedResult.text = ""
            return
        }

        progressBar.visibility = View.VISIBLE
        translationManager.translate(text, tvTranslatedResult)
        // Hide progress bar after short delay
        tvTranslatedResult.postDelayed({
            progressBar.visibility = View.GONE
        }, 500)
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, translationManager.sourceLanguage.speechCode)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in ${translationManager.sourceLanguage.name}...")
        }
        try {
            speechResultLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice input not supported", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speakOutput() {
        val text = tvTranslatedResult.text.toString().trim()
        if (text.isNotEmpty() && text != "Translating...") {
            try {
                val locale = Locale.forLanguageTag(translationManager.targetLanguage.speechCode)
                tts?.language = locale
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "translator_tts")
            } catch (e: Exception) {
                Toast.makeText(this, "Text-to-speech error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyOutput() {
        val text = tvTranslatedResult.text.toString().trim()
        if (text.isNotEmpty()) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Translation", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareOutput() {
        val text = tvTranslatedResult.text.toString().trim()
        if (text.isNotEmpty()) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Translation"))
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d("TranslatorActivity", "TTS initialized")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts?.stop()
        tts?.shutdown()
        translationManager.close()
    }
}
