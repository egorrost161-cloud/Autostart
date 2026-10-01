package com.example.autostart

import android.app.AppOpsManager
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogWriter.init(this)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val setupDone = prefs.getBoolean("setup_done", false)
        val allGranted = areAllPermissionsGranted()

        // П3: если разрешения слетели — снова показываем экран приветствия
        if (allGranted) {
            prefs.edit().putBoolean("setup_done", true).commit()
        } else if (setupDone) {
            // Разрешения были выданы, но сейчас пропали
            prefs.edit().putBoolean("setup_done", false).commit()
        }

        if (!prefs.getBoolean("setup_done", false)) {
            showSetupScreen(prefs)
        } else {
            showMainScreen(prefs)
        }
    }

    // ============================================================
    // ЭКРАН ПРИВЕТСТВИЯ (первый запуск или если разрешения слетели)
    // ============================================================

    private fun showSetupScreen(prefs: android.content.SharedPreferences) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        root.addView(TextView(this).apply {
            text = "AutoStart"
            textSize = 26f
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "\nДля работы нужно 3 разрешения.\n" +
                   "Нажми на каждое — откроется системная настройка.\n"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        })

        // Кнопка 1: Наложение
        val btnOverlay = Button(this).apply {
            text = overlayLabel()
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.canDrawOverlays(this@MainActivity)) {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    } else {
                        Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        root.addView(btnOverlay)

        // Кнопка 2: Статистика
        val btnStats = Button(this).apply {
            text = usageStatsLabel()
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                Toast.makeText(
                    this@MainActivity,
                    "Найди AutoStart и включи доступ",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        root.addView(btnStats)

        // Кнопка 3: Батарея
        val btnBattery = Button(this).apply {
            text = batteryLabel()
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                        try {
                            startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        } catch (e: Exception) {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Уже выдано", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        root.addView(btnBattery)

        // Кнопка «Продолжить» — не блокируется (П2 = Нет)
        root.addView(Button(this).apply {
            text = "ПРОДОЛЖИТЬ"
            setPadding(0, 40, 0, 0)
            setOnClickListener {
                // Сохраняем статус «setup_done» только если всё выдано
                if (areAllPermissionsGranted()) {
                    prefs.edit().putBoolean("setup_done", true).commit()
                }
                recreate()
            }
        })

        // Кнопка «Проверить снова» — обновить статусы
        root.addView(Button(this).apply {
            text = "Обновить статус"
            setOnClickListener { recreate() }
        })

        setContentView(root)
    }

    private fun overlayLabel(): String {
        val granted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Settings.canDrawOverlays(this)
        return if (granted) "✅ Наложение поверх окон" else "❌ Наложение поверх окон"
    }

    private fun usageStatsLabel(): String {
        return if (hasUsageStatsPermission()) "✅ Статистика использования"
        else "❌ Статистика использования"
    }

    private fun batteryLabel(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "✅ Игнор батареи"
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (pm.isIgnoringBatteryOptimizations(packageName))
            "✅ Игнор батареи" else "❌ Игнор батареи"
    }

    // ============================================================
    // ГЛАВНЫЙ ЭКРАН (когда все разрешения выданы)
    // ============================================================

    private fun showMainScreen(prefs: android.content.SharedPreferences) {
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

        // Шапка
        root.addView(TextView(this).apply {
            text = "AutoStart"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "Текущий выбор: ${current ?: "не выбран"}"
            textSize = 13f
            setPadding(0, 8, 0, 12)
        })

        // П1 = B: баннер «не все разрешения выданы»
        if (!areAllPermissionsGranted()) {
            val banner = Button(this).apply {
                text = "⚠️ Не все разрешения выданы — нажми сюда"
                setBackgroundColor(Color.parseColor("#FF9800"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    prefs.edit().putBoolean("setup_done", false).commit()
                    recreate()
                }
            }
            root.addView(banner)
            root.addView(TextView(this).apply {
                text = "Без разрешений автозапуск может не работать.\n"
                textSize = 12f
                setPadding(0, 8, 0, 12)
            })
        }

        // Тест
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
            text = "Запустить сервис сейчас"
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
        root.addView(createSpoyler("ТЕСТ: запустить сервис сейчас", testContent))

        // Логи
        val logContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }
        logContent.addView(TextView(this).apply {
            text = "Лог сохраняется во внутренней памяти приложения."
            textSize = 12f
            setPadding(0, 4, 0, 8)
        })
        logContent.addView(Button(this).apply {
            text = "Показать последние 30 строк"
            setOnClickListener {
                val text = LogWriter.readLog()
                if (text.isEmpty() || text == "Лог пуст — событий ещё не было") {
                    Toast.makeText(this@MainActivity, "Лог пуст", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val lines = text.lines().takeLast(30)
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Последние 30 строк")
                    .setMessage(lines.joinToString("\n"))
                    .setPositiveButton("OK", null)
                    .show()
            }
        })
        logContent.addView(Button(this).apply {
            text = "Очистить лог"
            setOnClickListener {
                LogWriter.clearLog()
                Toast.makeText(this@MainActivity, "Лог очищен", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(createSpoyler("ЛОГИ", logContent))

        // Отключить автозапуск
        root.addView(Button(this).apply {
            text = "Отключить автозапуск"
            setOnClickListener {
                prefs.edit().remove("target_package").commit()
                Toast.makeText(this@MainActivity, "Отключено", Toast.LENGTH_SHORT).show()
                recreate()
            }
        })

        // Задержка
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
        delayContent.addView(createSpoyler("Быстрые пресеты", delayPresets))

        delayContent.addView(Button(this).apply {
            text = "Сохранить"
            setOnClickListener {
                val v = delayInput.text.toString().toLongOrNull()
                if (v == null || v < 0 || v > 600) {
                    Toast.makeText(this@MainActivity, "0-600 секунд", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                prefs.edit().putLong("delay_sec", v).commit()
                Toast.makeText(this@MainActivity, "Задержка: ${v}с", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(createSpoyler("ЗАДЕРЖКА ПЕРЕД ЗАПУСКОМ", delayContent))

        // Монитор
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
            presets = listOf(15L, 30L, 60L, 120L),
            onClick = { sec -> intervalInput.setText(sec.toString()) }
        )
        intervalPresets.visibility = View.GONE
        monitorContent.addView(createSpoyler("Быстрые пресеты", intervalPresets))

        monitorContent.addView(Button(this).apply {
            text = "Сохранить интервал"
            setOnClickListener {
                val v = intervalInput.text.toString().toLongOrNull()
                if (v == null || v < 5 || v > 600) {
                    Toast.makeText(this@MainActivity, "От 5 до 600 секунд", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                prefs.edit().putLong("monitor_interval_ms", v * 1000L).commit()
                Toast.makeText(this@MainActivity, "Интервал: ${v}с", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(createSpoyler("ПОСТОЯННЫЙ МОНИТОР", monitorContent))

        // Список приложений
        root.addView(sectionTitle("ВЫБЕРИ ПРИЛОЖЕНИЕ"))

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
                    Toast.makeText(this@MainActivity, "Автозапуск отключён", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.edit().putString("target_package", pkg).commit()
                    Toast.makeText(this@MainActivity, "Выбрано: $label", Toast.LENGTH_SHORT).show()
                }
                recreate()
            })
        }

        root.addView(listLayout)

        val outerScroll = ScrollView(this)
        outerScroll.addView(root)
        setContentView(outerScroll)
    }

    // ============================================================
    // ХЕЛПЕРЫ
    // ============================================================

    private fun areAllPermissionsGranted(): Boolean {
        val overlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(this)
        val stats = hasUsageStatsPermission()
        val battery = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            true
        } else {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(packageName)
        }
        return overlay && stats && battery
    }

    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

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
                text = if (isSelected) "[OK] $label" else label
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
