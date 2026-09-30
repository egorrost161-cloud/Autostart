package com.example.autostart

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
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
        const val DEFAULT_MONITOR_INTERVAL_MS = 8000L
        const val DEFAULT_DELAY_SEC = 15L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var monitorRunnable: Runnable? = null
    private var isStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIF_ID, notification)

        if (isStarted) {
            Log.i("AutoStart", "Сервис уже работает")
            return START_STICKY
        }
        isStarted = true

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val delaySec = prefs.getLong("delay_sec", DEFAULT_DELAY_SEC)
        val delayMs = delaySec * 1000L

        Log.i("AutoStart", "Сервис запущен, ждём ${delaySec}с")

        handler.postDelayed({
            launchTarget()

            // Проверяем: включён ли монитор?
            val monitorEnabled = prefs.getBoolean("monitor_enabled", true)
            if (monitorEnabled) {
                startMonitor()
            } else {
                Log.i("AutoStart", "Монитор выключен, сервис больше ничего не делает")
            }
        }, delayMs)

        return START_STICKY
    }

    private fun startMonitor() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val intervalMs = prefs.getLong("monitor_interval_ms", DEFAULT_MONITOR_INTERVAL_MS)

        monitorRunnable = object : Runnable {
            override fun run() {
                launchTarget()
                handler.postDelayed(this, intervalMs)
            }
        }
        handler.postDelayed(monitorRunnable!!, intervalMs)

        Log.i("AutoStart", "Монитор запущен, интервал ${intervalMs}мс")
    }

    private fun launchTarget() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val targetPackage = prefs.getString("target_package", null)

        if (targetPackage.isNullOrEmpty()) {
            Log.w("AutoStart", "Пакет не выбран")
            return
        }

        try {
            if (isAppInForeground(targetPackage)) {
                Log.i("AutoStart", "$targetPackage уже активно")
                return
            }

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

    private fun isAppInForeground(packageName: String): Boolean {
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 1000 * 60,
                now
            )
            if (stats != null && stats.isNotEmpty()) {
                val sorted = stats.sortedByDescending { it.lastTimeUsed }
                val topPackage = sorted.firstOrNull()?.packageName
                Log.i("AutoStart", "На переднем плане: $topPackage")
                return topPackage == packageName
            }
        } catch (e: Exception) {
            Log.e("AutoStart", "Ошибка UsageStats: ${e.message}")
        }
        return false
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
                "AutoStart",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Канал для сервиса автозапуска"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        monitorRunnable?.let { handler.removeCallbacks(it) }
        isStarted = false
        Log.i("AutoStart", "Сервис остановлен")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
