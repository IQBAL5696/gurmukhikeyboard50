package com.iqbal.gurmukhikeyboard50

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object GurbaniSearchHelper {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun searchGurbani(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        
        val results = mutableListOf<String>()
        try {
            // Encode the query for URL safety
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            
            // Search type 3 is often better for general Gurbani search
            val url = "https://api.gurbaninow.com/v2/search/$encodedQuery?searchtype=3" 
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
                
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val jsonData = response.body?.string() ?: return emptyList()
                val json = JSONObject(jsonData)
                
                if (json.has("shabads")) {
                    val shabads = json.getJSONArray("shabads")
                    for (i in 0 until minOf(shabads.length(), 30)) {
                        val shabad = shabads.getJSONObject(i)
                        if (shabad.has("line")) {
                            val line = shabad.getJSONObject("line")
                            if (line.has("gurmukhi")) {
                                val gurmukhi = line.getJSONObject("gurmukhi").getString("unicode")
                                results.add(gurmukhi)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results.distinct()
    }
}
