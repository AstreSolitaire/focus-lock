package com.focuslock.app.logic

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import android.util.Log
import com.focuslock.app.data.LockSession
import com.focuslock.app.data.LockWindow
import com.focuslock.app.data.Prefs
import com.focuslock.app.data.StatsStore
import com.focuslock.app.service.LockService
import com.focuslock.app.ui.LockActivity
import com.focuslock.app.util.Notifications

private const val TAG = "LockController"

/**
 * 锁机的总调度：什么时候锁、锁多久、什么时候解。
 *
 * 抗篡改设计（这是「重启/改时间都不能提前结束锁机」的实现核心）：
 *
 * - 开始锁机时把时长固定成 [LockSession.durMs]，之后只看「已经过去了多久」，
 *   不再依赖「现在几点」。倒计时同时用两个时钟计算：
 *     · 墙钟   = startedAt + durMs - System.currentTimeMillis()
 *     · 单调钟 = durMs - (elapsedRealtime() - startedElapsed)
 *   取两者较大值。把系统时间往后调，墙钟剩余变负，但单调钟不受影响；
 *   把时间往前调，墙钟剩余暴涨，则用单调钟把基线重新锚定回来。
 *
 * - 单调时钟在重启后归零。此时（elapsedRealtime() < startedElapsed）改用墙钟剩余量
 *   重建基线，并参考历史观测到的最大墙钟时刻，抵消「重启同时把时间往回拨」。
 *
 * - 每个时段结束后把区间起点写入「已完成」集合，即使时钟被回拨导致该时段
 *   在时间轴上再次「生效」，也不会重复开启锁机。
 */
object LockController {

    /** 每次评估的最大递归深度，避免异常状态下无限自调用 */
    private var depth = 0

    /**
     * 从磁盘恢复进行中的锁机会话。
     *
     * 进程可能是被闹钟 / 开机广播 / 无障碍服务冷启动的，此时内存里没有会话。
     * 每个入口（Application、锁机服务、无障碍服务、闹钟接收器）都要先调它。
     */
    fun restoreIfNeeded(ctx: Context) {
        if (LockRuntime.session != null) return
        val saved = Prefs.session ?: return
        LockRuntime.setSession(saved)
        LockRuntime.setRemaining(remainingOf(ctx, saved).coerceAtLeast(0L))
        refreshAllowed(ctx)
        Log.i(TAG, "已从磁盘恢复锁机会话：${saved.scheduleName}")
    }

    fun evaluate(ctx: Context, reason: String) {
        if (depth > 2) return
        depth++
        try {
            evaluateInner(ctx, reason)
        } catch (t: Throwable) {
            Log.w(TAG, "evaluate($reason) 失败", t)
        } finally {
            depth--
        }
    }

    private fun evaluateInner(ctx: Context, reason: String) {
        val now = System.currentTimeMillis()
        Prefs.observeWall(now)

        if (!Prefs.masterEnabled) {
            if (LockRuntime.isLocked) endLock(ctx, "总开关已关闭", completed = false)
            AlarmScheduler.cancelAll(ctx)
            ServiceLauncher.stopLockService(ctx)
            return
        }

        val current = LockRuntime.session
        if (current != null) {
            val remain = remainingOf(ctx, current)
            if (remain <= 0L) {
                endLock(ctx, "时段结束", completed = true)
                evaluate(ctx, "结束后重排")
                return
            }
            LockRuntime.setRemaining(remain)
            AlarmScheduler.scheduleEnd(ctx, System.currentTimeMillis() + remain)
            AlarmScheduler.scheduleWatchdog(ctx)
            ServiceLauncher.startLockService(ctx)
            applyUninstallBlock(ctx, current.strict)
            return
        }

        // 没有进行中的会话：看看是否该开始
        val window = ScheduleEvaluator.activeWindow(
            Prefs.schedules,
            now,
            Prefs.completedWindowKeys()
        )
        if (window != null) {
            startLock(ctx, window)
            return
        }

        // 空闲状态
        LockRuntime.setSession(null)
        AlarmScheduler.cancelEnd(ctx)
        AlarmScheduler.cancelWatchdog(ctx)
        ServiceLauncher.stopLockService(ctx)
        applyUninstallBlock(ctx, false)
        val next = ScheduleEvaluator.nextWindow(Prefs.schedules, now)
        if (next != null) AlarmScheduler.scheduleStart(ctx, next.start)
    }

    // ------------------------------------------------------------------ 开始

