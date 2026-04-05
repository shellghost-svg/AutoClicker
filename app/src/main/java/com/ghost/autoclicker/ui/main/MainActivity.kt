package com.ghost.autoclicker.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import com.ghost.autoclicker.model.ClickMode
import com.ghost.autoclicker.model.ClickPoint
import com.ghost.autoclicker.ui.theme.AutoClickerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = MainViewModel(application)

        setContent {
            AutoClickerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(vm)
                }
            }
        }

        // 监听服务状态
        ClickAccessibilityService.onStatusChanged = { running ->
            // Compose 重组会自动读取 isServiceRunning
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPoint by remember { mutableStateOf<ClickPoint?>(null) }

    // 定时刷新服务状态
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("连点器", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // 状态栏
            StatusBanner(vm)

            // 点击点列表
            if (vm.clickPoints.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("还没有点击点，点击下方添加", color = Color.Gray, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(vm.clickPoints, key = { it.id }) { point ->
                        PointCard(
                            point = point,
                            onEdit = { editingPoint = point },
                            onDelete = { vm.removePoint(point.id) },
                            onToggle = { updated ->
                                vm.updatePoint(point.copy(enabled = updated))
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // 底部操作栏
            BottomBar(
                vm = vm,
                onAdd = { showAddDialog = true },
                onStop = { vm.stopAll() }
            )
        }
    }

    // 添加对话框
    if (showAddDialog) {
        PointEditDialog(
            point = ClickPoint(),
            onConfirm = {
                vm.addPoint(it)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // 编辑对话框
    editingPoint?.let { point ->
        PointEditDialog(
            point = point,
            onConfirm = {
                vm.updatePoint(it)
                editingPoint = null
            },
            onDismiss = { editingPoint = null }
        )
    }
}

@Composable
fun StatusBanner(vm: MainViewModel) {
    val serviceOk = vm.isServiceRunning
    val overlayOk = vm.isOverlayGranted

    Column(modifier = Modifier.padding(16.dp)) {
        // 无障碍服务状态
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (serviceOk) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    MaterialTheme.shapes.small
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (serviceOk) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (serviceOk) Color(0xFF4CAF50) else Color(0xFFF44336),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (serviceOk) "无障碍服务已开启" else "无障碍服务未开启",
                fontSize = 13.sp
            )
            if (!serviceOk) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.openAccessibilitySettings() }) {
                    Text("去开启", fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 悬浮窗权限
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (overlayOk) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    MaterialTheme.shapes.small
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (overlayOk) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (overlayOk) Color(0xFF4CAF50) else Color(0xFFF44336),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (overlayOk) "悬浮窗权限已授予" else "悬浮窗权限未授予",
                fontSize = 13.sp
            )
            if (!overlayOk) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.openOverlaySettings() }) {
                    Text("去开启", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun PointCard(
    point: ClickPoint,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (point.enabled) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 开关
            Switch(checked = point.enabled, onCheckedChange = onToggle)

            Spacer(modifier = Modifier.width(12.dp))

            // 信息
            Column(modifier = Modifier.weight(1f)) {
                Text(point.label, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "(${point.x}, ${point.y})  ${point.delayMs}ms/次  ${point.clickMode.label}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                if (point.posOffsetPx > 0 || point.delayRandomMs > 0) {
                    Text(
                        buildString {
                            if (point.posOffsetPx > 0) append("位置偏移±${point.posOffsetPx}px ")
                            if (point.delayRandomMs > 0) append("时间偏移±${point.delayRandomMs}ms")
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 操作按钮
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun BottomBar(vm: MainViewModel, onAdd: () -> Unit, onStop: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { vm.toggleFloatWindow() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (vm.floatWindow.isShowing()) "隐藏悬浮窗" else "显示悬浮窗")
            }
            Button(
                onClick = onAdd,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加点击点")
            }
        }
    }
}

private val ClickMode.label: String
    get() = when (this) {
        ClickMode.SINGLE -> "单击"
        ClickMode.LONG_PRESS -> "长按"
        ClickMode.DOUBLE -> "双击"
    }
