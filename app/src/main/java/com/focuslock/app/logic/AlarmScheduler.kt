package com.focuslock.app.logic

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.focuslock.app.service.AlarmReceiver
import com.focuslock.app.ui.MainActivity

private const val TAG = "AlarmScheduler"

/**
 * 用 AlarmManager 兜住三件事：
 *  1. 下一个时段开始时把锁机拉起来；
 *  2. 本次锁机结束时刻精确退出；
 *  3. 锁机期间每分钟一次心跳，服务被杀掉后自动复活。
 *
 * ## 为什么开始/结束都用 setAlarmClock
 *
 * 实测（一加 Ace 6 / ColorOS 16 / Android 16）：
 *  - `setExactAndAllowWhileIdle` 会被系统降级成带窗口的批处理闹钟，
 *    离目标越远窗口越大（24 小时后直接被封顶到 1 小时），到点晚了 70 秒；
 *  - `setAlarmClock` 下发的「闹钟式」条目不做批处理，实测准点触发。
 *
 * 代价是状态栏会出现一个闹钟图标（系统把下一次锁机当成用户的闹钟展示）。
 * 为了不让这个图标整天挂着，只在「距开始不足 30 分钟」时才升级成闹钟式下发，
 * 更早的时候先排一个「预备闹钟」，到提前 30 分钟那一刻再自我升级。
 */
object AlarmScheduler {

    const val KIND_START = "start"
    const val KIND_END = "end"
    const val KIND_WATCHDOG = "watchdog"
    const val KIND_ARM = "arm"

    const val ACTION_ALARM = "com.focuslock.app.ALARM"
    const val ACTION_END_NOW = "com.focuslock.app.END_NOW"

    private const val REQ_START = 3001
    private const val REQ_END = 3002
    private const val REQ_WATCHDOG = 3003
    private const val REQ_ARM = 3004

    /** 心跳间隔 */
    const val WATCHDOG_INTERVAL_MS = 60_000L

    /** 距开始小于这个量时，改用闹钟式下发 */
    private const val ALARM_CLOCK_LEAD_MS = 30 * 60_000L

    private fun am(ctx: Context): AlarmManager? =
        ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun canScheduleExact(ctx: Context): Boolean {
        val m = am(ctx) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { m.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            true
        }
    }

    private fun pending(ctx: Context, kind: String, req: Int): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra(AlarmReceiver.EXTRA_KIND, kind)
            // data 参与 Intent 匹配，保证不同 kind 的 PendingIntent 互不覆盖
            data = android.net.Uri.parse("focuslock://alarm/$kind")
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, req, intent, flags)
    }

    /** 点状态栏闹钟图标时打开的界面 */
    private fun showIntent(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            4001,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** 闹钟式下发。返回是否成功。 */
    private fun setAsAlarmClock(m: AlarmManager, ctx: Context, at: Long, pi: PendingIntent): Boolean =
        runCatching {
            m.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent(ctx)), pi)
            true
        }.getOrDefault(false)

    /** 退化的精确/非精确下发 */
    private fun setExactOrInexact(ctx: Context, m: AlarmManager, at: Long, pi: PendingIntent) {
        runCatching {
            if (canScheduleExact(ctx)) {
                m.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }.onFailure { Log.w(TAG, "下发闹钟失败", it) }
    }

    /** 下一个时段的开始时刻 */
    fun scheduleStart(ctx: Context, at: Long) {
        val m = am(ctx) ?: return
        val pi = pending(ctx, KIND_START, REQ_START)
        val now = System.currentTimeMillis()
        val whenAt = at.coerceAtLeast(now + 1_000L)

        if (whenAt - now <= ALARM_CLOCK_LEAD_MS) {
            val ok = setAsAlarmClock(m, ctx, whenAt, pi)
            Log.i(TAG, "开始闹钟（闹钟式）$whenAt 成功=$ok")
            if (ok) {
                cancelArm(ctx)
                return
            }
            setExactOrInexact(ctx, m, whenAt, pi)
            cancelArm(ctx)
            return
        }

        // 还早：先按普通闹钟排，另外排一个「提前 30 分钟」的预备闹钟，
        // 到那一刻再升级成闹钟式下发
        setExactOrInexact(ctx, m, whenAt, pi)
        scheduleArm(ctx, whenAt - ALARM_CLOCK_LEAD_MS)
        Log.i(TAG, "开始闹钟（预排）$whenAt")
    }

    /** 预备闹钟：到点后重新评估，把开始闹钟升级成闹钟式 */
    fun scheduleArm(ctx: Context, at: Long) {
        val m = am(ctx) ?: return
        val pi = pending(ctx, KIND_ARM, REQ_ARM)
        val whenAt = at.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        setExactOrInexact(ctx, m, whenAt, pi)
    }

    fun cancelArm(ctx: Context) = cancel(ctx, KIND_ARM, REQ_ARM)

    /** 结束时刻同样用闹钟式下发，到点必然触发 */
    fun scheduleEnd(ctx: Context, at: Long) {
        val m = am(ctx) ?: return
        val pi = pending(ctx, KIND_END, REQ_END)
        val whenAt = at.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        val ok = setAsAlarmClock(m, ctx, whenAt, pi)
        if (!ok) setExactOrInexact(ctx, m, whenAt, pi)
        Log.i(TAG, "结束闹钟 $whenAt 闹钟式=$ok 可精确=${canScheduleExact(ctx)}")
    }

    fun scheduleWatchdog(ctx: Context) {
        val m = am(ctx) ?: return
        val pi = pending(ctx, KIND_WATCHDOG, REQ_WATCHDOG)
        val whenAt = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS
        setExactOrInexact(ctx, m, whenAt, pi)
    }

    fun cancelStart(ctx: Context) {
        cancel(ctx, KIND_START, REQ_START)
        cancelArm(ctx)
    }

    fun cancelEnd(ctx: Context) {
        val m = am(ctx) ?: return
        runCatching { m.cancel(pending(ctx, KIND_END, REQ_END)) }
    }

    fun cancelWatchdog(ctx: Context) = cancel(ctx, KIND_WATCHDOG, REQ_WATCHDOG)

    private fun cancel(ctx: Context, kind: String, req: Int) {
        val m = am(ctx) ?: return
        runCatching { m.cancel(pending(ctx, kind, req)) }
    }

    /** 重新排程：开机、时间变更、计划修改后调用 */
    fun reschedule(ctx: Context) {
        cancelStart(ctx)
        if (LockRuntime.isLocked) {
            scheduleWatchdog(ctx)
            LockController.evaluate(ctx, "reschedule")
            return
        }
        cancelWatchdog(ctx)
        LockController.evaluate(ctx, "reschedule")
    }

    fun cancelAll(ctx: Context) {
        cancelStart(ctx)
        cancelEnd(ctx)
        cancelWatchdog(ctx)
    }
}
