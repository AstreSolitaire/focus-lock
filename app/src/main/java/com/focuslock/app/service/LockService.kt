package com.focuslock.app.service

import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.content.Context
import android.content.BroadcastReceiver
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.focuslock.app.R
import com.focuslock.app.data.Prefs
import com.focuslock.app.logic.AlarmScheduler
import com.focuslock.app.logic.LockController
import com.focuslock.app.logic.LockRuntime
import com.focuslock.app.util.AccessibilityUtil
import com.focuslock.app.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "LockService"

/**
 * 锁机期间的常驻前台服务。
 *
 * 职责：
 *  - 每秒走一次 [LockController.onTick]，驱动倒计时与时长累加；
 *  - 刷新常驻通知里的剩余时间；
 *  - 每 15 秒向 [LockController.evaluate] 汇报一次，兜住计划变更、无障碍被关等异常；
 *  - 被划掉任务时立刻借助闹钟把自己拉回来。
 */
class LockService : Service() {

    companion object {
        const val ACTION_ENSURE = "com.focuslock.app.action.ENSURE"

        /** 通知刷新的节流间隔（单位：tick，1 tick = 1 秒） */
        private const val NOTIFY_EVERY_TICKS = 5
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null
    private var warnedAccessibility = false

    /**
     * 屏幕亮起 / 用户解锁时，把锁屏界面顶到最前面。
     *
     * 为什么需要它：修掉「屏幕常亮」之后，手机能正常息屏了，但屏幕重新亮起时
     * 系统会先显示它自己的锁屏（就是那张带通知预览的界面），我的锁机界面被压在
     * 后面 —— 观感上就是「锁机好像没生效，点了也没反应」。
     * 这里在亮屏和解锁的瞬间把锁机界面重新拉到前台，把它盖回系统锁屏之上。
     *
     * 注意：ACTION_SCREEN_ON / ACTION_USER_PRESENT 只能动态注册，不能写进清单。
     */
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!LockRuntime.isLocked) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    Log.i(TAG, "屏幕亮起 / 解锁，把锁屏顶回前台")
                    LockController.launchLockUi(this@LockService)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        runCatching {
            registerReceiver(
                screenStateReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_USER_PRESENT)
                }
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LockController.restoreIfNeeded(this)
        promoteToForeground()
        startLoop()
        return START_STICKY
    }

    private fun promoteToForeground() {
        val s = LockRuntime.session
        val notif = Notifications.buildLockNotification(
            this,
            s?.scheduleName ?: getString(R.string.lock_default_schedule_name),
            LockRuntime.state.value.remainingMs,
            s?.strict == true
        )
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
            ServiceCompat.startForeground(this, Notifications.ID_LOCK, notif, type)
        } catch (t: Throwable) {
            Log.w(TAG, "转前台失败", t)
        }
    }

    private fun startLoop() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            var ticks = 0
            while (isActive) {
                val alive = LockController.onTick(this@LockService)
                if (!alive) {
                    Notifications.cancelLock(this@LockService)
                    shutdown()
                    return@launch
                }
                ticks++

                // 通知里的倒计时每 5 秒刷新一次就够了。每秒重发通知会让系统
                // （尤其 ColorOS）每次都去查一遍前台应用，日志里刷满权限拒绝记录。
                if (ticks % NOTIFY_EVERY_TICKS == 0) {
                    val st = LockRuntime.state.value
                    Notifications.updateLock(this@LockService, st.scheduleName, st.remainingMs, st.strict)
                }

                if (ticks % 15 == 0) {
                    LockController.evaluate(this@LockService, "service-heartbeat")
                    checkAccessibility(LockRuntime.state.value.strict)
                    // 心跳闹钟续期，保证服务被杀后还能被叫醒
                    AlarmScheduler.scheduleWatchdog(this@LockService)
                }
                delay(1_000L)
            }
        }
    }

    /**
     * 严格模式下无障碍一旦失效，锁机就形同虚设，必须提醒用户。
     *
     * 关键：**不能只看系统设置里的那个开关。**
     * App 更新、或进程被系统杀掉之后，AccessibilityManagerService 会把服务标记成
     * Crashed 并且**不再自动重绑**，而「设置 → 无障碍」里依旧显示「已开启」。
     * 结果就是拦截已经停了，用户在界面上完全看不出来 —— 只能靠服务自己有没有
     * 真的连上来判断（同一个进程，用静态标志最直接）。
     */
    private fun checkAccessibility(strict: Boolean) {
        if (!strict) return
        val enabledInSettings = AccessibilityUtil.isEnabled(this)
        val connected = AppWatchService.running
        if (enabledInSettings && connected) {
            warnedAccessibility = false
            return
        }
        if (warnedAccessibility) return
        Log.w(TAG, "无障碍失效：设置里开启=$enabledInSettings，实际连上=$connected")
        // 只有通知真的发出去了才记「已提醒」，否则没权限时会永远哑火
        warnedAccessibility = Notifications.notifyAccessibilityLost(
            this,
            crashedWhileEnabled = enabledInSettings && !connected
        )
    }

    private fun shutdown() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 从最近任务里划掉：不改变锁机状态，立刻用闹钟把自己叫回来
        Log.i(TAG, "任务被移除，尝试自恢复")
        AlarmScheduler.scheduleWatchdog(this)
        LockController.evaluate(this, "task-removed")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenStateReceiver) }
        scope.cancel()
        super.onDestroy()
    }
}
