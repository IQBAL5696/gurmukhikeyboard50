package com.iqbal.gurmukhikeyboard50

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class PhotoEditorActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var cropButton: Button
    private lateinit var shareButton: Button
    private lateinit var uri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_editor)

        imageView = findViewById(R.id.imageView)
        cropButton = findViewById(R.id.cropButton)
        shareButton = findViewById(R.id.shareButton)

        uri = Uri.parse(intent.getStringExtra("image_uri"))
        val inputStream = contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        imageView.setImageBitmap(bitmap)

        cropButton.setOnClickListener { openSystemEditor(uri) }
        shareButton.setOnClickListener { shareImage(uri) }
    }

    private fun openSystemEditor(uri: Uri) {
        val editIntent = Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(editIntent, "Edit photo with…"))
    }

    private fun shareImage(uri: Uri) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share photo via"))
    }
}
