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
import com.ghost.autoclicker.util.ClickLog

/**
 * 点击点悬浮标记管理器
 *
 * 坐标系核心逻辑：
 * - ClickPoint.x/y = 屏幕绝对坐标（与 GestureDescription 一致）
 * - WindowManager params.y ≠ 屏幕y（Gravity.TOP 在 Android 12+ 偏移了一个状态栏高度）
 * - getLocationOnScreen() 返回屏幕绝对坐标
 * - screenOffset = getLocationOnScreen().y - params.y，在首个 marker 添加时自动测量
 *
 * 所有坐标转换统一使用 screenOffset：
 *   params.y = screenY - size/2 - screenOffset
 *   screenY  = params.y + screenOffset + size/2
 */
class PointMarkerManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val markers = mutableMapOf<Long, MarkerEntry>()

    /**
     * 屏幕偏移量，在首个 marker 添加后通过 getLocationOnScreen 自动测量。
     * 典型值：Android 12+ 上等于状态栏高度（~128px）
     */
    private var screenOffset: Int = 0

    /**
     * 获取所有 marker 的实时屏幕坐标。
     * 返回 Map<pointId, Pair<centerX, centerY>>
     * 使用 getLocationOnScreen 获取真实屏幕坐标。
     */
    fun getActualScreenPositions(): Map<Long, Pair<Int, Int>> {
        val size = markerSizePx
        val result = mutableMapOf<Long, Pair<Int, Int>>()
        markers.values.forEach { entry ->
            try {
                val location = IntArray(2)
                entry.view.getLocationOnScreen(location)
                val cx = location[0] + size / 2
                val cy = location[1] + size / 2
                result[entry.pointId] = Pair(cx, cy)
                ClickLog.v("getActualPos[${entry.pointId}]: raw=(${location[0]},${location[1]}) center=($cx,$cy)")
            } catch (e: Exception) {
                ClickLog.e("getActualPos[${entry.pointId}] 失败", e)
            }
        }
        ClickLog.d("getActualScreenPositions: 共${result.size}个点, screenOffset=$screenOffset")
        return result
    }

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

    fun screenYToParamsY(screenY: Int, size: Int): Int {
        return screenY - size / 2 - screenOffset
    }

    fun getScreenOffset(): Int = screenOffset

    private fun getMarkerScreenCenter(view: View, size: Int): Pair<Int, Int> {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Pair(location[0] + size / 2, location[1] + size / 2)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun addMarker(point: ClickPoint, index: Int) {
        if (markers.containsKey(point.id)) {
            ClickLog.w("addMarker: ${point.id} 已存在，跳过")
            return
        }

        val size = markerSizePx
        ClickLog.i("addMarker[${point.id}]: point=(${point.x},${point.y}) index=$index size=${size}px screenOffset=$screenOffset")

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
            y = point.y - size / 2  // 临时值，下面会修正
        }

        ClickLog.d("addMarker[${point.id}]: 初始params=(${params.x},${params.y})")

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
                    ClickLog.v("marker[${point.id}] DOWN: raw=(${event.rawX},${event.rawY}) params=(${params.x},${params.y})")

                    longPressRunnable = Runnable {
                        longPressed = true
                        ClickLog.i("marker[${point.id}] 长按触发")
                        onPointLongPressed?.invoke(point.id)
                    }
                    handler.postDelayed(longPressRunnable!!, 500)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // 运行中禁止拖动，直接消费事件
                    if (ClickAccessibilityService.globalConfig.isRunning) return@setOnTouchListener true

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
                    ClickLog.v("marker[${point.id}] UP: moved=$moved longPressed=$longPressed")

                    if (longPressed) {
                        // Already handled
                    } else if (moved && !ClickAccessibilityService.globalConfig.isRunning) {
                        val (centerX, centerY) = getMarkerScreenCenter(marker, size)
                        ClickLog.i("marker[${point.id}] 拖动结束: screenCenter=($centerX,$centerY) params=(${params.x},${params.y})")
                        onPointMoved?.invoke(point.id, centerX, centerY)
                    } else if (!moved) {
                        val wasSelected = selectedPoints.contains(point.id)
                        if (wasSelected) selectedPoints.remove(point.id) else selectedPoints.add(point.id)
                        ClickLog.i("marker[${point.id}] 点击切换: selected=${!wasSelected}")
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

        // ★ 关键：添加 view 后通过 post 测量真实的 screenOffset
        // 然后用统一的 screenYToParamsY 设置正确位置
        marker.post {
            try {
                val location = IntArray(2)
                marker.getLocationOnScreen(location)
                val yOffset = location[1] - params.y

                ClickLog.i("addMarker[${point.id}] post测量: locationOnScreen=(${location[0]},${location[1]}) params.y=${params.y} yOffset=$yOffset")

                if (yOffset > 0 && yOffset < 500) {
                    if (screenOffset == 0 || screenOffset != yOffset) {
                        if (screenOffset != 0 && screenOffset != yOffset) {
                            ClickLog.w("addMarker[${point.id}] screenOffset 变化: $screenOffset -> $yOffset")
                        }
                        screenOffset = yOffset
                    }
                }

                // 用统一的坐标转换方法设置正确位置
                params.y = screenYToParamsY(point.y, size)
                windowManager.updateViewLayout(marker, params)

                // 验证修正后的位置
                marker.post {
                    try {
                        val loc2 = IntArray(2)
                        marker.getLocationOnScreen(location)
                        val expectedScreenY = point.y
                        val actualScreenY = loc2[1] + size / 2
                        val errorY = actualScreenY - expectedScreenY
                        val actualScreenX = loc2[0] + size / 2
                        val errorX = actualScreenX - point.x
                        ClickLog.i("addMarker[${point.id}] 验证: target=(${point.x},${point.y}) actual=($actualScreenX,$actualScreenY) error=($errorX,$errorY)")
                        if (Math.abs(errorX) > 5 || Math.abs(errorY) > 5) {
                            ClickLog.e("addMarker[${point.id}] 位置偏差过大! error=($errorX,$errorY) offset=$screenOffset")
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                ClickLog.e("addMarker[${point.id}] post测量失败", e)
            }
        }
    }

    fun removeMarker(pointId: Long) {
        markers.remove(pointId)?.let { entry ->
            try { windowManager.removeView(entry.view) } catch (_: Exception) {}
            ClickLog.i("removeMarker[$pointId]")
        }
        selectedPoints.remove(pointId)
        updateAllIndices()
    }

    fun removeAllMarkers() {
        ClickLog.i("removeAllMarkers: 共${markers.size}个")
        markers.values.forEach { entry ->
            try { windowManager.removeView(entry.view) } catch (_: Exception) {}
        }
        markers.clear()
        selectedPoints.clear()
        screenOffset = 0
    }

    fun updateMarkerPosition(pointId: Long, x: Int, y: Int) {
        val entry = markers[pointId] ?: run {
            ClickLog.w("updateMarkerPosition[$pointId]: 不存在")
            return
        }
        val size = markerSizePx
        val oldParams = entry.params.y
        entry.params.x = x - size / 2
        entry.params.y = screenYToParamsY(y, size)
        ClickLog.d("updateMarkerPosition[$pointId]: target=($x,$y) params=(${entry.params.x},${entry.params.y}) oldParams.y=$oldParams screenOffset=$screenOffset")
        try { windowManager.updateViewLayout(entry.view, entry.params) } catch (e: Exception) {
            ClickLog.e("updateMarkerPosition[$pointId] 更新失败", e)
        }
    }

    fun updateAllMarkers(points: List<ClickPoint>) {
        val currentIds = points.map { it.id }.toSet()
        val removed = markers.keys.filter { it !in currentIds }
        if (removed.isNotEmpty()) {
            ClickLog.d("updateAllMarkers: 移除${removed.size}个旧点: $removed")
        }
        removed.forEach { removeMarker(it) }

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
        ClickLog.i("updateAllMarkers 完成: 共${points.size}个点, ${markers.size}个marker, screenOffset=$screenOffset")
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

        val color = when {
            running -> "#4CAF50"
            selected -> "#FF9800"
            else -> "#E53935"
        }
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor(color))
            setStroke((1.5 * density).toInt(), Color.WHITE)
            alpha = if (running) 150 else if (selected) 240 else 220
        }
        view.background = bg
    }

    fun updateRunningState() {
        val running = ClickAccessibilityService.globalConfig.isRunning
        markers.values.forEach { entry ->
            updateMarkerAppearance(entry.view, entry.pointId, entry.index)
            // 运行时让 marker 不接收触摸事件，避免吞掉 dispatchGesture 的点击
            if (running) {
                entry.params.flags = entry.params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                entry.params.flags = entry.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            try { windowManager.updateViewLayout(entry.view, entry.params) } catch (_: Exception) {}
        }
        if (running) ClickLog.i("marker外观切换为运行状态(绿色)")
    }

    fun isShowing() = markers.isNotEmpty()
}
