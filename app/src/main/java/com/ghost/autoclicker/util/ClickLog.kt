package com.ghost.autoclicker.util

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 统一日志工具
 * - 同时输出到 Logcat (tag=AutoClicker)
 * - 持久化到文件 /sdcard/Download/autoclicker.log
 * - 保留最近 500KB，自动滚动
 */
object ClickLog {
    private const val TAG = "AutoClicker"
    private const val MAX_SIZE = 512 * 1024L // 500KB

    private var logFile: File? = null
    private var handler: Handler? = null

    @Synchronized
    fun init(context: Context) {
        val dir = File(context.getExternalFilesDir(null), "logs")
        if (!dir.exists()) dir.mkdirs()
        logFile = File(dir, "click.log")

        val thread = HandlerThread("ClickLog").apply { start() }
        handler = Handler(thread.looper)

        w("=== ClickLog 初始化 ===")
        w("日志文件: ${logFile?.absolutePath}")
        i("设备: ${android.os.Build.MODEL}, SDK=${android.os.Build.VERSION.SDK_INT}")
        i("屏幕: ${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels}, density=${context.resources.displayMetrics.density}")
    }

    private fun ts(): String = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())

    private fun write(level: String, msg: String) {
        val line = "[${ts()}][$level] $msg\n"
        Log.println(when (level) {
            "V" -> Log.VERBOSE; "D" -> Log.DEBUG; "I" -> Log.INFO
            "W" -> Log.WARN; "E" -> Log.ERROR; else -> Log.INFO
        }, TAG, msg)
        handler?.post {
            try {
                val f = logFile ?: return@post
                if (f.length() > MAX_SIZE) {
                    val tmp = File(f.parent, "click.log.tmp")
                    tmp.writeText("")
                    // 保留后一半
                    val all = f.readText()
                    val cut = all.indexOf('\n', (all.length / 2).toInt())
                    if (cut > 0) tmp.appendText(all.substring(cut + 1))
                    tmp.renameTo(f)
                }
                f.appendText(line)
            } catch (_: Exception) {}
        }
    }

    fun v(msg: String) = write("V", msg)
    fun d(msg: String) = write("D", msg)
    fun i(msg: String) = write("I", msg)
    fun w(msg: String) = write("W", msg)
    fun e(msg: String, t: Throwable? = null) {
        write("E", if (t != null) "$msg | ${t.message}" else msg)
        t?.let { Log.e(TAG, msg, it) }
    }

    fun getLogContent(maxLines: Int = 500): String {
        val f = logFile ?: return "(日志未初始化)"
        return try {
            val lines = f.readText().lines().takeLast(maxLines)
            lines.joinToString("\n")
        } catch (_: Exception) { "(读取失败)" }
    }

    fun clearLog() {
        handler?.post { logFile?.writeText("") }
    }

    // 诊断数据（由其他模块写入）
    @JvmStatic
    var currentScreenOffset: Int = 0
        private set
    @JvmStatic
    val currentPositions = mutableListOf<LongArray>() // [pointId, savedX, savedY, actualX, actualY]

    fun updateDiagnostic(offset: Int, positions: Map<Long, Pair<Int, Int>>, savedPoints: List<*>) {
        currentScreenOffset = offset
        currentPositions.clear()
        savedPoints.forEach { pt ->
            try {
                val pointMap = pt as? Map<*, *> ?: return@forEach
                val id = (pointMap["id"] as? Number)?.toLong() ?: return@forEach
                val px = (pointMap["x"] as? Number)?.toInt() ?: return@forEach
                val py = (pointMap["y"] as? Number)?.toInt() ?: return@forEach
                val actual = positions[id]
                if (actual != null) {
                    currentPositions.add(longArrayOf(id, px.toLong(), py.toLong(), actual.first.toLong(), actual.second.toLong()))
                }
            } catch (_: Exception) {}
        }
    }
}
