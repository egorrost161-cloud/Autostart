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
        val currentDelay = prefs.getLong("delay_sec", BootReceiver.DEFAULT_DELAY_SEC)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
        }

        // === Информация ===
        val header = TextView(this).apply {
            text = "Автозапуск\n\nТекущий выбор: ${current ?: "не выбран"}"
            textSize = 16f
        }
        root.addView(header)

        // === Кнопка разрешения наложения поверх окон ===
        val btnOverlay = Button(this).apply {
            text = "🔓 Разрешить наложение поверх окон"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.canDrawOverlays(this@MainActivity)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                        Toast.makeText(
                            this@MainActivity,
                            "Включите переключатель и вернитесь",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Разрешение уже выдано",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Не требуется на этой версии Android",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        root.addView(btnOverlay)

        // === Задержка ===
        val delayLabel = TextView(this).apply {
            text = "\n⏱ Задержка после загрузки (секунд):"
            textSize = 16f
            setPadding(0, 30, 0, 10)
        }
        root.addView(delayLabel)

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
        val presets = listOf(5L, 10L, 15L, 30L)
        for (sec in presets) {
            val btn = Button(this).apply {
                text = "${sec}с"
                textSize = 13f
                setOnClickListener {
                    delayInput.setText(sec.toString())
                }
            }
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            btn.layoutParams = params
            presetsLayout.addView(btn)
        }
        root.addView(presetsLayout)

        val btnSaveDelay = Button(this).apply {
            text = "💾 Сохранить задержку"
            setOnClickListener {
                val value = delayInput.text.toString().toLongOrNull()
                if (value == null || value < 0 || value > 600) {
                    Toast.makeText(
                        this@MainActivity,
                        "Введите число от 0 до 600",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                prefs.edit().putLong("delay_sec", value).apply()
                Toast.makeText(
                    this@MainActivity,
                    "Задержка сохранена: ${value}с",
                    Toast.LENGTH_SHORT
                ).show()
                recreate()
            }
        }
        root.addView(btnSaveDelay)

        // === Список приложений ===
        val listLabel = TextView(this).apply {
            text = "\n📱 Выберите приложение для автозапуска:"
            textSize = 16f
            setPadding(0, 30, 0, 10)
        }
        root.addView(listLabel)

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

            val btn = Button(this).apply {
                text = if (pkg == current) "✅ $label\n$pkg" else "$label\n$pkg"
                textSize = 14f
                setOnClickListener {
                    val saved = prefs.getString("target_package", null)
                    if (saved == pkg) {
                        prefs.edit().remove("target_package").apply()
                        Toast.makeText(this@MainActivity, "Автозапуск отключён", Toast.LENGTH_SHORT).show()
                    } else {
                        prefs.edit().putString("target_package", pkg).apply()
                        Toast.makeText(this@MainActivity, "Выбрано: $label", Toast.LENGTH_SHORT).show()
                    }
                    recreate()
                }
            }
            listLayout.addView(btn)
        }

        scroll.addView(listLayout)
        root.addView(scroll)

        // === Внизу ===
        val btnClear = Button(this).apply {
            text = "🚫 Отключить автозапуск"
            setOnClickListener {
                prefs.edit().remove("target_package").apply()
                Toast.makeText(this@MainActivity, "Автозапуск отключён", Toast.LENGTH_SHORT).show()
                recreate()
            }
        }
        root.addView(btnClear)

        val btnTest = Button(this).apply {
            text = "▶️ Запустить сейчас (проверка)"
            setOnClickListener {
                val pkg = prefs.getString("target_package", null)
                if (pkg.isNullOrEmpty()) {
                    Toast.makeText(this@MainActivity, "Сначала выберите приложение", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val i = pm.getLaunchIntentForPackage(pkg)
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(i)
                } else {
                    Toast.makeText(this@MainActivity, "Не удалось запустить", Toast.LENGTH_SHORT).show()
                }
            }
        }
        root.addView(btnTest)

        val outerScroll = ScrollView(this)
        outerScroll.addView(root)
        setContentView(outerScroll)
    }
}
