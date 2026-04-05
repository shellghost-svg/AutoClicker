package com.ghost.autoclicker.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ghost.autoclicker.model.*
import com.ghost.autoclicker.service.ClickAccessibilityService
import com.ghost.autoclicker.ui.theme.AutoClickerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = MainViewModel(application)

        setContent {
            AutoClickerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(vm)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPoint by remember { mutableStateOf<ClickPoint?>(null) }
    var showPresetDialog by remember { mutableStateOf(false) }
    var showTargetAppDialog by remember { mutableStateOf(false) }

    // 定时刷新 service 状态
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            vm.refreshServiceStatus()
            vm.floatWindow.updateStatus()
            vm.pointMarkers.updateRunningState()
            val editId = vm.consumeEditRequest()
            if (editId != null) {
                editingPoint = vm.points.find { it.id == editId }
            }
        }
    }

    // 当悬浮窗显示/隐藏时刷新标记
    LaunchedEffect(vm.floatWindow.isShowing()) {
        if (vm.floatWindow.isShowing()) {
            vm.pointMarkers.updateAllMarkers(vm.points)
        } else {
            vm.pointMarkers.removeAllMarkers()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("👻 连点器", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                actions = {
                    vm.targetPackage.value?.let { pkg ->
                        Surface(color = Color(0xFFE3F2FD), shape = MaterialTheme.shapes.small) {
                            Text("🎯 $pkg", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = { showTargetAppDialog = true }) {
                        Icon(Icons.Default.Apps, contentDescription = "限定APP")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            StatusBanner(vm)

            // 运行状态条
            if (vm.isRunning.value) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = Color(0xFFC8E6C9),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🟢 运行中", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        Spacer(Modifier.width(12.dp))
                        Text("已执行 ${vm.totalClicks.value} 次", fontSize = 12.sp, color = Color(0xFF555555))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // 点击点列表 - 用 vm.points（Compose observable）
            if (vm.points.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有点击点", color = Color.Gray, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("点击下方「添加」或「预设」快速开始", color = Color.Gray.copy(0.6f), fontSize = 13.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(vm.points, key = { it.id }) { point ->
                        PointCard(
                            point = point,
                            onEdit = { editingPoint = point },
                            onDelete = { vm.removePoint(point.id) },
                            onToggle = { vm.updatePoint(point.copy(enabled = it)) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // 底部操作栏
            BottomBar(
                vm = vm,
                onAdd = { showAddDialog = true },
                onPreset = { showPresetDialog = true },
                onStop = { vm.stopAll() }
            )
        }
    }

    if (showAddDialog) {
        PointEditDialog(point = ClickPoint(), isNew = true, onConfirm = {
            vm.addPoint(it); showAddDialog = false
        }, onDismiss = { showAddDialog = false })
    }

    editingPoint?.let { point ->
        PointEditDialog(point = point, onConfirm = {
            vm.updatePoint(it); editingPoint = null
        }, onDismiss = { editingPoint = null })
    }

    if (showPresetDialog) {
        PresetDialog(
            onSelect = { preset ->
                vm.loadPreset(preset)
                showPresetDialog = false
            },
            onDismiss = { showPresetDialog = false }
        )
    }

    if (showTargetAppDialog) {
        TargetAppDialog(
            currentPackage = vm.targetPackage.value,
            onConfirm = { vm.setTargetPackage(it) },
            onDismiss = { showTargetAppDialog = false }
        )
    }
}

@Composable
fun StatusBanner(vm: MainViewModel) {
    Column(Modifier.padding(16.dp)) {
        StatusRow(
            ok = vm.isServiceRunning.value,
            text = if (vm.isServiceRunning.value) "无障碍服务已开启" else "无障碍服务未开启",
            onClick = { if (!vm.isServiceRunning.value) vm.openAccessibilitySettings() },
            showButton = !vm.isServiceRunning.value
        )
        Spacer(Modifier.height(8.dp))
        StatusRow(
            ok = vm.isOverlayGranted.value,
            text = if (vm.isOverlayGranted.value) "悬浮窗权限已授予" else "悬浮窗权限未授予",
            onClick = { if (!vm.isOverlayGranted.value) vm.openOverlaySettings() },
            showButton = !vm.isOverlayGranted.value
        )
    }
}

@Composable
fun StatusRow(ok: Boolean, text: String, onClick: () -> Unit, showButton: Boolean) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (ok) Color(0xFFE8F5E9) else Color(0xFFFFEBEE), MaterialTheme.shapes.small)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Error,
            null,
            tint = if (ok) Color(0xFF4CAF50) else Color(0xFFF44336),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (showButton) TextButton(onClick = onClick) { Text("去开启", fontSize = 13.sp) }
    }
}

@Composable
fun PointCard(point: ClickPoint, onEdit: () -> Unit, onDelete: () -> Unit, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (point.enabled) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = point.enabled, onCheckedChange = onToggle)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(point.label, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                val info = buildString {
                    append("(${point.x}, ${point.y})")
                    append(" ${point.delayMs}ms/次")
                    append(" ${point.clickMode.label}")
                    if (point.clickMode == ClickMode.SWIPE) append("→(${point.swipeEndX},${point.swipeEndY})")
                    if (point.maxRepeat > 0) append(" ×${point.maxRepeat}")
                }
                Text(info, fontSize = 12.sp, color = Color.Gray)
                if (point.posOffsetPx > 0 || point.delayRandomMs > 0) {
                    Text(
                        buildString {
                            if (point.posOffsetPx > 0) append("位置±${point.posOffsetPx}px ")
                            if (point.delayRandomMs > 0) append("时间±${point.delayRandomMs}ms")
                        },
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "编辑", Modifier.size(20.dp)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除", Modifier.size(20.dp)) }
        }
    }
}

@Composable
fun BottomBar(vm: MainViewModel, onAdd: () -> Unit, onPreset: () -> Unit, onStop: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.toggleFloatWindow() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.TouchApp, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (vm.floatWindow.isShowing()) "隐藏悬浮窗" else "显示悬浮窗", fontSize = 13.sp)
                }
                OutlinedButton(onClick = onPreset, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Bookmark, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("预设", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加点击点", fontSize = 13.sp)
                }
                if (vm.isRunning.value) {
                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                    ) {
                        Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("停止", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PresetDialog(onSelect: (Preset) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("快捷预设") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(preset) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(preset.icon, fontSize = 24.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(preset.name, fontWeight = FontWeight.Medium)
                                Text(preset.description, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun TargetAppDialog(currentPackage: String?, onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var pkg by remember { mutableStateOf(currentPackage ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("限定目标APP") },
        text = {
            Column {
                Text("输入APP包名，连点器只在该APP内运行。留空则不限制。", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pkg, onValueChange = { pkg = it },
                    label = { Text("包名（如 com.tencent.mm）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                if (currentPackage != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { pkg = "" }) { Text("清除限制") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(pkg.ifBlank { null })
                onDismiss()
            }) { Text("确认") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
