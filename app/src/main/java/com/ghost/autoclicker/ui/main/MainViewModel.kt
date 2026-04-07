package com.ghost.autoclicker.ui.main

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.lifecycle.AndroidViewModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ghost.autoclicker.model.*
import com.ghost.autoclicker.service.ClickAccessibilityService
import com.ghost.autoclicker.ui.float.FloatWindowManager
import com.ghost.autoclicker.ui.float.PointMarkerManager

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val floatWindow = FloatWindowManager(app)
    val pointMarkers = PointMarkerManager(app)
    init {
        floatWindow.markerManager = pointMarkers
    }
    private val prefs = app.getSharedPreferences("autoclicker", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Compose 可观察状态
    private val _points = mutableStateListOf<ClickPoint>()
    val points: List<ClickPoint> get() = _points

    val isServiceRunning = mutableStateOf(false)
    val isOverlayGranted = mutableStateOf(false)
    val isRunning = mutableStateOf(false)
    val totalClicks = mutableStateOf(0L)
    val targetPackage = mutableStateOf<String?>(null)

    var editingPointId by mutableStateOf<Long?>(null)
        private set

    init {
        loadConfig()
        setupMarkerCallbacks()
        refreshServiceStatus()
        // 悬浮窗默认打开（如果权限已授予）
        if (isOverlayGranted.value && _points.isNotEmpty()) {
            floatWindow.show()
            pointMarkers.updateAllMarkers(_points)
        }
    }

    private fun setupMarkerCallbacks() {
        pointMarkers.onPointMoved = { pointId, x, y ->
            val idx = _points.indexOfFirst { it.id == pointId }
            if (idx >= 0) {
                _points[idx] = _points[idx].copy(x = x, y = y)
                syncToService()
                saveConfig()
            }
        }
        pointMarkers.onPointLongPressed = { pointId ->
            editingPointId = pointId
        }
    }

    fun refreshServiceStatus() {
        isServiceRunning.value = ClickAccessibilityService.instance != null
        isOverlayGranted.value = Settings.canDrawOverlays(getApplication())
        isRunning.value = ClickAccessibilityService.globalConfig.isRunning
        totalClicks.value = ClickAccessibilityService.globalConfig.totalClicks
        targetPackage.value = ClickAccessibilityService.globalConfig.targetPackage
    }

    fun consumeEditRequest(): Long? {
        val id = editingPointId
        editingPointId = null
        return id
    }

    // ===== 同步到 Service =====
    private fun syncToService() {
        // 获取每个 marker 的实际屏幕坐标，确保点击位置精确
        val actualPositions = pointMarkers.getActualScreenPositions()
        val pointsToSync = _points.map { pt ->
            actualPositions[pt.id]?.let { (ax, ay) ->
                pt.copy(x = ax, y = ay)
            } ?: pt
        }
        ClickAccessibilityService.clickPoints.clear()
        ClickAccessibilityService.clickPoints.addAll(pointsToSync)
        ClickAccessibilityService.instance?.updateRunning()
    }

    // ===== 持久化 =====
    fun saveConfig() {
        val json = gson.toJson(_points.toList())
        prefs.edit()
            .putString("points", json)
            .putString("target_package", ClickAccessibilityService.globalConfig.targetPackage ?: "")
            .apply()
    }

    private fun loadConfig() {
        val json = prefs.getString("points", null)
        if (!json.isNullOrEmpty()) {
            val type = object : TypeToken<List<ClickPoint>>() {}.type
            val loaded: List<ClickPoint> = try { gson.fromJson(json, type) } catch (_: Exception) { emptyList() }
            _points.clear()
            _points.addAll(loaded)
        }
        val pkg = prefs.getString("target_package", "") ?: ""
        if (pkg.isNotEmpty()) {
            ClickAccessibilityService.globalConfig = ClickAccessibilityService.globalConfig.copy(targetPackage = pkg)
        }
        targetPackage.value = ClickAccessibilityService.globalConfig.targetPackage
        // 同步到 service
        syncToService()
    }

    // ===== 操作 =====
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
        _points.add(point)
        syncToService()
        saveConfig()
        if (floatWindow.isShowing()) {
            pointMarkers.updateAllMarkers(_points)
        }
    }

    fun removePoint(id: Long) {
        _points.removeAll { it.id == id }
        pointMarkers.removeMarker(id)
        syncToService()
        saveConfig()
    }

    fun updatePoint(point: ClickPoint) {
        val idx = _points.indexOfFirst { it.id == point.id }
        if (idx >= 0) _points[idx] = point
        syncToService()
        saveConfig()
        if (pointMarkers.isShowing()) {
            pointMarkers.updateMarkerPosition(point.id, point.x, point.y)
        }
    }

    fun loadPreset(preset: Preset) {
        _points.clear()
        _points.addAll(preset.points)
        syncToService()
        saveConfig()
        if (floatWindow.isShowing()) {
            pointMarkers.removeAllMarkers()
            pointMarkers.updateAllMarkers(_points)
        }
    }

    fun clearAllPoints() {
        _points.clear()
        syncToService()
        saveConfig()
    }

    fun setTargetPackage(pkg: String?) {
        ClickAccessibilityService.globalConfig = ClickAccessibilityService.globalConfig.copy(targetPackage = pkg?.ifBlank { null })
        targetPackage.value = ClickAccessibilityService.globalConfig.targetPackage
        saveConfig()
    }

    fun toggleFloatWindow() {
        if (floatWindow.isShowing()) {
            floatWindow.hide()
            pointMarkers.removeAllMarkers()
        } else {
            floatWindow.show()
            pointMarkers.updateAllMarkers(_points)
        }
    }

    fun stopAll() {
        val svc = ClickAccessibilityService.instance ?: return
        svc.stopClicking()
        ClickAccessibilityService.globalConfig = ClickAccessibilityService.globalConfig.copy(isRunning = false)
        isRunning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        saveConfig()
        pointMarkers.removeAllMarkers()
    }
}
