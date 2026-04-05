package com.ghost.autoclicker.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ghost.autoclicker.model.ClickMode
import com.ghost.autoclicker.model.ClickPoint

@Composable
fun PointEditDialog(
    point: ClickPoint,
    isNew: Boolean = false,
    onConfirm: (ClickPoint) -> Unit,
    onDismiss: () -> Unit
) {
    var pointLabel by remember { mutableStateOf(point.label) }
    var x by remember { mutableStateOf(point.x.toString()) }
    var y by remember { mutableStateOf(point.y.toString()) }
    var delayMs by remember { mutableStateOf(point.delayMs.toString()) }
    var delayRandomMs by remember { mutableStateOf(point.delayRandomMs.toString()) }
    var posOffsetPx by remember { mutableStateOf(point.posOffsetPx.toString()) }
    var clickMode by remember { mutableStateOf(point.clickMode) }
    var longPressDuration by remember { mutableStateOf(point.longPressDurationMs.toString()) }
    var maxRepeat by remember { mutableStateOf(if (point.maxRepeat < 0) "" else point.maxRepeat.toString()) }
    // 滑动参数
    var swipeEndX by remember { mutableStateOf(point.swipeEndX.toString()) }
    var swipeEndY by remember { mutableStateOf(point.swipeEndY.toString()) }
    var swipeDuration by remember { mutableStateOf(point.swipeDurationMs.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isNew) "添加点击点" else "编辑点击点", fontSize = 18.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关闭") }
                }

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = pointLabel, onValueChange = { pointLabel = it },
                    label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = x, onValueChange = { x = it.filter { c -> c.isDigit() } },
                        label = { Text("X 坐标") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = y, onValueChange = { y = it.filter { c -> c.isDigit() } },
                        label = { Text("Y 坐标") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = delayMs, onValueChange = { delayMs = it.filter { c -> c.isDigit() } },
                        label = { Text("间隔(ms)") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = delayRandomMs, onValueChange = { delayRandomMs = it.filter { c -> c.isDigit() } },
                        label = { Text("时间偏移±ms") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text("随机抖动", fontSize = 11.sp) }
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = posOffsetPx, onValueChange = { posOffsetPx = it.filter { c -> c.isDigit() || c == '-' } },
                    label = { Text("位置偏移±px") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("模拟手指抖动，防检测", fontSize = 11.sp) }
                )

                Spacer(Modifier.height(12.dp))

                // 点击模式
                Text("点击模式", fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClickMode.entries.forEach { mode ->
                        FilterChip(selected = clickMode == mode, onClick = { clickMode = mode }, label = { Text(mode.label) })
                    }
                }

                // 长按时长
                if (clickMode == ClickMode.LONG_PRESS) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = longPressDuration, onValueChange = { longPressDuration = it.filter { c -> c.isDigit() } },
                        label = { Text("长按时长(ms)") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                // 滑动终点和时长
                if (clickMode == ClickMode.SWIPE) {
                    Spacer(Modifier.height(12.dp))
                    Text("滑动终点", fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = swipeEndX, onValueChange = { swipeEndX = it.filter { c -> c.isDigit() } },
                            label = { Text("终点X") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = swipeEndY, onValueChange = { swipeEndY = it.filter { c -> c.isDigit() } },
                            label = { Text("终点Y") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = swipeDuration, onValueChange = { swipeDuration = it.filter { c -> c.isDigit() } },
                        label = { Text("滑动时长(ms)") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = maxRepeat, onValueChange = { maxRepeat = it.filter { c -> c.isDigit() || c == '-' } },
                    label = { Text("重复次数") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("留空=无限循环", fontSize = 11.sp) }
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        onConfirm(
                            point.copy(
                                label = pointLabel.ifBlank { point.label },
                                x = x.toIntOrNull()?.coerceIn(0, 4096) ?: point.x,
                                y = y.toIntOrNull()?.coerceIn(0, 4096) ?: point.y,
                                delayMs = delayMs.toLongOrNull()?.coerceIn(10, 60_000) ?: point.delayMs,
                                delayRandomMs = delayRandomMs.toLongOrNull()?.coerceIn(0, 30_000) ?: 0,
                                posOffsetPx = posOffsetPx.toIntOrNull()?.coerceIn(0, 100) ?: 0,
                                clickMode = clickMode,
                                longPressDurationMs = longPressDuration.toLongOrNull()?.coerceIn(50, 10_000) ?: 500,
                                maxRepeat = maxRepeat.toIntOrNull() ?: -1,
                                swipeEndX = swipeEndX.toIntOrNull()?.coerceIn(0, 4096) ?: point.swipeEndX,
                                swipeEndY = swipeEndY.toIntOrNull()?.coerceIn(0, 4096) ?: point.swipeEndY,
                                swipeDurationMs = swipeDuration.toLongOrNull()?.coerceIn(50, 5000) ?: 300,
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("确认") }
            }
        }
    }
}
