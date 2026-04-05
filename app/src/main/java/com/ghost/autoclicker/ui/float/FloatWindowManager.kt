package com.ghost.autoclicker.ui.float

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.ghost.autoclicker.service.ClickAccessibilityService

class FloatWindowManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatView: View? = null

    fun show() {
        if (floatView != null) return
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val dot = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(0xCC333333.toInt())
            setPadding(16, 16, 16, 16)
        }

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

        dot.setOnTouchListener { _, event ->
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
                    windowManager.updateViewLayout(dot, params)
                    moved = true
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        toggleClicking(dot)
                    }
                    true
                }
                else -> false
            }
        }

        floatView = dot
        windowManager.addView(dot, params)
    }

    private fun toggleClicking(dot: ImageView) {
        val svc = ClickAccessibilityService.instance ?: return
        val wasRunning = ClickAccessibilityService.globalConfig.isRunning
        ClickAccessibilityService.globalConfig = ClickAccessibilityService.globalConfig.copy(isRunning = !wasRunning)
        dot.setImageResource(
            if (!wasRunning) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
        dot.setBackgroundColor(
            if (!wasRunning) 0xCC4CAF50.toInt()
            else 0xCC333333.toInt()
        )
        svc.updateRunning()
    }

    fun hide() {
        floatView?.let { windowManager.removeView(it); floatView = null }
    }

    fun isShowing() = floatView != null
}
