package com.iqbal.gurmukhikeyboard50

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton

class LiveDarbarSahibActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var fabPlayPause: FloatingActionButton
    private lateinit var fabStop: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_darbar_sahib)

        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        tvStatus = findViewById(R.id.tv_status)
        fabPlayPause = findViewById(R.id.fab_play_pause)
        fabStop = findViewById(R.id.fab_stop)

        val btnAddShortcut = findViewById<View>(R.id.btn_add_shortcut_page)
        btnAddShortcut.setOnClickListener {
            addHomeShortcut()
        }

        fabPlayPause.setOnClickListener {
            togglePlayPause()
        }

        fabStop.setOnClickListener {
            stopAudio()
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun isPlaying(): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean("is_gurbani_playing", false)
    }

    private fun updateUI() {
        if (isPlaying()) {
            tvStatus.text = "🔊 ਲਾਈਵ ਪ੍ਰਸਾਰਣ ਚਾਲੂ ਹੈ (Playing Live Kirtan)"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            fabPlayPause.setImageResource(R.drawable.ic_pause)
        } else {
            tvStatus.text = "▶️ ਲਾਈਵ ਪ੍ਰਸਾਰਣ ਚਲਾਉਣ ਲਈ ਪਲੇ ਬਟਨ ਦਬਾਓ"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.black))
            fabPlayPause.setImageResource(R.drawable.ic_play)
        }
    }

    private fun togglePlayPause() {
        if (isPlaying()) {
            stopAudio()
        } else {
            playAudio()
        }
    }

    private fun playAudio() {
        val intent = Intent(this, GurbaniPlayerService::class.java).apply {
            action = GurbaniPlayerService.ACTION_PLAY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean("is_gurbani_playing", true)
            .apply()
        updateUI()
        Toast.makeText(this, "Playing Live Darbar Sahib Kirtan...", Toast.LENGTH_SHORT).show()
    }

    private fun stopAudio() {
        val intent = Intent(this, GurbaniPlayerService::class.java).apply {
            action = GurbaniPlayerService.ACTION_STOP
        }
        startService(intent)
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean("is_gurbani_playing", false)
            .apply()
        updateUI()
        Toast.makeText(this, "Live Kirtan Stopped", Toast.LENGTH_SHORT).show()
    }

    private fun addHomeShortcut() {
        val shortcutIntent = Intent(this, LiveDarbarSahibActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val pinShortcutInfo = ShortcutInfoCompat.Builder(this, "live_darbar_sahib_home_shortcut")
                .setShortLabel("ਲਾਈਵ ਦਰਬਾਰ ਸਾਹਿਬ")
                .setLongLabel("ਸ੍ਰੀ ਹਰਿਮੰਦਰ ਸਾਹਿਬ ਲਾਈਵ ਕੀਰਤਨ")
                .setIcon(getShortcutIcon(R.drawable.ik_onkar_svg))
                .setIntent(shortcutIntent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(this, pinShortcutInfo, null)
        }

        sendLegacyShortcutBroadcast("ਲਾਈਵ ਦਰਬਾਰ ਸਾਹਿਬ", R.drawable.ik_onkar_svg, shortcutIntent)
        Toast.makeText(this, "ਸ਼ਾਰਟਕੱਟ ਹੋਮ ਸਕ੍ਰੀਨ 'ਤੇ ਜੋੜਨ ਦੀ ਬੇਨਤੀ ਭੇਜੀ ਗਈ", Toast.LENGTH_LONG).show()
    }

    private fun getShortcutIcon(resId: Int): IconCompat {
        return try {
            val drawable = ContextCompat.getDrawable(this, resId)
            if (drawable != null) {
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(108),
                    drawable.intrinsicHeight.coerceAtLeast(108),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                IconCompat.createWithBitmap(bitmap)
            } else {
                IconCompat.createWithResource(this, resId)
            }
        } catch (e: Exception) {
            IconCompat.createWithResource(this, resId)
        }
    }

    private fun sendLegacyShortcutBroadcast(name: String, iconRes: Int, targetIntent: Intent) {
        try {
            val addIntent = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, targetIntent)
                putExtra(Intent.EXTRA_SHORTCUT_NAME, name)
                putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(this@LiveDarbarSahibActivity, iconRes))
                putExtra("duplicate", false)
            }
            sendBroadcast(addIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
