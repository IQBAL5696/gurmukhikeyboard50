package com.iqbal.gurmukhikeyboard50

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs
import kotlin.math.max

class OCRActivity : AppCompatActivity() {

    private lateinit var ivSelectedImage: ImageView
    private lateinit var etRecognizedText: EditText
    private lateinit var cbTableMode: CheckBox
    private val PICK_IMAGE_REQUEST = 100
    private var lastVisionText: Text? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr)

        ivSelectedImage = findViewById(R.id.iv_selected_image)
        etRecognizedText = findViewById(R.id.et_recognized_text)
        cbTableMode = findViewById(R.id.cb_table_mode)

        findViewById<Button>(R.id.btn_select_image).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        cbTableMode.setOnCheckedChangeListener { _, _ -> formatText() }

        findViewById<Button>(R.id.btn_cancel).setOnClickListener { finish() }

        findViewById<Button>(R.id.btn_insert).setOnClickListener {
            val text = etRecognizedText.text.toString()
            if (text.isNotEmpty()) {
                val intent = Intent(this, MyKeyboardIME::class.java)
                intent.putExtra("recognized_text", text)
                startService(intent)
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val imageUri = data.data ?: return
            ivSelectedImage.visibility = View.VISIBLE
            ivSelectedImage.setImageURI(imageUri)
            recognizeTextFromImage(imageUri)
        }
    }

    private fun recognizeTextFromImage(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(this, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    lastVisionText = visionText
                    formatText()
                }
                .addOnFailureListener { e -> Toast.makeText(this, "ਗਲਤੀ: ${e.message}", Toast.LENGTH_SHORT).show() }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun formatText() {
        val visionText = lastVisionText ?: return
        val allLines = mutableListOf<Text.Line>()
        for (block in visionText.textBlocks) allLines.addAll(block.lines)
        if (allLines.isEmpty()) return

        allLines.sortBy { it.boundingBox?.top ?: 0 }
        val rows = mutableListOf<MutableList<Text.Line>>()
        var currentRow = mutableListOf<Text.Line>()
        currentRow.add(allLines[0])
        rows.add(currentRow)

        for (i in 1 until allLines.size) {
            val prevLine = allLines[i - 1]
            val currLine = allLines[i]
            val prevTop = prevLine.boundingBox?.top ?: 0
            val currTop = currLine.boundingBox?.top ?: 0
            val prevHeight = prevLine.boundingBox?.height() ?: 20
            if (abs(currTop - prevTop) < (prevHeight * 0.7)) currentRow.add(currLine)
            else { currentRow = mutableListOf<Text.Line>(); currentRow.add(currLine); rows.add(currentRow) }
        }

        val formattedRows = mutableListOf<List<String>>()
        var maxCols = 0
        for (row in rows) {
            row.sortBy { it.boundingBox?.left ?: 0 }
            val rowTexts = row.map { it.text }
            formattedRows.add(rowTexts)
            maxCols = max(maxCols, rowTexts.size)
        }

        if (cbTableMode.isChecked) {
            val colWidths = IntArray(maxCols) { 0 }
            for (row in formattedRows) {
                for (i in row.indices) colWidths[i] = max(colWidths[i], row[i].length)
            }

            val resultText = StringBuilder()
            val separator = StringBuilder("+")
            for (width in colWidths) separator.append("-".repeat(width + 2)).append("+")
            
            for (row in formattedRows) {
                resultText.append(separator).append("\n|")
                for (i in 0 until maxCols) {
                    val text = if (i < row.size) row[i] else ""
                    resultText.append(" ").append(text.padEnd(colWidths[i])).append(" |")
                }
                resultText.append("\n")
            }
            resultText.append(separator)
            etRecognizedText.setText(resultText.toString())
        } else {
            val resultText = StringBuilder()
            for (row in formattedRows) {
                for (i in row.indices) {
                    resultText.append(row[i])
                    if (i < row.size - 1) resultText.append("        ")
                }
                resultText.append("\n")
            }
            etRecognizedText.setText(resultText.toString().trim())
        }
    }
}
