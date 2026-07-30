package com.iqbal.gurmukhikeyboard50

import android.content.Context
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

object DailyVerseManager {

    data class Verse(
        val verse: String,
        val translation: String,
        val source: String
    )

    fun getVerseForDate(context: Context, date: Date): Verse? {
        try {
            val inputStream = context.assets.open("daily_verses.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val json = String(buffer, Charsets.UTF_8)
            val jsonArray = JSONArray(json)
            if (jsonArray.length() == 0) return null

            val sdf = SimpleDateFormat("MM-dd", Locale.ENGLISH)
            val dateString = sdf.format(date)

            // 1. Try exact date match
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.has("date") && obj.getString("date") == dateString) {
                    return Verse(
                        obj.getString("verse"),
                        obj.getString("translation"),
                        obj.getString("source")
                    )
                }
            }
            
            // 2. Fallback: Use day of year to cycle through all available verses
            val calendar = Calendar.getInstance()
            calendar.time = date
            val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
            val index = dayOfYear % jsonArray.length()
            
            val obj = jsonArray.getJSONObject(index)
            return Verse(
                obj.getString("verse"),
                obj.getString("translation"),
                obj.getString("source")
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
