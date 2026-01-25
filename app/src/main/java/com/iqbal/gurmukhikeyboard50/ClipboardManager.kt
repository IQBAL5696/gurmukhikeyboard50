package com.iqbal.gurmukhikeyboard50

import android.content.ClipboardManager
import android.content.Context

class MyClipboardManager(private val context: Context) {

    private val clipboardManager:
            ClipboardManager? = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    fun getClipboardItems(): List<String> {
        val items = mutableListOf<String>()
        clipboardManager?.let {
            val clipData = it.primaryClip
            clipData?.let {
                for (i in 0 until it.itemCount) {
                    val item = it.getItemAt(i)
                    item.text?.let { text ->
                        items.add(text.toString())
                    }
                }
            }
        }
        return items
    }
}
