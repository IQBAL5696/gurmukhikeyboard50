package com.iqbal.gurmukhikeyboard50

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.net.URLEncoder

class QRCodeActivity : AppCompatActivity() {

    private lateinit var etInput: EditText
    private lateinit var ivQrCode: ImageView
    private lateinit var tvShareHint: TextView
    private lateinit var pbLoading: ProgressBar
    private var currentBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qrcode)

        etInput = findViewById(R.id.et_qr_input)
        ivQrCode = findViewById(R.id.iv_qrcode)
        tvShareHint = findViewById(R.id.tv_share_hint)
        pbLoading = findViewById(R.id.pb_qr_loading)

        findViewById<Button>(R.id.btn_generate_qr).setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                // ਜੇਕਰ ਇਹ UPI ID ਹੈ, ਤਾਂ ਇਸਨੂੰ ਪੇਮੈਂਟ ਫਾਰਮੈਟ ਵਿੱਚ ਬਦਲੋ
                val finalData = if (text.contains("@") && !text.startsWith("upi://")) {
                    "upi://pay?pa=$text&pn=Business&cu=INR"
                } else {
                    text
                }
                generateQRCode(finalData)
            } else {
                Toast.makeText(this, "ਕਿਰਪਾ ਕਰਕੇ ਪਹਿਲਾਂ ਕੁਝ ਲਿਖੋ", Toast.LENGTH_SHORT).show()
            }
        }

        ivQrCode.setOnLongClickListener {
            shareQRCode()
            true
        }

        findViewById<Button>(R.id.btn_qr_close).setOnClickListener { finish() }
    }

    private fun generateQRCode(text: String) {
        pbLoading.visibility = View.VISIBLE
        ivQrCode.visibility = View.GONE
        tvShareHint.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val url = "https://api.qrserver.com/v1/create-qr-code/?size=500x500&data=$encodedText"
                
                val inputStream = URL(url).openStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        currentBitmap = bitmap
                        ivQrCode.setImageBitmap(bitmap)
                        ivQrCode.visibility = View.VISIBLE
                        tvShareHint.visibility = View.VISIBLE
                    } else {
                        Toast.makeText(this@QRCodeActivity, "QR ਕੋਡ ਨਹੀਂ ਬਣ ਸਕਿਆ", Toast.LENGTH_SHORT).show()
                    }
                    pbLoading.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pbLoading.visibility = View.GONE
                    Toast.makeText(this@QRCodeActivity, "ਇੰਟਰਨੈੱਟ ਚੈੱਕ ਕਰੋ ਜੀ", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareQRCode() {
        val bitmap = currentBitmap ?: return
        try {
            val cachePath = File(cacheDir, "images")
            cachePath.mkdirs()
            val stream = FileOutputStream("$cachePath/image.png")
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val newFile = File(cachePath, "image.png")
            val contentUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", newFile)

            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            shareIntent.setDataAndType(contentUri, contentResolver.getType(contentUri))
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri)
            startActivity(Intent.createChooser(shareIntent, "QR ਕੋਡ ਸਾਂਝਾ ਕਰੋ"))
        } catch (e: Exception) {
            Toast.makeText(this, "ਸਾਂਝਾ ਕਰਨ ਵਿੱਚ ਗਲਤੀ ਆਈ", Toast.LENGTH_SHORT).show()
        }
    }
}
