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
        const val DEFAULT_MONITOR_INTERVAL_MS = 30000L  // 30 сек между проверками
        const val DEFAULT_DELAY_SEC = 15L
        // Окно «активности»: если приложение было на переднем плане за это время — не трогаем
        const val ACTIVITY_WINDOW_MS = 30 * 60 * 1000L  // 30 минут
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

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val monitorEnabled = prefs.getBoolean("monitor_enabled", false)

        // Если монитор выключен — убиваем цикл немедленно
        if (!monitorEnabled && monitorRunnable != null) {
            Log.i("AutoStart", "Монитор выключен — убиваем цикл")
            handler.removeCallbacks(monitorRunnable!!)
            monitorRunnable = null
        }

        // Спецрежим: если intent с флагом FORCE_CHECK — сразу проверяем и выходим
        val forceCheck = intent?.getBooleanExtra("FORCE_CHECK", false) ?: false
        if (forceCheck) {
            Log.i("AutoStart", "Принудительная проверка (SCREEN_ON)")
            launchTarget()
            return START_STICKY
        }

        if (isStarted) {
            Log.i("AutoStart", "Сервис уже работает")
            if (monitorEnabled && monitorRunnable == null) {
                startMonitor()
            }
            return START_STICKY
        }
        isStarted = true

        var delaySec = prefs.getLong("delay_sec", DEFAULT_DELAY_SEC)
        if (delaySec <= 0) delaySec = DEFAULT_DELAY_SEC  // защита от нуля
        val delayMs = delaySec * 1000L

        Log.i("AutoStart", "Сервис запущен, ждём ${delaySec}с, монитор=$monitorEnabled")

        handler.postDelayed({
            launchTarget()
            if (monitorEnabled) {
                startMonitor()
            }
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
                    Log.i("AutoStart", "Монитор отключён — выходим из цикла")
                    monitorRunnable = null
                    return
                }
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
            // ГЛАВНОЕ: если приложение активно за последние 30 минут — не трогаем
            if (wasActiveRecently(targetPackage)) {
                Log.i("AutoStart", "$targetPackage был активен недавно — не трогаем")
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

    /**
     * Проверяет, было ли приложение активно за последние ACTIVITY_WINDOW_MS миллисекунд.
     * Если да — значит оно живо (свёрнуто), не трогаем.
     * Если нет — возможно, убито, запускаем.
     */
    private fun wasActiveRecently(packageName: String): Boolean {
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val begin = now - ACTIVITY_WINDOW_MS

            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, now)
            if (stats == null || stats.isEmpty()) return false

            for (stat in stats) {
                if (stat.packageName == packageName) {
                    // lastTimeUsed — когда приложение последний раз было на переднем плане
                    if (now - stat.lastTimeUsed < ACTIVITY_WINDOW_MS) {
                        Log.i("AutoStart", "$packageName активен ${(now - stat.lastTimeUsed)/1000}с назад")
                        return true
                    }
                }
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
        monitorRunnable = null
        isStarted = false
        Log.i("AutoStart", "Сервис остановлен")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
