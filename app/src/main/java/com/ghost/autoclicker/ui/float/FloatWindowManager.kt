package com.ghost.autoclicker.ui.float

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ghost.autoclicker.service.ClickAccessibilityService
import com.ghost.autoclicker.util.ClickLog

class FloatWindowManager(private val context: Context) {

    var markerManager: PointMarkerManager? = null

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var floatView: View? = null
    private var countText: TextView? = null
    private var statusText: TextView? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (floatView != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xDD222222.toInt())
            setPadding(12, 8, 12, 8)
            gravity = Gravity.CENTER
        }

        val statusTv = TextView(context).apply {
            text = "▶ 未启动"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        statusText = statusTv

        val countTv = TextView(context).apply {
            text = "0 次"
            setTextColor(0xAAFFFFFF.toInt())
            textSize = 10f
            gravity = Gravity.CENTER
        }
        countText = countTv

        container.addView(statusTv)
        container.addView(countTv)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 100
            y = 200
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(container, params)
                    moved = true
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleClicking(container)
                    true
                }
                else -> false
            }
        }

        floatView = container
        windowManager.addView(container, params)
        ClickLog.i("FloatWindow: 已显示")
    }

    fun updateStatus() {
        val running = ClickAccessibilityService.globalConfig.isRunning
        val clicks = ClickAccessibilityService.globalConfig.totalClicks

        statusText?.text = if (running) "⏸ 运行中" else "▶ 未启动"
        statusText?.setTextColor(if (running) 0xFF66BB6A.toInt() else 0xFFFFFFFF.toInt())
        countText?.text = "$clicks 次"
    }

    @Suppress("UNUSED_PARAMETER")
    private fun toggleClicking(container: LinearLayout) {
        val svc = ClickAccessibilityService.instance
        if (svc == null) {
            ClickLog.e("toggleClicking: 服务未连接!")
            statusText?.text = "❌ 服务未连接"
            statusText?.setTextColor(0xFFFF5555.toInt())
            countText?.text = "请开启无障碍"
            handler.postDelayed({ updateStatus() }, 2000)
            return
        }

        val wasRunning = ClickAccessibilityService.globalConfig.isRunning
        val enabledPoints = ClickAccessibilityService.clickPoints.filter { it.enabled }
        if (!wasRunning && enabledPoints.isEmpty()) {
            ClickLog.e("toggleClicking: 无可用点")
            statusText?.text = "❌ 无可用点"
            statusText?.setTextColor(0xFFFF5555.toInt())
            countText?.text = "请添加点击点"
            handler.postDelayed({ updateStatus() }, 2000)
            return
        }

        ClickAccessibilityService.globalConfig = ClickAccessibilityService.globalConfig.copy(
            isRunning = !wasRunning,
            totalClicks = if (!wasRunning) 0 else ClickAccessibilityService.globalConfig.totalClicks
        )

        // 启动前同步 marker 的实际屏幕坐标到 service
        if (!wasRunning) {
            markerManager?.let { mm ->
                val positions = mm.getActualScreenPositions()
                ClickLog.i("toggleClicking: 启动前同步坐标, marker offset=${mm.getScreenOffset()}")
                ClickAccessibilityService.clickPoints.forEachIndexed { i, pt ->
                    positions[pt.id]?.let { (ax, ay) ->
                        val oldX = pt.x; val oldY = pt.y
                        ClickAccessibilityService.clickPoints[i] = pt.copy(x = ax, y = ay)
                        if (oldX != ax || oldY != ay) {
                            ClickLog.i("toggleClicking: 点[${pt.label}] 坐标修正: ($oldX,$oldY) -> ($ax,$ay)")
                        }
                    }
                }
            }
        }

        ClickLog.i("toggleClicking: ${if (wasRunning) "停止" else "启动"}, 共${enabledPoints.size}个点")
        updateStatus()
        svc.updateRunning()
    }

    fun hide() {
        floatView?.let { windowManager.removeView(it); floatView = null }
        countText = null
        statusText = null
        ClickLog.i("FloatWindow: 已隐藏")
    }

    fun isShowing() = floatView != null
}
