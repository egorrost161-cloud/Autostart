package com.example.autostart

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class KeepAliveService : Service() {

    companion object {
        const val CHANNEL_ID = "autostart_keepalive"
        const val NOTIF_ID = 101
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIF_ID, notification)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val delaySec = prefs.getLong("delay_sec", BootReceiver.DEFAULT_DELAY_SEC)
        val delayMs = delaySec * 1000L

        Log.i("AutoStart", "KeepAliveService запущен, задержка ${delaySec}с")

        Handler(Looper.getMainLooper()).postDelayed({
            launchTarget()
        }, delayMs)

        return START_STICKY
    }

    private fun launchTarget() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val targetPackage = prefs.getString("target_package", null)

        if (targetPackage.isNullOrEmpty()) {
            Log.w("AutoStart", "Пакет не выбран, запуск пропущен")
            return
        }

        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Log.i("AutoStart", "Запущен $targetPackage")
            } else {
                Log.e("AutoStart", "Не найдена точка входа для $targetPackage")
            }
        } catch (e: Exception) {
            Log.e("AutoStart", "Ошибка запуска: ${e.message}")
        }
    }

    private fun buildNotification(): android.app.Notification {
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoStart активен")
            .setContentText("Следит за запуском приложения")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoStart KeepAlive",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Канал для удержания сервиса автозапуска"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
