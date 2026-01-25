package com.iqbal.gurmukhikeyboard50

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

class PrivacyPolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        val webView: WebView = findViewById(R.id.privacy_policy_webview)
        webView.loadUrl("file:///android_asset/privacy_policy.html")
    }
}
