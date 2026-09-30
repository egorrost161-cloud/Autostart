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
            Intent.ACTION_POWER_CONNECTED -> startService(context)
            Intent.ACTION_SCREEN_ON -> startService(context)
            Intent.ACTION_USER_PRESENT -> startService(context)
            WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(
                    WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN
                )
                if (state == WifiManager.WIFI_STATE_ENABLED) {
                    startService(context)
                }
            }
            Intent.ACTION_HEADSET_PLUG -> startService(context)
            Intent.ACTION_MEDIA_MOUNTED -> startService(context)
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