    fun startLock(ctx: Context, w: LockWindow) {
        val now = System.currentTimeMillis()
        val dur = (w.endAt - now).coerceAtLeast(1_000L)
        val session = LockSession(
            scheduleId = w.scheduleId,
            scheduleName = w.scheduleName,
            startedAt = now,
            startedElapsed = SystemClock.elapsedRealtime(),
            durMs = dur,
            useWhitelist = w.useWhitelist,
            strict = w.strict,
            intervalKeys = w.keys.toList()
        )
        Prefs.session = session
        Prefs.observeWall(now)
        LockRuntime.setSession(session)
        refreshAllowed(ctx)
        StatsStore.beginSession(now)
        // 已经开始锁机，下一次开始的闹钟（含预备闹钟）都不需要了
        AlarmScheduler.cancelStart(ctx)
        AlarmScheduler.scheduleEnd(ctx, now + dur)
        AlarmScheduler.scheduleWatchdog(ctx)
        ServiceLauncher.startLockService(ctx)
        launchLockUi(ctx)
        applyUninstallBlock(ctx, w.strict)
        if (w.strict && Prefs.lockScreenOnStart) FocusAdmin.lockNow(ctx)
        if (Prefs.vibrate) Haptics.pulse(ctx, longArrayOf(0, 60, 80, 60))
        // 刻意不再单独发一条「锁机已开始」通知：同一个应用的多个通知会被系统折叠成
        // 一组，常驻通知上的「结束锁机」按钮会被藏进展开层，用户根本点不到。
        // 常驻通知的标题本身就会变成「专注中 · 计划名」，信息不丢。
        Log.i(TAG, "锁机开始：${w.scheduleName}，时长 ${dur / 1000}s")
    }

    // ------------------------------------------------------------------ 结束

    fun endLock(ctx: Context, reason: String, completed: Boolean) {
        val s = LockRuntime.session
        val now = System.currentTimeMillis()
        if (s != null) {
            StatsStore.endSession(now, s.scheduleName, completed)
            // 标记构成该时段的全部区间为已完成，避免时钟回拨后被重复触发
            s.intervalKeys.forEach { Prefs.addCompletedWindow(it) }
            Notifications.notifyLockEnd(ctx, s.scheduleName, reason)
            if (Prefs.vibrate) Haptics.pulse(ctx, longArrayOf(0, 40, 60, 40))
            Log.i(TAG, "锁机结束：${s.scheduleName}，原因：$reason")
        }
        Prefs.session = null
        LockRuntime.setSession(null)
        AlarmScheduler.cancelEnd(ctx)
        AlarmScheduler.cancelWatchdog(ctx)
        ServiceLauncher.stopLockService(ctx)
        applyUninstallBlock(ctx, false)
    }

    /** 手动立刻结束（仅非严格模式提供，或紧急解锁校验通过后） */
    fun forceEnd(ctx: Context, reason: String) {
        endLock(ctx, reason, completed = false)
        evaluate(ctx, "强制结束后重排")
    }

    // ------------------------------------------------------------------ 计时

    /**
     * 由锁机服务每秒调用。返回 false 表示会话已经结束，服务应停止。
     */
    fun onTick(ctx: Context): Boolean {
        val s = LockRuntime.session ?: return false
        Prefs.observeWall(System.currentTimeMillis())
        val remain = remainingOf(ctx, s)
        if (remain <= 0L) {
            endLock(ctx, "时段结束", completed = true)
            evaluate(ctx, "结束后重排")
            return false
        }
        LockRuntime.setRemaining(remain)
        StatsStore.tick(System.currentTimeMillis())
        return true
    }

    /**
     * 剩余毫秒。核心的时钟判断在 [Countdown] 里，这里只负责按结果落库。
     */
    fun remainingOf(ctx: Context, s: LockSession): Long {
        val result = Countdown.evaluate(
            startedAt = s.startedAt,
            startedElapsed = s.startedElapsed,
            durMs = s.durMs,
            nowWall = System.currentTimeMillis(),
            nowElapsed = SystemClock.elapsedRealtime(),
            maxWallSeen = Prefs.maxWallSeen
        )
        return when (result) {
            is Countdown.Result.Remaining -> result.ms
            is Countdown.Result.Rebaseline -> {
                rebaseline(ctx, s, result.ms)
                result.ms
            }
        }
    }

    private fun rebaseline(ctx: Context, s: LockSession, durMs: Long) {
        val now = System.currentTimeMillis()
        val fresh = s.copy(
            startedAt = now,
            startedElapsed = SystemClock.elapsedRealtime(),
            durMs = durMs.coerceAtLeast(0L)
        )
        Prefs.session = fresh
        LockRuntime.setSession(fresh)
        if (durMs > 0L) AlarmScheduler.scheduleEnd(ctx, now + durMs)
        Log.i(TAG, "计时基线重建，剩余 ${durMs / 1000}s")
    }

    // ------------------------------------------------------------------ 白名单

