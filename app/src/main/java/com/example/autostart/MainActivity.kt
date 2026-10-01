package com.example.autostart

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
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
        val currentMonitorEnabled = prefs.getBoolean("monitor_enabled", false)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        // ===== Шапка =====
        root.addView(TextView(this).apply {
            text = "AutoStart"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "Текущий выбор: ${current ?: "не выбран"}"
            textSize = 13f
            setPadding(0, 8, 0, 12)
        })

        // ===== Разрешения =====
        root.addView(sectionTitle("🔐 РАЗРЕШЕНИЯ (выдай все три)"))

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
                            "Включи переключатель и вернись",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity, "Уже выдано",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })

        root.addView(Button(this).apply {
            text = "📊 Статистика использования"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                Toast.makeText(
                    this@MainActivity,
                    "Найди AutoStart и включи доступ",
                    Toast.LENGTH_LONG
                ).show()
            }
        })

        root.addView(Button(this).apply {
            text = "🔋 Игнорировать оптимизацию батареи"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                        try {
                            val intent = Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                            Toast.makeText(
                                this@MainActivity,
                                "Подтверди в диалоге",
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: Exception) {
                            // Если прошивка не поддерживает диалог — открываем общий список
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            Toast.makeText(
                                this@MainActivity,
                                "Найди AutoStart в списке",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@MainActivity, "Уже выдано",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })

        // ===== СПОЙЛЕР: Тест =====
        val testContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }
        testContent.addView(TextView(this).apply {
            text = "Автозапуск после перезагрузки произойдёт сам.\n" +
                   "Эта кнопка — только для проверки без ребута."
            textSize = 12f
            setPadding(0, 4, 0, 8)
        })
        testContent.addView(Button(this).apply {
            text = "▶️ Запустить сервис сейчас"
            setOnClickListener {
                val i = Intent(this@MainActivity, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(i)
                } else {
                    startService(i)
                }
                Toast.makeText(
                    this@MainActivity,
                    "Сервис запущен, жди задержку",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
        root.addView(createSpoyler("🧪 Тест: запустить сервис сейчас", testContent))

        // ===== Отключить автозапуск =====
        root.addView(Button(this).apply {
            text = "🚫 Отключить автозапуск"
            setOnClickListener {
                prefs.edit().remove("target_package").commit()
                Toast.makeText(
                    this@MainActivity, "Отключено",
                    Toast.LENGTH_SHORT
                ).show()
                recreate()
            }
        })

        // ===== СПОЙЛЕР: Задержка =====
        val delayContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }
        val delayInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentDelay.toString())
            textSize = 18f
        }
        delayContent.addView(delayInput)

        val delayPresets = createPresetRow(
            presets = listOf(15L, 20L, 25L, 30L),
            onClick = { sec -> delayInput.setText(sec.toString()) }
        )
        delayPresets.visibility = View.GONE
        delayContent.addView(createSpoyler("⚙️ Быстрые пресеты", delayPresets))

        delayContent.addView(Button(this).apply {
            text = "💾 Сохранить"
            setOnClickListener {
                val v = delayInput.text.toString().toLongOrNull()
                if (v == null || v < 0 || v > 600) {
                    Toast.makeText(
                        this@MainActivity, "0–600 секунд",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                prefs.edit().putLong("delay_sec", v).commit()
                Toast.makeText(
                    this@MainActivity, "Задержка: ${v}с",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
        root.addView(createSpoyler("⏱ Задержка перед запуском", delayContent))

        // ===== СПОЙЛЕР: Монитор =====
        val monitorContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }
        monitorContent.addView(CheckBox(this).apply {
            text = "Следить за приложением и возвращать, если упало"
            textSize = 14f
            isChecked = currentMonitorEnabled
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("monitor_enabled", checked).commit()
                Toast.makeText(
                    this@MainActivity,
                    if (checked) "Монитор включён" else "Монитор выключен",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        val intervalInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentInterval.toString())
            textSize = 18f
        }
        monitorContent.addView(intervalInput)

        val intervalPresets = createPresetRow(
            presets = listOf(5L, 8L, 15L, 30L),
            onClick = { sec -> intervalInput.setText(sec.toString()) }
        )
        intervalPresets.visibility = View.GONE
        monitorContent.addView(createSpoyler("⚙️ Быстрые пресеты", intervalPresets))

        monitorContent.addView(Button(this).apply {
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
                prefs.edit().putLong("monitor_interval_ms", v * 1000L).commit()
                Toast.makeText(
                    this@MainActivity, "Интервал: ${v}с",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
        root.addView(createSpoyler("🔄 Постоянный монитор", monitorContent))

        // ===== Список приложений =====
        root.addView(sectionTitle("📱 ВЫБЕРИ ПРИЛОЖЕНИЕ"))

        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        for (app in apps) {
            val label = pm.getApplicationLabel(app).toString()
            val pkg = app.packageName

            if (pkg == packageName) continue

            val hasLaunch = pm.getLaunchIntentForPackage(pkg) != null
            if (!hasLaunch) continue

            val icon: Drawable = pm.getApplicationIcon(app)
            val isSelected = pkg == current

            listLayout.addView(createAppRow(label, icon, isSelected) {
                val saved = prefs.getString("target_package", null)
                if (saved == pkg) {
                    prefs.edit().remove("target_package").commit()
                    Toast.makeText(
                        this@MainActivity, "Автозапуск отключён",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    prefs.edit().putString("target_package", pkg).commit()
                    Toast.makeText(
                        this@MainActivity, "Выбрано: $label",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                recreate()
            })
        }

        root.addView(listLayout)

        val outerScroll = ScrollView(this)
        outerScroll.addView(root)
        setContentView(outerScroll)
    }

    // ===== ХЕЛПЕРЫ =====

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setPadding(0, 30, 0, 10)
        }
    }

    private fun createAppRow(
        label: String,
        icon: Drawable,
        isSelected: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 20, 20, 20)
            isClickable = true
            isFocusable = true

            if (isSelected) {
                setBackgroundColor(Color.parseColor("#4CAF50"))
            } else {
                setBackgroundColor(Color.parseColor("#EEEEEE"))
            }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 6, 0, 6)
            }

            addView(ImageView(this@MainActivity).apply {
                setImageDrawable(icon)
                layoutParams = LinearLayout.LayoutParams(100, 100).apply {
                    setMargins(0, 0, 20, 0)
                }
            })

            addView(TextView(this@MainActivity).apply {
                text = if (isSelected) "✅ $label" else label
                textSize = 16f
                setTextColor(Color.parseColor("#222222"))
            })

            setOnClickListener { onClick() }
        }
    }

    private fun createPresetRow(
        presets: List<Long>,
        onClick: (Long) -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
            presets.forEach { sec ->
                val btn = Button(this@MainActivity).apply {
                    text = "${sec}с"
                    textSize = 13f
                    setOnClickListener { onClick(sec) }
                }
                btn.layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                addView(btn)
            }
        }
    }

    private fun createSpoyler(
        title: String,
        content: View
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)

            val toggle = Button(this@MainActivity).apply {
                text = "▼ $title"
                textSize = 15f
                setOnClickListener {
                    if (content.visibility == View.GONE) {
                        content.visibility = View.VISIBLE
                        text = "▲ $title"
                    } else {
                        content.visibility = View.GONE
                        text = "▼ $title"
                    }
                }
            }
            addView(toggle)
            addView(content)
        }
    }
}
