package com.ghost.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.ghost.autoclicker.model.ClickMode
import com.ghost.autoclicker.model.ClickPoint
import com.ghost.autoclicker.model.GlobalConfig
import kotlin.random.Random

class ClickAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ClickAccessibilityService? = null
            private set

        var clickPoints = mutableListOf<ClickPoint>()
        var globalConfig = GlobalConfig()
        var onStatusChanged: ((Boolean) -> Unit)? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isClicking = false
    private var clickRunnable: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        onStatusChanged?.invoke(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        stopClicking()
        instance = null
        onStatusChanged?.invoke(false)
    }

    fun startClicking() {
        if (isClicking) return
        if (!globalConfig.isRunning) return
        isClicking = true

        val enabledPoints = clickPoints.filter { it.enabled }
        if (enabledPoints.isEmpty()) {
            isClicking = false
            return
        }

        scheduleNext(enabledPoints, 0)
    }

    fun stopClicking() {
        isClicking = false
        clickRunnable?.let { handler.removeCallbacks(it) }
        clickRunnable = null
    }

    fun pauseClicking() {
        clickRunnable?.let { handler.removeCallbacks(it) }
        clickRunnable = null
        isClicking = false
    }

    fun updateRunning() {
        if (globalConfig.isRunning && !isClicking) {
            startClicking()
        } else if (!globalConfig.isRunning && isClicking) {
            stopClicking()
        }
    }

    private fun scheduleNext(points: List<ClickPoint>, pointIndex: Int) {
        if (!isClicking || !globalConfig.isRunning) return

        val point = points[pointIndex % points.size]

        // 检查是否超过最大重复次数
        if (point.maxRepeat > 0 && point.repeatCount >= point.maxRepeat) {
            val nextIdx = (pointIndex + 1) % points.size
            points[nextIdx].repeatCount = 0
            scheduleNext(points, nextIdx)
            return
        }

        performClick(point)

        clickRunnable = object : Runnable {
            override fun run() {
                if (!isClicking || !globalConfig.isRunning) return
                point.repeatCount++
                val nextIdx = (pointIndex + 1) % points.size
                if (points.size > 1 && nextIdx == 0) {
                    points.forEach { it.repeatCount = 0 }
                }
                scheduleNext(points, nextIdx)
            }
        }

        // 计算延迟 = 基础延迟 + 随机偏移
        val delay = point.delayMs + if (point.delayRandomMs > 0) {
            Random.nextLong(-point.delayRandomMs, point.delayRandomMs + 1)
        } else 0L

        handler.postDelayed(clickRunnable!!, delay.coerceAtLeast(10))
    }

    private fun performClick(point: ClickPoint) {
        // 位置偏移
        val offsetX = if (point.posOffsetPx > 0) {
            Random.nextInt(-point.posOffsetPx, point.posOffsetPx + 1)
        } else 0
        val offsetY = if (point.posOffsetPx > 0) {
            Random.nextInt(-point.posOffsetPx, point.posOffsetPx + 1)
        } else 0

        val finalX = point.x + offsetX.toFloat()
        val finalY = point.y + offsetY.toFloat()

        when (point.clickMode) {
            ClickMode.SINGLE -> click(finalX, finalY)
            ClickMode.LONG_PRESS -> longPress(finalX, finalY, point.longPressDurationMs)
            ClickMode.DOUBLE -> {
                click(finalX, finalY)
                handler.postDelayed({ click(finalX, finalY) }, 80)
            }
        }
    }

    private fun click(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun longPress(x: Float, y: Float, durationMs: Long) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }
}

// 扩展属性：追踪重复次数
private var ClickPoint.repeatCount: Int
    get() = _repeatCounts.getOrPut(id) { 0 }
    set(value) { _repeatCounts[id] = value }

private val _repeatCounts = mutableMapOf<Long, Int>()
