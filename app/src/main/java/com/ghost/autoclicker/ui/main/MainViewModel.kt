package com.ghost.autoclicker.ui.main

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
    private val prefs = app.getSharedPreferences("autoclicker", Context.MODE_PRIVATE)
    private val gson = Gson()

    val clickPoints: MutableList<ClickPoint>
        get() = ClickAccessibilityService.clickPoints

    val isServiceRunning: Boolean
        get() = ClickAccessibilityService.instance != null

    val isOverlayGranted: Boolean
        get() = Settings.canDrawOverlays(getApplication())

    val totalClicks: Long
        get() = ClickAccessibilityService.globalConfig.totalClicks

    val targetPackage: String?
        get() = ClickAccessibilityService.globalConfig.targetPackage

    init {
        loadConfig()
        setupMarkerCallbacks()
    }

    private fun setupMarkerCallbacks() {
        pointMarkers.onPointMoved = { pointId, x, y ->
            val idx = clickPoints.indexOfFirst { it.id == pointId }
            if (idx >= 0) {
                clickPoints[idx] = clickPoints[idx].copy(x = x, y = y)
                saveConfig()
            }
        }
        pointMarkers.onPointLongPressed = { pointId ->
            // Trigger the edit dialog via livedata or direct callback
            _editingPointId.value = pointId
        }
        pointMarkers.onPointToggled = { _, _ ->
            // Visual feedback only, handled in PointMarkerManager
        }
    }

    private val _editingPointId = androidx.lifecycle.MutableLiveData<Long>(null)
    val editingPointId: androidx.lifecycle.LiveData<Long> get() = _editingPointId

    fun consumeEditRequest(): Long? {
        val id = _editingPointId.value
        _editingPointId.value = null
        return id
    }

    // ===== 持久化 =====

    fun saveConfig() {
        val json = gson.toJson(clickPoints)
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
            if (loaded.isNotEmpty()) {
                ClickAccessibilityService.clickPoints.clear()
                ClickAccessibilityService.clickPoints.addAll(loaded)
            }
        }
        val pkg = prefs.getString("target_package", "") ?: ""
        if (pkg.isNotEmpty()) {
            ClickAccessibilityService.globalConfig = ClickAccessibilityService.globalConfig.copy(targetPackage = pkg)
        }
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
        clickPoints.add(point)
        ClickAccessibilityService.instance?.updateRunning()
        saveConfig()
        if (pointMarkers.isShowing()) {
            pointMarkers.updateAllMarkers(clickPoints)
        }
    }

    fun removePoint(id: Long) {
        clickPoints.removeAll { it.id == id }
        pointMarkers.removeMarker(id)
        saveConfig()
    }

    fun updatePoint(point: ClickPoint) {
        val idx = clickPoints.indexOfFirst { it.id == point.id }
        if (idx >= 0) clickPoints[idx] = point
        ClickAccessibilityService.instance?.updateRunning()
        saveConfig()
        if (pointMarkers.isShowing()) {
            pointMarkers.updateMarkerPosition(point.id, point.x, point.y)
        }
    }

    fun loadPreset(preset: Preset) {
        ClickAccessibilityService.clickPoints.clear()
        ClickAccessibilityService.clickPoints.addAll(preset.points)
        saveConfig()
        if (pointMarkers.isShowing()) {
            pointMarkers.removeAllMarkers()
            pointMarkers.updateAllMarkers(clickPoints)
        }
    }

    fun clearAllPoints() {
        ClickAccessibilityService.clickPoints.clear()
        saveConfig()
    }

    fun setTargetPackage(pkg: String?) {
        ClickAccessibilityService.globalConfig = ClickAccessibilityService.globalConfig.copy(targetPackage = pkg?.ifBlank { null })
        saveConfig()
    }

    fun toggleFloatWindow() {
        if (floatWindow.isShowing()) {
            floatWindow.hide()
            pointMarkers.removeAllMarkers()
        } else {
            floatWindow.show()
            pointMarkers.updateAllMarkers(clickPoints)
        }
    }

    fun showPointMarkers() {
        pointMarkers.updateAllMarkers(clickPoints)
    }

    fun hidePointMarkers() {
        pointMarkers.removeAllMarkers()
    }

    fun stopAll() {
        val svc = ClickAccessibilityService.instance ?: return
        svc.stopClicking()
        ClickAccessibilityService.globalConfig = ClickAccessibilityService.globalConfig.copy(isRunning = false)
    }

    override fun onCleared() {
        super.onCleared()
        saveConfig()
        pointMarkers.removeAllMarkers()
    }
}