    /**
     * 重新计算「锁机期间允许启动的包」。
     * 无论白名单怎么配，以下几类永远放行，否则会连电话都打不出去：
     *  - 本应用自己（锁屏界面）
     *  - 输入法（否则任何输入框都没法用）
     *  - 系统默认拨号盘（紧急呼叫）
     *  - android 系统包
     */
    fun refreshAllowed(ctx: Context) {
        val allowed = mutableSetOf<String>()
        allowed += ctx.packageName
        allowed += "android"
        allowed += "com.android.phone"
        allowed += "com.android.server.telecom"

        runCatching {
            val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            imm?.enabledInputMethodList?.forEach { allowed += it.packageName }
            imm?.inputMethodList?.forEach { allowed += it.packageName }
        }

        runCatching {
            val dial = Intent(Intent.ACTION_DIAL)
            ctx.packageManager
                .resolveActivity(dial, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
                ?.let { allowed += it }
        }

        // 系统的权限弹窗、安装弹窗、ColorOS 的启动确认框必须放行：
        // 白名单应用首次运行要申请权限，拦掉这些框等于应用没法用
        allowed += SYSTEM_DIALOG_PACKAGES

        // 记住默认桌面，按 Home 键时要用它来判断该不该弹回锁屏
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = ctx.packageManager
                .resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            if (resolved != null && resolved != ctx.packageName) {
                LockRuntime.setDefaultLauncher(resolved)
                allowed -= resolved
            }
        }

        val s = LockRuntime.session
        if (s != null && s.useWhitelist) {
            allowed += Prefs.whitelist
        }
        LockRuntime.setAllowed(allowed)
        Log.i(TAG, "允许清单 ${allowed.size} 项，白名单${if (s?.useWhitelist == true) "已启用" else "未启用"}")
    }

    /** 判断某个包在锁机期间是否放行（供无障碍服务调用） */
    fun isAllowed(pkg: String): Boolean {
        val s = LockRuntime.session ?: return true
        if (pkg == "com.android.systemui") {
            return !(s.strict && Prefs.strictBlockShade)
        }
        if (pkg == SETTINGS_PACKAGE || pkg == SETTINGS_PACKAGE_ALT) {
            return !(s.strict && Prefs.strictBlockSettings)
        }
        return pkg in LockRuntime.allowedPackages
    }

    /**
     * 无论如何都拦住的包 —— 连「刚按过电源键」的宽限期也不例外。
     *
     * 绕开锁机最省事的办法是长按电源键唤起本机 AI 助手（一加的小布），
     * 再让助手替你打开微信或系统设置。电源键本身绝不能拦（否则没法关机），
     * 所以只能在这一层把助手堵死；桌面同理，否则按 Home 就跑了。
     */
    private val NEVER_ALLOWED_PREFIXES = listOf(
        // 一加 / OPPO 的小布助手及其语音、场景服务
        "com.coloros.assistantscreen",
        "com.coloros.speechassist",
        "com.heytap.speechassist",
        "com.oplus.aiunit",
        "com.oplus.ocs",
        "com.coloros.ocs",
        "com.oplus.smartengine",
        "com.oplus.pantanal",
        // Google 助手
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.googleassistant",
        // 各家桌面（按 Home 键的落点）
        "com.android.launcher",
        "com.oplus.launcher",
        "com.oppo.launcher",
        "com.coloros.launcher"
    )

    fun isNeverAllowed(pkg: String): Boolean {
        if (NEVER_ALLOWED_PREFIXES.any { pkg.startsWith(it) }) return true
        val launcher = LockRuntime.defaultLauncher
        return launcher != null && pkg == launcher
    }

    /**
     * 系统自带的弹窗类应用，锁机期间也要放行。
     *
     * 拦掉它们会造成两种坏体验：一是从锁屏启动白名单应用时，系统先弹的
     * 「确认启动 / 授予权限」框被弹回，应用根本打不开；二是白名单应用自己
     * 申请权限时对话框被吞掉。这些框本身不具备绕过锁机的能力。
     */
    private val SYSTEM_DIALOG_PACKAGES = setOf(
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.oplus.securitypermission"
        // 注意：com.android.systemui 不在这里 —— 它由 isAllowed 里单独的
        // 严格模式分支处理，放进来反而会被那一段先截住，徒增困惑
    )

    // ------------------------------------------------------------------ 杂项

    private fun applyUninstallBlock(ctx: Context, active: Boolean) {
        if (!Prefs.blockUninstall) {
            if (FocusAdmin.isAdminActive(ctx)) FocusAdmin.setUninstallBlocked(ctx, false)
            return
        }
        if (FocusAdmin.isAdminActive(ctx)) {
            FocusAdmin.setUninstallBlocked(ctx, active)
        }
    }

    /** 把锁屏界面拉到最前 */
    fun launchLockUi(ctx: Context) {
        if (!LockRuntime.isLocked) return
        val now = System.currentTimeMillis()
        if (now - LockRuntime.lastForceShowAt < 250L) return
        LockRuntime.lastForceShowAt = now
        try {
            ctx.startActivity(LockActivity.intent(ctx))
        } catch (t: Throwable) {
            Log.w(TAG, "直接拉起锁屏失败，改用全屏通知", t)
            Notifications.notifyFullScreenFallback(ctx)
        }
    }

    /** 是否正在充电（锁机界面会显示，用于区分「在学习」还是「在充电玩」） */
    fun isCharging(ctx: Context): Boolean = runCatching {
        val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }.getOrDefault(false)

    const val SETTINGS_PACKAGE = "com.android.settings"
    const val SETTINGS_PACKAGE_ALT = "com.android.settings.intelligence"
}
