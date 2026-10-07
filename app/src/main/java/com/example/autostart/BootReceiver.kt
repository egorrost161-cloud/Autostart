package com.example.autostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        // Задержка после загрузки (мс). 20000 = 20 сек.
        private const val BOOT_DELAY_MS = 20000L
        // Задержка между музыкой и лаунчером (мс). 500 = 0.5 сек.
        private const val SWITCH_DELAY_MS = 500L

        // Имена пакетов
        private const val MUSIC_PACKAGE = "ru.yandex.music"
        private const val LAUNCHER_PACKAGE = "com.launcheravto.lalauncher3"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.i("AutoStart", "BOOT_COMPLETED получен, ждём ${BOOT_DELAY_MS}мс")
            val handler = Handler(Looper.getMainLooper())

            handler.postDelayed({
                launchMusic(context, handler)
            }, BOOT_DELAY_MS)
        }
    }

    private fun launchMusic(context: Context, handler: Handler) {
        // Шаг 1: Запускаем Яндекс Музыку
        try {
            val musicIntent = context.packageManager
                .getLaunchIntentForPackage(MUSIC_PACKAGE)

            if (musicIntent != null) {
                musicIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(musicIntent)
                Log.i("AutoStart", "Яндекс Музыка запущена")
            } else {
                Log.e("AutoStart", "Яндекс Музыка не найдена")
                launchLauncher(context) // если музыки нет — сразу лаунчер
                return
            }
        } catch (e: Exception) {
            Log.e("AutoStart", "Ошибка запуска музыки: ${e.message}")
        }

        // Шаг 2: Через задержку запускаем лаунчер — он перекроет музыку
        handler.postDelayed({
            launchLauncher(context)
        }, SWITCH_DELAY_MS)
    }

    private fun launchLauncher(context: Context) {
        try {
            val launcherIntent = context.packageManager
                .getLaunchIntentForPackage(LAUNCHER_PACKAGE)

            if (launcherIntent != null) {
                launcherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launcherIntent)
                Log.i("AutoStart", "Лаунчер запущен")
            } else {
                Log.e("AutoStart", "Лаунчер не найден")
            }
        } catch (e: Exception) {
            Log.e("AutoStart", "Ошибка запуска лаунчера: ${e.message}")
        }
    }
}
