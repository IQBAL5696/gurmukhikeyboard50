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

class GurbaniPlayerService : Service() {

    private var player: ExoPlayer? = null

    companion object {
        const val ACTION_PLAY = "com.iqbal.gurmukhikeyboard50.ACTION_PLAY"
        const val ACTION_PAUSE = "com.iqbal.gurmukhikeyboard50.ACTION_PAUSE"
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

        val player = ExoPlayer.Builder(this).build()
        player.setMediaSource(mediaSource)
        player.prepare()
        this.player = player
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                player?.play()
                startForeground(NOTIFICATION_ID, createNotification())
            }
            ACTION_PAUSE -> {
                player?.pause()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
            }
            else -> {
                // This is for when the service is restarted.
                player?.play()
                startForeground(NOTIFICATION_ID, createNotification())
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Gurbani Player",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder.setContentTitle("Gurbani Radio")
            .setContentText("Live from Harmandir Sahib")
            .setSmallIcon(R.drawable.ic_play)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
