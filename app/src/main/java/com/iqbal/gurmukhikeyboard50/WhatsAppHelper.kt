package com.iqbal.gurmukhikeyboard50

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

val WHATSAPP_PACKAGE_NAMES = listOf("com.whatsapp", "com.whatsapp.w4b")

fun handleWhatsAppEnterKey(inputConnection: InputConnection?, editorInfo: EditorInfo?): Boolean {
    if (editorInfo?.packageName !in WHATSAPP_PACKAGE_NAMES) {
        return false
    }
    inputConnection?.commitText("\n", 1)
    return true
}

object WhatsAppHelper {
    private val phoneRegex = Regex("""^[0-9]{10}$""")

    fun isPhoneNumber(text: String): Boolean {
        return phoneRegex.matches(text.trim())
    }

    fun openWhatsAppChat(context: Context, phoneNumber: String) {
        val cleanNumber = phoneNumber.trim().replace("+", "").replace(" ", "")
        val finalNumber = if (cleanNumber.length == 10) "91$cleanNumber" else cleanNumber
        
        try {
            val url = "https://api.whatsapp.com/send?phone=$finalNumber"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
