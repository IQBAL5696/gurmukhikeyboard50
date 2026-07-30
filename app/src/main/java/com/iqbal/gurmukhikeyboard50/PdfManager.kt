package com.iqbal.gurmukhikeyboard50

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.util.Locale

object PdfManager {

    fun isDownloaded(context: Context, fileName: String): Boolean {
        // Check app-specific external files dir (Reliable on Android 10+)
        val appSpecificFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (appSpecificFile.exists()) return true
        
        // Check public downloads dir (Legacy)
        val publicFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        return publicFile.exists()
    }

    fun downloadPdf(context: Context, url: String, name: String, fileName: String) {
        try {
            val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(name)
                .setDescription("ਡਾਊਨਲੋਡ ਹੋ ਰਿਹਾ ਹੈ...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(destinationFile))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            Toast.makeText(context, "ਡਾਊਨਲੋਡ ਸ਼ੁਰੂ ਹੋ ਗਿਆ", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "ਡਾਊਨਲੋਡ ਫੇਲ ਹੋਇਆ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun openPdf(context: Context, fileName: String) {
        val file = when {
            fileName.startsWith("/") -> File(fileName)
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName).exists() -> 
                File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            else -> File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        }
        
        if (!file.exists()) {
            Toast.makeText(context, "ਫਾਈਲ ਨਹੀਂ ਲੱਭੀ", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(context, PdfViewerActivity::class.java).apply {
                putExtra("pdf_path", file.absolutePath)
                putExtra("pdf_name", file.name.replace(".pdf", "").replace("_", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() })
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "PDF ਖੋਲ੍ਹਣ ਵਿੱਚ ਦਿੱਕਤ ਆਈ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun importPdf(context: Context, uri: Uri, originalFileName: String): String? {
        try {
            val destinationDir = File(context.filesDir, "custom_books")
            if (!destinationDir.exists()) destinationDir.mkdirs()
            
            val destinationFile = File(destinationDir, originalFileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return destinationFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
