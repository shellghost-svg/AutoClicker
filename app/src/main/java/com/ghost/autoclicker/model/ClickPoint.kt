package com.ghost.autoclicker.model

data class ClickPoint(
    val id: Long = System.currentTimeMillis(),
    val label: String = "点 ${id.toString().takeLast(4)}",
    val x: Int = 540,      // 默认屏幕中央
    val y: Int = 960,
    val enabled: Boolean = true,
    val clickMode: ClickMode = ClickMode.SINGLE,
    val delayMs: Long = 100,       // 点击间隔（毫秒）
    val delayRandomMs: Long = 0,   // 时间随机偏移 ±ms
    val posOffsetPx: Int = 0,      // 位置随机偏移 ±px
    val longPressDurationMs: Long = 500,
    val maxRepeat: Int = -1,       // -1 = 无限循环
)

enum class ClickMode {
    SINGLE,   // 单击
    LONG_PRESS, // 长按
    DOUBLE     // 双击
}

data class GlobalConfig(
    val targetPackage: String? = null, // 限定APP包名，null=全局
    val isRunning: Boolean = false,
)
