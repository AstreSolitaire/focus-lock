package com.focuslock.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
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

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
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
            s?.scheduleName ?: "专注中",
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

    /** 严格模式下如果无障碍被关掉，锁机就形同虚设，提醒用户去打开 */
    private fun checkAccessibility(strict: Boolean) {
        if (!strict) return
        if (AccessibilityUtil.isEnabled(this)) {
            warnedAccessibility = false
            return
        }
        if (warnedAccessibility) return
        warnedAccessibility = true
        Notifications.notifyWarning(
            this,
            "锁机拦截已失效",
            "「专注锁机」的无障碍服务被关闭了，白名单之外的应用暂时拦不住。请在系统设置中重新开启。"
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
        scope.cancel()
        super.onDestroy()
    }
}
