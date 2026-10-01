package com.example.autostart

import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogWriter {

    private const val TAG = "AutoStart"
    private const val LOG_DIR = "AutoStartLog"
    private const val LOG_FILE = "autostart_log.txt"
    private const val MAX_SIZE_BYTES = 500 * 1024L // 500 KB

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun log(message: String) {
        // Дублируем в системный лог
        Log.i(TAG, message)

        try {
            val dir = File(Environment.getExternalStorageDirectory(), LOG_DIR)
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, LOG_FILE)

            // Если файл больше 500 КБ — очищаем (оставляем последние 100 строк)
            if (file.exists() && file.length() > MAX_SIZE_BYTES) {
                val lines = file.readLines()
                val last = lines.takeLast(100)
                file.writeText(last.joinToString("\n") + "\n")
            }

            val timestamp = dateFormat.format(Date())
            file.appendText("$timestamp  $message\n")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка записи лога: ${e.message}")
        }
    }

    fun getLogFile(): File {
        val dir = File(Environment.getExternalStorageDirectory(), LOG_DIR)
        return File(dir, LOG_FILE)
    }

    fun clearLog() {
        try {
            val file = getLogFile()
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка очистки лога: ${e.message}")
        }
    }
}
