package com.example.autostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        const val DEFAULT_DELAY_SEC = 5L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON") {

            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val delaySec = prefs.getLong("delay_sec", DEFAULT_DELAY_SEC)
            val delayMs = delaySec * 1000L

            Log.i("AutoStart", "BOOT_COMPLETED получен, задержка: ${delaySec}с")

            Handler(Looper.getMainLooper()).postDelayed({
                launchTarget(context)
            }, delayMs)
        }
    }

    private fun launchTarget(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val targetPackage = prefs.getString("target_package", null)

        if (targetPackage.isNullOrEmpty()) {
            Log.w("AutoStart", "Пакет не выбран, запуск пропущен")
            return
        }

        try {
            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(targetPackage)

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Log.i("AutoStart", "Запущен $targetPackage")
            } else {
                Log.e("AutoStart", "Не найдена точка входа для $targetPackage")
            }
        } catch (e: Exception) {
            Log.e("AutoStart", "Ошибка запуска: ${e.message}")
        }
    }
}
