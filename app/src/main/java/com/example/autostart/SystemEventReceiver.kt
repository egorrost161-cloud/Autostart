package com.example.autostart

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build

class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        LogWriter.log("Событие: $action")

        when (action) {
            Intent.ACTION_POWER_CONNECTED -> {
                LogWriter.log("POWER_CONNECTED — зажигание включено")
                startService(context, forceCheck = false)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                LogWriter.log("POWER_DISCONNECTED — зажигание выключено")
            }
            Intent.ACTION_SCREEN_ON -> {
                LogWriter.log("SCREEN_ON — принудительная проверка")
                startService(context, forceCheck = true)
            }
            Intent.ACTION_SCREEN_OFF -> {
                LogWriter.log("SCREEN_OFF — экран выключен")
            }
            Intent.ACTION_USER_PRESENT -> {
                LogWriter.log("USER_PRESENT — разблокировка")
                startService(context, forceCheck = false)
            }
            WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(
                    WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN
                )
                if (state == WifiManager.WIFI_STATE_ENABLED) {
                    LogWriter.log("Wi-Fi включён")
                    startService(context, forceCheck = false)
                }
            }
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                LogWriter.log("Wi-Fi состояние изменилось")
                startService(context, forceCheck = false)
            }
            "android.net.conn.CONNECTIVITY_CHANGE" -> {
                LogWriter.log("Соединение изменилось")
                startService(context, forceCheck = false)
            }
            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                LogWriter.log("Авиарежим изменён")
            }
            Intent.ACTION_HEADSET_PLUG -> {
                LogWriter.log("Гарнитура подключена")
                startService(context, forceCheck = false)
            }
            Intent.ACTION_MEDIA_MOUNTED -> {
                LogWriter.log("SD-карта смонтирована")
                startService(context, forceCheck = false)
            }
            Intent.ACTION_MEDIA_EJECT -> LogWriter.log("SD-карта извлечена")
            Intent.ACTION_MEDIA_REMOVED -> LogWriter.log("SD-карта удалена")
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> LogWriter.log("Пакет удалён")
            "android.intent.action.PROVIDER_CHANGED" -> LogWriter.log("Провайдер изменён")
        }
    }

    private fun startService(context: Context, forceCheck: Boolean) {
        val svc = Intent(context, KeepAliveService::class.java)
        if (forceCheck) {
            svc.putExtra("FORCE_CHECK", true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }
}
