package com.example.autostart

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val current = prefs.getString("target_package", null)
        val currentDelay = prefs.getLong("delay_sec", KeepAliveService.DEFAULT_DELAY_SEC)
        val currentInterval = prefs.getLong(
            "monitor_interval_ms",
            KeepAliveService.DEFAULT_MONITOR_INTERVAL_MS
        ) / 1000

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
        }

        root.addView(TextView(this).apply {
            text = "AutoStart\n\nТекущий выбор: ${current ?: "не выбран"}"
            textSize = 16f
        })

        // === Разрешения ===
        root.addView(TextView(this).apply {
            text = "\n🔐 Разрешения:"
            textSize = 16f
            setPadding(0, 30, 0, 10)
        })

        root.addView(Button(this).apply {
            text = "🔓 Наложение поверх окон"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.canDrawOverlays(this@MainActivity)) {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                        Toast.makeText(
                            this@MainActivity,
                            "Включите переключатель и вернитесь",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Уже выдано",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })

        root.addView(Button(this).apply {
            text = "📊 Статистика использования"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    Toast.makeText(
                        this@MainActivity,
                        "Найдите AutoStart и включите",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })

        // === Задержка ===
        root.addView(TextView(this).apply {
            text = "\n⏱ Задержка перед запуском (сек):"
            textSize = 16f
            setPadding(0, 30, 0, 10)
        })

        val delayInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentDelay.toString())
            textSize = 18f
        }
        root.addView(delayInput)

        val presetsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
        }
        listOf(5L, 10L, 15L, 30L).forEach { sec ->
            val btn = Button(this).apply {
                text = "${sec}с"
                textSize = 13f
                setOnClickListener { delayInput.setText(sec.toString()) }
            }
            btn.layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            presetsLayout.addView(btn)
        }
        root.addView(presetsLayout)

        root.addView(Button(this).apply {
            text = "💾 Сохранить задержку"
            setOnClickListener {
                val v = delayInput.text.toString().toLongOrNull()
                if (v == null || v < 0 || v > 600) {
                    Toast.makeText(
                        this@MainActivity, "0–600 секунд",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                prefs.edit().putLong("delay_sec", v).apply()
                Toast.makeText(
                    this@MainActivity, "Задержка: ${v}с",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        // === Интервал монитора ===
        root.addView(TextView(this).apply {
            text = "\n🔄 Интервал проверки монитора (сек):"
            textSize = 16f
            setPadding(0, 30, 0, 10)
        })

        val intervalInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentInterval.toString())
            textSize = 18f
        }
        root.addView(intervalInput)

        root.addView(Button(this).apply {
            text = "💾 Сохранить интервал"
            setOnClickListener {
                val v = intervalInput.text.toString().toLongOrNull()
                if (v == null || v < 3 || v > 300) {
                    Toast.makeText(
                        this@MainActivity, "От 3 до 300 секунд",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                prefs.edit().putLong("monitor_interval_ms", v * 1000L).apply()
                Toast.makeText(
                    this@MainActivity, "Интервал: ${v}с",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        // === Список приложений ===
        root.addView(TextView(this).apply {
            text = "\n📱 Выберите приложение:"
            textSize = 16f
            setPadding(0, 30, 0, 10)
        })

        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        val scroll = ScrollView(this)
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString()
            val pkg = app.packageName

            if (pkg == packageName) continue

            val hasLaunch = pm.getLaunchIntentForPackage(pkg) != null
            if (!hasLaunch) continue

            listLayout.addView(Button(this).apply {
                text = if (pkg == current) "✅ $label\n$pkg" else "$label\n$pkg"
                textSize = 14f
                setOnClickListener {
                    val saved = prefs.getString("target_package", null)
                    if (saved == pkg) {
                        prefs.edit().remove("target_package").apply()
                        Toast.makeText(
                            this@MainActivity, "Автозапуск отключён",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        prefs.edit().putString("target_package", pkg).apply()
                        Toast.makeText(
                            this@MainActivity, "Выбрано: $label",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    recreate()
                }
            })
        }

        scroll.addView(listLayout)
        root.addView(scroll)

        root.addView(Button(this).apply {
            text = "🚫 Отключить автозапуск"
            setOnClickListener {
                prefs.edit().remove("target_package").apply()
                Toast.makeText(
                    this@MainActivity, "Отключено",
                    Toast.LENGTH_SHORT
                ).show()
                recreate()
            }
        })

        root.addView(Button(this).apply {
            text = "▶️ Запустить сервис сейчас"
            setOnClickListener {
                val i = Intent(this@MainActivity, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(i)
                } else {
                    startService(i)
                }
                Toast.makeText(
                    this@MainActivity, "Сервис запущен",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        val outerScroll = ScrollView(this)
        outerScroll.addView(root)
        setContentView(outerScroll)
    }
}
