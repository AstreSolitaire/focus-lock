package com.focuslock.app.util

import android.content.Context
import com.focuslock.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 时间与文案格式化。
 *
 * 这里几乎每个函数都要 [Context]，因为输出的单位词（小时/分、周一/星期几）
 * 都是需要跟随语言的字符串资源，不能写死。
 */
private val ZONE: ZoneId get() = ZoneId.systemDefault()

/** 界面语言。用于日期这类由格式串决定的输出。 */
fun appLocale(ctx: Context): Locale {
    val locales = ctx.resources.configuration.locales
    return if (locales.isEmpty) Locale.getDefault() else locales[0]
}

private fun dateFormatter(ctx: Context, patternRes: Int): DateTimeFormatter =
    DateTimeFormatter.ofPattern(ctx.getString(patternRes), appLocale(ctx))

// ---------------------------------------------------------------- 时刻

/** 24 小时制 HH:mm。刻意不跟随系统的 12/24 小时偏好：计划本身就是按 HH:mm 录入的。 */
fun fmtClock(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))

fun fmtClockSec(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US))

fun fmtDate(ctx: Context, epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalDate()
        .format(dateFormatter(ctx, R.string.fmt_date_pattern))

fun fmtDateFull(ctx: Context, epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalDate()
        .format(dateFormatter(ctx, R.string.fmt_date_full_pattern))

// ---------------------------------------------------------------- 星期

private fun weekdayRes(value: Int): Int = when (value) {
    1 -> R.string.weekday_mon
    2 -> R.string.weekday_tue
    3 -> R.string.weekday_wed
    4 -> R.string.weekday_thu
    5 -> R.string.weekday_fri
    6 -> R.string.weekday_sat
    else -> R.string.weekday_sun
}

private fun weekdayShortRes(value: Int): Int = when (value) {
    1 -> R.string.weekday_short_mon
    2 -> R.string.weekday_short_tue
    3 -> R.string.weekday_short_wed
    4 -> R.string.weekday_short_thu
    5 -> R.string.weekday_short_fri
    6 -> R.string.weekday_short_sat
    else -> R.string.weekday_short_sun
}

/** DayOfWeek.value（1=周一 … 7=周日）→ 本地化星期名 */
fun weekdayFull(ctx: Context, value: Int): String =
    ctx.getString(weekdayRes(value.coerceIn(1, 7)))

/** 紧凑选择器里用的单字缩写 */
fun weekdayShort(ctx: Context, value: Int): String =
    ctx.getString(weekdayShortRes(value.coerceIn(1, 7)))

fun fmtWeekday(ctx: Context, epochMs: Long): String =
    weekdayFull(ctx, Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalDate().dayOfWeek.value)

fun dayKey(epochMs: Long = System.currentTimeMillis()): String =
    Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalDate().toString()

fun LocalDate.toEpochMs(): Long = atStartOfDay(ZONE).toInstant().toEpochMilli()

// ---------------------------------------------------------------- 时长

/** 毫秒 →「1小时23分」/「1 hr 23 min」 */
fun fmtDuration(ctx: Context, ms: Long): String {
    if (ms <= 0) return ctx.getString(R.string.fmt_duration_zero)
    val totalMin = ms / 60_000L
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> ctx.getString(R.string.fmt_duration_hm, h, m)
        h > 0 -> ctx.getString(R.string.fmt_duration_hours, h)
        else -> ctx.getString(R.string.fmt_duration_minutes, m)
    }
}

/** 毫秒 →「1 小时 23 分 45 秒」，用于通知里更精确的表述 */
fun fmtDurationLong(ctx: Context, ms: Long): String {
    val total = (ms.coerceAtLeast(0L) + 999L) / 1000L
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return ctx.getString(R.string.fmt_duration_hms, h, m, s)
}

/** 毫秒 →「01:23:45」/「23:45」。纯数字，不需要本地化。 */
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

/** 「今天 22:00」/「周三 08:00」 */
fun fmtNextHint(ctx: Context, startAt: Long, now: Long = System.currentTimeMillis()): String {
    val d0 = Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate()
    val d1 = Instant.ofEpochMilli(startAt).atZone(ZONE).toLocalDate()
    val dayPart = when (d1.toEpochDay() - d0.toEpochDay()) {
        0L -> ctx.getString(R.string.fmt_today)
        1L -> ctx.getString(R.string.fmt_tomorrow)
        else -> weekdayFull(ctx, d1.dayOfWeek.value)
    }
    return "$dayPart ${fmtClock(startAt)}"
}
