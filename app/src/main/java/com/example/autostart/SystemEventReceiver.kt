package com.example.autostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        Log.i("AutoStart", "Событие: $action")

        when (action) {
            // ===== Питание (зажигание) =====
            Intent.ACTION_POWER_CONNECTED -> startService(context)
            Intent.ACTION_POWER_DISCONNECTED -> {
                // Не запускаем — но лог полезен
                Log.i("AutoStart", "Питание отключено")
            }

            // ===== Экран =====
            Intent.ACTION_SCREEN_ON -> startService(context)
            Intent.ACTION_SCREEN_OFF -> {
                Log.i("AutoStart", "Экран выключен")
            }
            Intent.ACTION_USER_PRESENT -> startService(context)

            // ===== Wi-Fi / сеть =====
            WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(
                    WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN
                )
                if (state == WifiManager.WIFI_STATE_ENABLED) {
                    startService(context)
                }
            }
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> startService(context)
            "android.net.conn.CONNECTIVITY_CHANGE" -> startService(context)
            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                Log.i("AutoStart", "Авиарежим изменён")
            }

            // ===== Периферия =====
            Intent.ACTION_HEADSET_PLUG -> startService(context)

            // ===== Медиа =====
            Intent.ACTION_MEDIA_MOUNTED -> startService(context)
            Intent.ACTION_MEDIA_EJECT -> {
                Log.i("AutoStart", "SD-карта извлечена")
            }
            Intent.ACTION_MEDIA_REMOVED -> {
                Log.i("AutoStart", "SD-карта извлечена (removed)")
            }

            // ===== Изменения в системе =====
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                Log.i("AutoStart", "Приложение удалено")
            }
            "android.intent.action.PROVIDER_CHANGED" -> {
                Log.i("AutoStart", "Provider changed")
            }
        }
    }

    private fun startService(context: Context) {
        val svc = Intent(context, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }
}
