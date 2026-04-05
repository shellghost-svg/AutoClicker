package com.ghost.autoclicker.ui.main

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import com.ghost.autoclicker.model.ClickMode
import com.ghost.autoclicker.model.ClickPoint
import com.ghost.autoclicker.service.ClickAccessibilityService
import com.ghost.autoclicker.ui.float.FloatWindowManager

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val floatWindow = FloatWindowManager(app)

    val clickPoints: MutableList<ClickPoint>
        get() = ClickAccessibilityService.clickPoints

    val isServiceRunning: Boolean
        get() = ClickAccessibilityService.instance != null

    val isOverlayGranted: Boolean
        get() = Settings.canDrawOverlays(getApplication())

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${getApplication<Application>().packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        getApplication<Application>().startActivity(intent)
    }

    fun addPoint(point: ClickPoint = ClickPoint()) {
        clickPoints.add(point)
        ClickAccessibilityService.instance?.updateRunning()
    }

    fun removePoint(id: Long) {
        clickPoints.removeAll { it.id == id }
    }

    fun updatePoint(point: ClickPoint) {
        val idx = clickPoints.indexOfFirst { it.id == point.id }
        if (idx >= 0) clickPoints[idx] = point
        ClickAccessibilityService.instance?.updateRunning()
    }

    fun toggleFloatWindow() {
        if (floatWindow.isShowing()) floatWindow.hide() else floatWindow.show()
    }

    fun stopAll() {
        val svc = ClickAccessibilityService.instance ?: return
        svc.stopClicking()
        ClickAccessibilityService.globalConfig = ClickAccessibilityService.globalConfig.copy(isRunning = false)
    }

    override fun onCleared() {
        super.onCleared()
        // 不自动关闭悬浮窗，让用户控制
    }
}
