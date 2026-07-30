package com.iqbal.gurmukhikeyboard50

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import okhttp3.OkHttpClient
import androidx.preference.PreferenceManager

class GurbaniPlayerService : Service() {

    private var player: ExoPlayer? = null

    companion object {
        const val ACTION_PLAY = "com.iqbal.gurmukhikeyboard50.ACTION_PLAY"
        const val ACTION_PAUSE = "com.iqbal.gurmukhikeyboard50.ACTION_PAUSE"
        const val ACTION_STOP = "com.iqbal.gurmukhikeyboard50.ACTION_STOP"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "GurbaniPlayerChannel"
        private const val STREAM_URL = "https://live.sgpc.net:8443/"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val okHttpClient = OkHttpClient.Builder().build()
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(STREAM_URL))

        val newPlayer = ExoPlayer.Builder(this).build()
        newPlayer.setMediaSource(mediaSource)
        newPlayer.prepare()
        this.player = newPlayer
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        when (intent?.action) {
            ACTION_PLAY -> {
                player?.play()
                startForeground(NOTIFICATION_ID, createNotification())
                prefs.edit().putBoolean("is_gurbani_playing", true).apply()
            }
            ACTION_PAUSE, ACTION_STOP -> {
                player?.pause()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                prefs.edit().putBoolean("is_gurbani_playing", false).apply()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("is_gurbani_playing", false).apply()
        player?.stop()
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Gurbani Player",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, GurbaniPlayerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
        }

        return builder.setContentTitle("Gurbani Radio")
            .setContentText("Live from Harmandir Sahib")
            .setSmallIcon(R.drawable.ic_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
