package com.focuslock.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ZONE: ZoneId get() = ZoneId.systemDefault()

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val TIME_SEC_FMT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
private val DATE_FMT = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
private val DATE_FULL_FMT = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

private val WEEK_CN = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun dayKey(epochMs: Long = System.currentTimeMillis()): String =
    Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalDate().toString()

fun LocalDate.toEpochMs(): Long = atStartOfDay(ZONE).toInstant().toEpochMilli()

fun fmtClock(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalTime().format(TIME_FMT)

fun fmtClockSec(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalTime().format(TIME_SEC_FMT)

fun fmtDate(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalDate().format(DATE_FMT)

fun fmtDateFull(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalDate().format(DATE_FULL_FMT)

fun fmtWeekday(epochMs: Long): String =
    WEEK_CN[Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalDate().dayOfWeek.value - 1]

fun weekdayCn(value: Int): String = WEEK_CN[(value - 1).coerceIn(0, 6)]

/** 毫秒 → "1小时23分" 这类人类可读时长 */
fun fmtDuration(ms: Long): String {
    if (ms <= 0) return "0分"
    val totalMin = ms / 60_000L
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "${h}小时${m}分"
        h > 0 -> "${h}小时"
        else -> "${m}分"
    }
}

/** 毫秒 → "01:23:45" / "23:45" */
fun fmtCountdown(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) + 999L) / 1000L
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

/** 毫秒 → "1 小时 23 分 45 秒"，用于文案 */
fun fmtLong(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) + 999L) / 1000L
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return buildString {
        if (h > 0) append("${h} 小时 ")
        if (h > 0 || m > 0) append("${m} 分 ")
        append("${s} 秒")
    }
}

/** "下次 今天 22:00" / "下次 周三 08:00" */
fun fmtNextHint(startAt: Long, now: Long = System.currentTimeMillis()): String {
    val d0 = Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate()
    val d1 = Instant.ofEpochMilli(startAt).atZone(ZONE).toLocalDate()
    val diff = d1.toEpochDay() - d0.toEpochDay()
    val dayPart = when (diff) {
        0L -> "今天"
        1L -> "明天"
        else -> weekdayCn(Instant.ofEpochMilli(startAt).atZone(ZONE).toLocalDate().dayOfWeek.value)
    }
    return "$dayPart ${fmtClock(startAt)}"
}
