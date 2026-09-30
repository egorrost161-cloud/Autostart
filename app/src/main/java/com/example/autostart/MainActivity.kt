package com.example.autostart

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
        }

        val header = TextView(this).apply {
            text = "Автозапуск\n\nТекущий выбор: ${current ?: "не выбран"}\n\n" +
                   "Тапни приложение, чтобы выбрать. Тапни ещё раз — чтобы снять выбор."
            textSize = 16f
        }
        root.addView(header)

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

        setContentView(root)
    }
}
