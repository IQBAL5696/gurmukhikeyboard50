package com.iqbal.gurmukhikeyboard50

import android.content.Context
import org.json.JSONObject
import java.io.IOException

class NanakshahiManager(private val context: Context) {

    private var sgpcDates: JSONObject? = null

    init {
        loadSgpcDates()
    }

    private fun loadSgpcDates() {
        try {
            val inputStream = context.assets.open("sgpc_dates.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            sgpcDates = JSONObject(String(buffer, Charsets.UTF_8))
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun convertToNanakshahi(day: Int, month: Int, year: Int, mode: String): String {
        val dateString = String.format("%04d-%02d-%02d", year, month, day)
        return sgpcDates?.optString(dateString, "") ?: ""
    }
}
