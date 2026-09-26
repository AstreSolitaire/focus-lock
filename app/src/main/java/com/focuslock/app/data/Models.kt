package com.focuslock.app.data

import java.util.Locale

const val MINUTES_PER_DAY = 1440

/**
 * 一天的某个时间段，以「当天 0 点起的分钟数」表示。
 *
 * [endMinute] 小于等于 [startMinute] 时视为跨过午夜，例如 22:30 - 06:00。
 * 起止完全相同按整 24 小时处理。
 */
data class TimeRange(
    val startMinute: Int,
    val endMinute: Int
) {
    val durationMinutes: Int
        get() = if (endMinute > startMinute) {
            endMinute - startMinute
        } else {
            MINUTES_PER_DAY - startMinute + endMinute
        }

    val crossesMidnight: Boolean get() = endMinute <= startMinute

    /** 起止相同表示整整一天，不要写成「次日」那样容易误读 */
    val isWholeDay: Boolean get() = durationMinutes >= MINUTES_PER_DAY

    fun label(): String = buildString {
        append(fmtMinute(startMinute))
        append(" - ")
        append(fmtMinute(endMinute))
        when {
            isWholeDay -> append("（全天）")
            crossesMidnight -> append(" 次日")
        }
    }

    fun normalize(): TimeRange = copy(
        startMinute = startMinute.coerceIn(0, MINUTES_PER_DAY - 1),
        endMinute = endMinute.coerceIn(0, MINUTES_PER_DAY - 1)
    )

    companion object {
        fun fmtMinute(m: Int): String {
            val safe = ((m % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
            return String.format(Locale.US, "%02d:%02d", safe / 60, safe % 60)
        }
    }
}

/**
 * 一条锁机计划：在 [days] 指定的星期几、[ranges] 指定的时间段内生效。
 */
data class Schedule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    /** 1 = 周一 … 7 = 周日，与 java.time.DayOfWeek.value 一致 */
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    /** 新建的计划默认不预置任何时间段，由用户自己添加 */
    val ranges: List<TimeRange> = emptyList(),
    /** 是否启用白名单；关闭时除系统必需应用外全部拦截 */
    val useWhitelist: Boolean = true,
    /** 严格模式：锁机期间屏蔽系统设置、下拉通知栏，并阻止卸载 */
    val strict: Boolean = true
) {
    /** 该计划一周内的总锁机分钟数 */
    val weeklyMinutes: Int get() = days.size * ranges.sumOf { it.durationMinutes }
}

/**
 * 一次即将生效（或正在生效）的锁机时段。
 *
 * [keys] 是构成这个时段的全部原始区间起点，用于在时段结束后做「已结束」标记，
 * 避免因为时钟回拨被重复开启。
 */
data class LockWindow(
    val scheduleId: String,
    val scheduleName: String,
    val startAt: Long,
    val endAt: Long,
    val useWhitelist: Boolean,
    val strict: Boolean,
    val keys: Set<Long>
)

/**
 * 持久化的锁机会话。时长被固定为 [durMs] 后再开始计时，
 * 结束时刻不受后续系统时间改动影响。
 */
data class LockSession(
    val scheduleId: String,
    val scheduleName: String,
    /** 基线墙钟时刻 */
    val startedAt: Long,
    /** 基线单调时刻（SystemClock.elapsedRealtime） */
    val startedElapsed: Long,
    /** 本次锁机总时长 */
    val durMs: Long,
    val useWhitelist: Boolean,
    val strict: Boolean,
    /** 构成本次锁机的原始区间起点，结束后整体写入「已完成」集合 */
    val intervalKeys: List<Long>
) {
    val endAt: Long get() = startedAt + durMs
}
