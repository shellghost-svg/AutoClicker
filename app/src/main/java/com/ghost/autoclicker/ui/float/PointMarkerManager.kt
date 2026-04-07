package com.ghost.autoclicker.ui.float

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.ghost.autoclicker.model.ClickPoint
import com.ghost.autoclicker.service.ClickAccessibilityService

class PointMarkerManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val markers = mutableMapOf<Long, MarkerEntry>()

    data class MarkerEntry(
        val view: View,
        val numberText: TextView,
        val params: WindowManager.LayoutParams,
        var pointId: Long,
        var index: Int
    )

    var onPointMoved: ((Long, Int, Int) -> Unit)? = null
    var onPointLongPressed: ((Long) -> Unit)? = null
    var onPointToggled: ((Long, Boolean) -> Unit)? = null

    private val selectedPoints = mutableSetOf<Long>()

    private val layoutType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private val markerSizePx: Int
        get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 44f, context.resources.displayMetrics).toInt()

    private val density: Float
        get() = context.resources.displayMetrics.density

    /**
     * 获取 marker 在屏幕上的绝对中心坐标。
     * 使用 getLocationOnScreen() 而非 params.y + size/2，
     * 因为 WindowManager 的 params.y 不等于屏幕绝对 y 坐标：
     * - Gravity.TOP 的 params.y=0 对应的是状态栏下方（Android 12+）
     * - 而 GestureDescription 使用的是屏幕绝对坐标（y=0 在屏幕最顶部）
     * - getLocationOnScreen() 返回的才是和 GestureDescription 一致的坐标系
     */
    private fun getMarkerScreenCenter(view: View, size: Int): Pair<Int, Int> {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Pair(location[0] + size / 2, location[1] + size / 2)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun addMarker(point: ClickPoint, index: Int) {
        if (markers.containsKey(point.id)) return

        val size = markerSizePx

        val marker = FrameLayout(context).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setSize(size, size)
                setColor(Color.parseColor("#E53935"))
                setStroke((1.5 * density).toInt(), Color.WHITE)
                alpha = 220
            }
            background = bg
            elevation = 4f * density
        }

        val numberText = TextView(context).apply {
            text = "${index + 1}"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        marker.addView(numberText, lp)

        val params = WindowManager.LayoutParams(
            size,
            size,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = point.x - size / 2
            y = point.y - size / 2
        }

        var downTime = 0L
        var downRawX = 0f
        var downRawY = 0f
        var downX = 0
        var downY = 0
        var moved = false
        var longPressed = false
        var longPressRunnable: Runnable? = null

        marker.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = System.currentTimeMillis()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downX = params.x
                    downY = params.y
                    moved = false
                    longPressed = false

                    longPressRunnable = Runnable {
                        longPressed = true
                        onPointLongPressed?.invoke(point.id)
                    }
                    handler.postDelayed(longPressRunnable!!, 500)

                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()

                    if (!moved && (Math.abs(dx) > 5 || Math.abs(dy) > 5)) {
                        moved = true
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                    }

                    if (moved) {
                        params.x = downX + dx
                        params.y = downY + dy
                        windowManager.updateViewLayout(marker, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }

                    if (longPressed) {
                        // Already handled
                    } else if (moved) {
                        // 拖动结束：用 getLocationOnScreen 获取准确的屏幕绝对坐标
                        val (centerX, centerY) = getMarkerScreenCenter(marker, size)
                        onPointMoved?.invoke(point.id, centerX, centerY)
                    } else {
                        val wasSelected = selectedPoints.contains(point.id)
                        if (wasSelected) selectedPoints.remove(point.id) else selectedPoints.add(point.id)
                        onPointToggled?.invoke(point.id, !wasSelected)
                        updateMarkerAppearance(marker, point.id, index)
                    }
                    true
                }
                else -> false
            }
        }

        markers[point.id] = MarkerEntry(marker, numberText, params, point.id, index)
        windowManager.addView(marker, params)
    }

    fun removeMarker(pointId: Long) {
        markers.remove(pointId)?.let { entry ->
            try { windowManager.removeView(entry.view) } catch (_: Exception) {}
        }
        selectedPoints.remove(pointId)
        updateAllIndices()
    }

    fun removeAllMarkers() {
        markers.values.forEach { entry ->
            try { windowManager.removeView(entry.view) } catch (_: Exception) {}
        }
        markers.clear()
        selectedPoints.clear()
    }

    fun updateMarkerPosition(pointId: Long, x: Int, y: Int) {
        val entry = markers[pointId] ?: return
        val size = markerSizePx
        entry.params.x = x - size / 2
        entry.params.y = y - size / 2
        try { windowManager.updateViewLayout(entry.view, entry.params) } catch (_: Exception) {}
    }

    fun updateAllMarkers(points: List<ClickPoint>) {
        val currentIds = points.map { it.id }.toSet()
        markers.keys.filter { it !in currentIds }.forEach { removeMarker(it) }

        points.forEachIndexed { index, point ->
            if (point.id in markers) {
                val entry = markers[point.id]!!
                entry.index = index
                entry.numberText.text = "${index + 1}"
                updateMarkerPosition(point.id, point.x, point.y)
                updateMarkerAppearance(entry.view, point.id, index)
            } else {
                addMarker(point, index)
            }
        }

        updateRunningState()
    }

    private fun updateAllIndices() {
        val sortedEntries = markers.values.sortedBy { it.pointId }
        sortedEntries.forEachIndexed { i, entry ->
            entry.index = i
            entry.numberText.text = "${i + 1}"
        }
    }

    private fun updateMarkerAppearance(view: View, pointId: Long, index: Int) {
        val selected = selectedPoints.contains(pointId)
        val running = ClickAccessibilityService.globalConfig.isRunning

        val bg = when {
            running -> GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4CAF50"))
                setStroke((1.5 * density).toInt(), Color.WHITE)
                alpha = 150
            }
            selected -> GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF9800"))
                setStroke((2.5 * density).toInt(), Color.parseColor("#FFE082"))
                alpha = 240
            }
            else -> GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E53935"))
                setStroke((1.5 * density).toInt(), Color.WHITE)
                alpha = 220
            }
        }
        view.background = bg
    }

    fun updateRunningState() {
        val running = ClickAccessibilityService.globalConfig.isRunning
        markers.values.forEach { entry ->
            updateMarkerAppearance(entry.view, entry.pointId, entry.index)
        }
    }

    fun isShowing() = markers.isNotEmpty()
}
