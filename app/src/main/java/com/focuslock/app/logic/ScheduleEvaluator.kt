package com.focuslock.app.logic

import com.focuslock.app.data.LockWindow
import com.focuslock.app.data.Schedule
import java.time.Instant
import java.time.ZoneId

/** 由某条计划展开出的一段具体锁机区间 */
data class Interval(
    val start: Long,
    val end: Long,
    val schedule: Schedule
)

/**
 * 计划 → 具体锁机区间的换算。
 *
 * 所有换算都以本地时区为准，每天 0 点为基准点。跨午夜的时段
 * （如 22:30 - 06:00）会把结束时刻落到次日。
 */
object ScheduleEvaluator {

    private const val MIN_MS = 60_000L

    /**
     * 展开 [around] 前后若干天内所有生效的锁机区间。
     *
     * 往前看 2 天是为了覆盖「昨天 23:00 开始、今天 07:00 结束」这类还在生效中的区间。
     */
    fun intervals(
        schedules: List<Schedule>,
        around: Long,
        backDays: Long = 2,
        forwardDays: Long = 9,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Interval> {
        if (schedules.isEmpty()) return emptyList()
        val today = Instant.ofEpochMilli(around).atZone(zone).toLocalDate()
        val out = ArrayList<Interval>()
        for (s in schedules) {
            if (!s.enabled || s.days.isEmpty() || s.ranges.isEmpty()) continue
            for (offset in -backDays..forwardDays) {
                val d = today.plusDays(offset)
                if (d.dayOfWeek.value !in s.days) continue
                val dayStartMs = d.atStartOfDay(zone).toInstant().toEpochMilli()
                for (r in s.ranges) {
                    val st = dayStartMs + r.startMinute * MIN_MS
                    val en = st + r.durationMinutes * MIN_MS
                    if (en > st) out += Interval(st, en, s)
                }
            }
        }
        return out.sortedBy { it.start }
    }

    /**
     * 当前正在生效的锁机时段。多个计划重叠时：
     * 结束时间取最晚的一个，严格模式任一生效即生效，
     * 白名单设置任一生效即生效（取更宽松的一侧，避免误伤）。
     */
    fun activeWindow(
        schedules: List<Schedule>,
        now: Long,
        excludeKeys: Set<Long> = emptySet(),
        zone: ZoneId = ZoneId.systemDefault()
    ): LockWindow? {
        val hit = intervals(schedules, now, zone = zone).filter {
            now >= it.start && now < it.end && it.start !in excludeKeys
        }
        if (hit.isEmpty()) return null
        return LockWindow(
            scheduleId = hit.first().schedule.id,
            scheduleName = hit.map { it.schedule.name }.distinct().joinToString(" + "),
            startAt = hit.minOf { it.start },
            endAt = hit.maxOf { it.end },
            useWhitelist = hit.any { it.schedule.useWhitelist },
            strict = hit.any { it.schedule.strict },
            keys = hit.map { it.start }.toSet()
        )
    }

    /** 下一个即将开始的时段，用于展示「距下次锁机」 */
    fun nextWindow(
        schedules: List<Schedule>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Interval? = intervals(schedules, now, zone = zone).firstOrNull { it.start > now }

    /** 今天剩下的全部时段，用于首页时间轴 */
    fun todayIntervals(
        schedules: List<Schedule>,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Interval> {
        val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return intervals(schedules, now)
            .filter { it.end > dayStart && it.start < dayEnd }
            .sortedBy { it.start }
    }
}
