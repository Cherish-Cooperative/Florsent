package com.example.zhenailife

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

class MusicService : Service() {

    private lateinit var mediaPlayer: MediaPlayer
    private val handler = Handler(Looper.getMainLooper())
    private var currentVolume: Float = 0f
    private val fadeDuration = 60_000L // 一分鐘
    private val fadeInterval = 1000L // 每秒增加音量一次
    private val fadeStep: Float = 1.0f / (fadeDuration / fadeInterval).toFloat()

    private var isFading = false // 防止多次啟動 fadeRunnable

    private val fadeRunnable = object : Runnable {
        override fun run() {
            if (currentVolume < 1.0f) {
                currentVolume += fadeStep
                // 確保音量不超過 1.0f
                currentVolume = if (currentVolume > 1.0f) 1.0f else currentVolume
                mediaPlayer.setVolume(currentVolume, currentVolume)
                Log.d("MusicService", "Current Volume: $currentVolume")
                handler.postDelayed(this, fadeInterval)
            } else {
                isFading = false // 漸入完成
                Log.d("MusicService", "Volume fade-in completed.")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaPlayer = MediaPlayer.create(this, R.raw.relaxing_soft_handpan)
        mediaPlayer.isLooping = true // 設置音樂循環播放
        mediaPlayer.setVolume(0f, 0f) // 初始音量設為 0
    }

    // 開始播放音樂
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.start()
            currentVolume = 0f
            if (!isFading) {
                isFading = true
                handler.post(fadeRunnable)
                Log.d("MusicService", "Starting volume fade-in.")
            }
        } else {
            Log.d("MusicService", "MediaPlayer is already playing.")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(fadeRunnable)
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        mediaPlayer.release()
        Log.d("MusicService", "MusicService destroyed and MediaPlayer released.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
} 