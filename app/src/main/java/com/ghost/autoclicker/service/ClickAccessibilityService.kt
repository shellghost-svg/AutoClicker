package com.ghost.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.ghost.autoclicker.model.*
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 可用于检测当前包名
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        stopClicking()
        instance = null
        onStatusChanged?.invoke(false)
        handler.removeCallbacksAndMessages(null)
    }

    fun startClicking() {
        if (isClicking) return
        if (!globalConfig.isRunning) return

        val enabledPoints = clickPoints.filter { it.enabled }
        if (enabledPoints.isEmpty()) {
            isClicking = false
            return
        }

        // 检查目标APP限定
        val targetPkg = globalConfig.targetPackage
        if (!targetPkg.isNullOrEmpty()) {
            val currentPkg = rootInActiveWindow?.packageName?.toString()
            if (currentPkg != null && currentPkg != targetPkg) {
                // 不在目标APP内，暂停但不停止，等回到目标APP再继续
                handler.postDelayed({ startClicking() }, 1000)
                return
            }
        }

        isClicking = true
        scheduleNext(enabledPoints, 0)
    }

    fun stopClicking() {
        isClicking = false
        clickRunnable?.let { handler.removeCallbacks(it) }
        clickRunnable = null
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

        // 检查目标APP
        val targetPkg = globalConfig.targetPackage
        if (!targetPkg.isNullOrEmpty()) {
            val currentPkg = rootInActiveWindow?.packageName?.toString()
            if (currentPkg != null && currentPkg != targetPkg) {
                handler.postDelayed({ scheduleNext(points, pointIndex) }, 1000)
                return
            }
        }

        // 检查最大重复次数
        val count = getRepeatCount(point.id)
        if (point.maxRepeat > 0 && count >= point.maxRepeat) {
            resetRepeatCount(point.id)
            val nextIdx = (pointIndex + 1) % points.size
            if (nextIdx == 0 && points.size > 1) {
                points.forEach { resetRepeatCount(it.id) }
            }
            scheduleNext(points, nextIdx)
            return
        }

        performClick(point)

        // 增加计数
        setRepeatCount(point.id, count + 1)
        globalConfig = globalConfig.copy(totalClicks = globalConfig.totalClicks + 1)
        onStatusChanged?.invoke(true)

        clickRunnable = object : Runnable {
            override fun run() {
                if (!isClicking || !globalConfig.isRunning) return
                val nextIdx = (pointIndex + 1) % points.size
                if (nextIdx == 0 && points.size > 1) {
                    points.forEach { resetRepeatCount(it.id) }
                }
                scheduleNext(points, nextIdx)
            }
        }

        val delay = (point.delayMs + if (point.delayRandomMs > 0) {
            Random.nextLong(-point.delayRandomMs, point.delayRandomMs + 1)
        } else 0L).coerceIn(10, 60_000)

        handler.postDelayed(clickRunnable!!, delay)
    }

    private fun performClick(point: ClickPoint) {
        val offsetX = if (point.posOffsetPx > 0) Random.nextInt(-point.posOffsetPx, point.posOffsetPx + 1) else 0
        val offsetY = if (point.posOffsetPx > 0) Random.nextInt(-point.posOffsetPx, point.posOffsetPx + 1) else 0

        val finalX = (point.x + offsetX).toFloat().coerceIn(0f, 2000f)
        val finalY = (point.y + offsetY).toFloat().coerceIn(0f, 2000f)

        when (point.clickMode) {
            ClickMode.SINGLE -> click(finalX, finalY)
            ClickMode.LONG_PRESS -> longPress(finalX, finalY, point.longPressDurationMs.coerceIn(50, 10_000))
            ClickMode.DOUBLE -> {
                click(finalX, finalY)
                handler.postDelayed({ click(finalX, finalY) }, 80)
            }
            ClickMode.SWIPE -> {
                val endX = (point.swipeEndX + offsetX).toFloat().coerceIn(0f, 2000f)
                val endY = (point.swipeEndY + offsetY).toFloat().coerceIn(0f, 2000f)
                swipe(finalX, finalY, endX, endY, point.swipeDurationMs.coerceIn(50, 5000))
            }
        }
    }

    private fun click(x: Float, y: Float) {
        val path = android.graphics.Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun longPress(x: Float, y: Float, durationMs: Long) {
        val path = android.graphics.Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long) {
        val path = android.graphics.Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, null, null)
    }
}

// 重复计数管理（使用独立Map避免扩展属性问题）
private val _repeatCounts = mutableMapOf<Long, Int>()

private fun getRepeatCount(id: Long): Int = _repeatCounts.getOrPut(id) { 0 }
private fun setRepeatCount(id: Long, count: Int) { _repeatCounts[id] = count }
private fun resetRepeatCount(id: Long) { _repeatCounts.remove(id) }
