package com.iqbal.gurmukhikeyboard50

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class SearchResultsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.search_results_activity)

        val webView: WebView = findViewById(R.id.search_results_webview)
        val query = intent.getStringExtra("QUERY")

        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()

        if (query != null) {
            val url = if (android.util.Patterns.WEB_URL.matcher(query).matches()) {
                query
            } else {
                // ✅ ਆਪਣੀ custom search engine ਲਿੰਕ ਲਗਾਉ (Google / search.gurmukhi.com)
                "https://www.google.com/search?q=$query"
            }

            // ✅ load main URL
            webView.loadUrl(url)

            // ✅ fallback message if page doesn’t load in time
            webView.postDelayed({
                if (webView.progress < 50) { // page not loaded enough
                    val fallbackHtml = """
                        <html>
                        <body style="text-align:center; font-family:sans-serif; margin-top:100px;">
                            <h2>Connecting...</h2>
                            <p>Please <a href="$url">click here</a> if you are not redirected within a few seconds.</p>
                        </body>
                        </html>
                    """
                    webView.loadData(fallbackHtml, "text/html", "UTF-8")
                }
            }, 4000) // check after 4 seconds
        }
    }
}
