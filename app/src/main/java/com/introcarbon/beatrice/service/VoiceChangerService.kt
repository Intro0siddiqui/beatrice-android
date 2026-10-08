package com.introcarbon.beatrice.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.introcarbon.beatrice.MainActivity
import com.introcarbon.beatrice.jni.BeatriceJni

class VoiceChangerService : Service {

    constructor() : super()

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "beatrice_vc_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.introcarbon.beatrice.ACTION_START"
        const val ACTION_STOP = "com.introcarbon.beatrice.ACTION_STOP"
        const val ACTION_SET_PITCH = "com.introcarbon.beatrice.ACTION_SET_PITCH"
        const val ACTION_SET_GATE = "com.introcarbon.beatrice.ACTION_SET_GATE"

        const val EXTRA_MODEL_PATH = "extra_model_path"
        const val EXTRA_MODEL_NAME = "extra_model_name"
        const val EXTRA_PITCH = "extra_pitch"
        const val EXTRA_GATE = "extra_gate"

        @Volatile
        var isServiceRunning: Boolean = false
            private set
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BeatriceVC::AudioWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L /* 10 hours max safety */)

        BeatriceJni.initEngine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                val modelPath = intent?.getStringExtra(EXTRA_MODEL_PATH) ?: ""
                val modelName = intent?.getStringExtra(EXTRA_MODEL_NAME) ?: "Custom Beatrice Voice"
                val pitch = intent?.getFloatExtra(EXTRA_PITCH, 0.0f) ?: 0.0f
                val gate = intent?.getFloatExtra(EXTRA_GATE, -50.0f) ?: -50.0f

                startForegroundWithNotification(modelName)

                if (modelPath.isNotEmpty()) {
                    BeatriceJni.loadModel(modelPath)
                }
                BeatriceJni.setPitchShift(pitch)
                BeatriceJni.setNoiseGate(gate)
                BeatriceJni.startAudio()
                isServiceRunning = true
            }

            ACTION_STOP -> {
                stopVoiceChanger()
            }

            ACTION_SET_PITCH -> {
                val pitch = intent?.getFloatExtra(EXTRA_PITCH, 0.0f) ?: 0.0f
                BeatriceJni.setPitchShift(pitch)
            }

            ACTION_SET_GATE -> {
                val gate = intent?.getFloatExtra(EXTRA_GATE, -50.0f) ?: -50.0f
                BeatriceJni.setNoiseGate(gate)
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification(modelName: String) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VoiceChangerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Beatrice Voice Conversion Active")
            .setContentText("Converting: $modelName")
            .setSubText("Ultra-Low Latency DSP")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopVoiceChanger() {
        BeatriceJni.stopAudio()
        isServiceRunning = false
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVoiceChanger()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Beatrice Real-Time Voice Changer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time voice conversion status and audio engine controls"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
