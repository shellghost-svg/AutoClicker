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
    var maxRepeat by remember { mutableStateOf(point.maxRepeat.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 标题
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("编辑点击点", fontSize = 18.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 名称
                OutlinedTextField(
                    value = pointLabel,
                    onValueChange = { pointLabel = it },
                    label = { Text("名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 坐标
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = x,
                        onValueChange = { x = it },
                        label = { Text("X 坐标") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = y,
                        onValueChange = { y = it },
                        label = { Text("Y 坐标") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 点击间隔
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = delayMs,
                        onValueChange = { delayMs = it },
                        label = { Text("间隔(ms)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = delayRandomMs,
                        onValueChange = { delayRandomMs = it },
                        label = { Text("时间偏移±ms") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text("每次间隔随机抖动", fontSize = 11.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 位置偏移
                OutlinedTextField(
                    value = posOffsetPx,
                    onValueChange = { posOffsetPx = it },
                    label = { Text("位置偏移±px") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("模拟手指抖动，防检测", fontSize = 11.sp) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 点击模式
                Text("点击模式", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClickMode.entries.forEach { mode ->
                        FilterChip(
                            selected = clickMode == mode,
                            onClick = { clickMode = mode },
                            label = { Text(mode.label) }
                        )
                    }
                }

                // 长按时长
                if (clickMode == ClickMode.LONG_PRESS) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = longPressDuration,
                        onValueChange = { longPressDuration = it },
                        label = { Text("长按时长(ms)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 重复次数
                OutlinedTextField(
                    value = maxRepeat,
                    onValueChange = { maxRepeat = it },
                    label = { Text("重复次数") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("-1 或留空 = 无限循环", fontSize = 11.sp) }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 确认按钮
                Button(
                    onClick = {
                        onConfirm(
                            point.copy(
                                label = pointLabel.ifBlank { point.label },
                                x = x.toIntOrNull() ?: point.x,
                                y = y.toIntOrNull() ?: point.y,
                                delayMs = delayMs.toLongOrNull() ?: point.delayMs,
                                delayRandomMs = delayRandomMs.toLongOrNull() ?: 0,
                                posOffsetPx = posOffsetPx.toIntOrNull() ?: 0,
                                clickMode = clickMode,
                                longPressDurationMs = longPressDuration.toLongOrNull() ?: 500,
                                maxRepeat = maxRepeat.toIntOrNull() ?: -1
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("确认")
                }
            }
        }
    }
}
