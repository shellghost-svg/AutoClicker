package com.ghost.autoclicker.model

import java.util.concurrent.atomic.AtomicLong

data class ClickPoint(
    val id: Long = _idGenerator.incrementAndGet(),
    val label: String = "点${id.toString().takeLast(4)}",
    val x: Int = 540,
    val y: Int = 960,
    val enabled: Boolean = true,
    val clickMode: ClickMode = ClickMode.SINGLE,
    val delayMs: Long = 100,
    val delayRandomMs: Long = 0,
    val posOffsetPx: Int = 0,
    val longPressDurationMs: Long = 500,
    val maxRepeat: Int = -1,
    // 滑动参数
    val swipeEndX: Int = 540,
    val swipeEndY: Int = 400,
    val swipeDurationMs: Long = 300,
) {
    companion object {
        private val _idGenerator = AtomicLong(1000)
    }
}

enum class ClickMode {
    SINGLE,
    LONG_PRESS,
    DOUBLE,
    SWIPE;

    val label: String
        get() = when (this) {
            SINGLE -> "单击"
            LONG_PRESS -> "长按"
            DOUBLE -> "双击"
            SWIPE -> "滑动"
        }
}

data class GlobalConfig(
    val targetPackage: String? = null,
    val isRunning: Boolean = false,
    val totalClicks: Long = 0,
)

// 快捷预设
data class Preset(
    val name: String,
    val description: String,
    val icon: String,
    val points: List<ClickPoint>,
)

val presets = listOf(
    Preset(
        name = "游戏挂机",
        description = "单点循环，间隔1秒，防检测偏移",
        icon = "🎮",
        points = listOf(
            ClickPoint(label = "挂机点", x = 540, y = 1200, delayMs = 1000, delayRandomMs = 200, posOffsetPx = 5, maxRepeat = -1)
        )
    ),
    Preset(
        name = "抢红包",
        description = "快速双击，间隔50ms",
        icon = "🧧",
        points = listOf(
            ClickPoint(label = "红包位置", x = 540, y = 800, clickMode = ClickMode.DOUBLE, delayMs = 50, maxRepeat = 3)
        )
    ),
    Preset(
        name = "刷视频",
        description = "自动上滑，间隔3秒",
        icon = "📱",
        points = listOf(
            ClickPoint(label = "上滑", x = 540, y = 1500, swipeEndX = 540, swipeEndY = 400, clickMode = ClickMode.SWIPE, delayMs = 3000, delayRandomMs = 500, swipeDurationMs = 400)
        )
    ),
    Preset(
        name = "连击刷怪",
        description = "多点快速攻击",
        icon = "⚔️",
        points = listOf(
            ClickPoint(label = "技能1", x = 200, y = 1800, delayMs = 500, maxRepeat = -1),
            ClickPoint(label = "技能2", x = 540, y = 1800, delayMs = 200, maxRepeat = -1),
            ClickPoint(label = "普攻", x = 800, y = 1800, delayMs = 100, maxRepeat = -1),
        )
    ),
    Preset(
        name = "自动签到",
        description = "单点长按，间隔5秒",
        icon = "✅",
        points = listOf(
            ClickPoint(label = "签到按钮", x = 540, y = 1600, clickMode = ClickMode.LONG_PRESS, delayMs = 5000, longPressDurationMs = 1000, maxRepeat = 1)
        )
    ),
)
