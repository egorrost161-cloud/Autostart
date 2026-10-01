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
import androidx.core.app.NotificationCompat

class KeepAliveService : Service() {

    companion object {
        const val CHANNEL_ID = "autostart_keepalive"
        const val NOTIF_ID = 101
        const val DEFAULT_MONITOR_INTERVAL_MS = 30000L
        const val DEFAULT_DELAY_SEC = 15L
        const val ACTIVITY_WINDOW_MS = 30 * 60 * 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var monitorRunnable: Runnable? = null
    private var isStarted = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        LogWriter.log("=== KeepAliveService onCreate ===")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIF_ID, notification)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val monitorEnabled = prefs.getBoolean("monitor_enabled", false)
        val targetPackage = prefs.getString("target_package", "не выбран")

        LogWriter.log("Сервис onStartCommand: target=$targetPackage, monitor=$monitorEnabled")

        if (!monitorEnabled && monitorRunnable != null) {
            LogWriter.log("Монитор выключен — убиваем цикл")
            handler.removeCallbacks(monitorRunnable!!)
            monitorRunnable = null
        }

        val forceCheck = intent?.getBooleanExtra("FORCE_CHECK", false) ?: false
        if (forceCheck) {
            LogWriter.log("FORCE_CHECK — принудительная проверка")
            launchTarget()
            return START_STICKY
        }

        if (isStarted) {
            LogWriter.log("Сервис уже работает")
            if (monitorEnabled && monitorRunnable == null) {
                startMonitor()
            }
            return START_STICKY
        }
        isStarted = true

        var delaySec = prefs.getLong("delay_sec", DEFAULT_DELAY_SEC)
        if (delaySec <= 0) delaySec = DEFAULT_DELAY_SEC
        val delayMs = delaySec * 1000L

        LogWriter.log("Сервис запущен, ждём ${delaySec}с, монитор=$monitorEnabled")

        handler.postDelayed({
            launchTarget()
            if (monitorEnabled) startMonitor()
        }, delayMs)

        return START_STICKY
    }

    private fun startMonitor() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val intervalMs = prefs.getLong("monitor_interval_ms", DEFAULT_MONITOR_INTERVAL_MS)

        monitorRunnable = object : Runnable {
            override fun run() {
                val enabled = prefs.getBoolean("monitor_enabled", false)
                if (!enabled) {
                    LogWriter.log("Монитор отключён — выходим")
                    monitorRunnable = null
                    return
                }
                launchTarget()
                handler.postDelayed(this, intervalMs)
            }
        }
        handler.postDelayed(monitorRunnable!!, intervalMs)
        LogWriter.log("Монитор запущен, интервал ${intervalMs}мс")
    }

    private fun launchTarget() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val targetPackage = prefs.getString("target_package", null)

        if (targetPackage.isNullOrEmpty()) {
            LogWriter.log("Пакет не выбран — выход")
            return
        }

        try {
            if (wasActiveRecently(targetPackage)) {
                LogWriter.log("$targetPackage активен недавно — не трогаем")
                return
            }

            val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                LogWriter.log("✓ Запущен $targetPackage")
            } else {
                LogWriter.log("✗ Не найдена точка входа для $targetPackage")
            }
        } catch (e: Exception) {
            LogWriter.log("✗ ОШИБКА запуска: ${e.message}")
        }
    }

    private fun wasActiveRecently(packageName: String): Boolean {
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val begin = now - ACTIVITY_WINDOW_MS

            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, now)
            if (stats == null || stats.isEmpty()) return false

            for (stat in stats) {
                if (stat.packageName == packageName) {
                    if (now - stat.lastTimeUsed < ACTIVITY_WINDOW_MS) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            LogWriter.log("Ошибка UsageStats: ${e.message}")
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
        monitorRunnable = null
        isStarted = false
        LogWriter.log("=== Сервис остановлен ===")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
