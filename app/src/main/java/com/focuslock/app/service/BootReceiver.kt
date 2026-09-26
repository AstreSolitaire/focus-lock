package com.focuslock.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.focuslock.app.logic.AlarmScheduler
import com.focuslock.app.logic.LockController
import com.focuslock.app.logic.LockRuntime

private const val TAG = "BootReceiver"

/**
 * 开机、应用更新、系统时间被修改时的恢复入口。
 *
 * 重启不会结束锁机：会话的时长存在磁盘上，开机后重建计时基线继续走完剩余时间。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "收到广播：$action")

        // 进程可能是被这个广播冷启动的，先从磁盘恢复锁机会话
        LockController.restoreIfNeeded(context)

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                AlarmScheduler.cancelAll(context)
                LockController.evaluate(context, "boot")
                // 开机后把无障碍服务之外的状态再确认一遍
                if (LockRuntime.isLocked) {
                    AlarmScheduler.scheduleWatchdog(context)
                }
            }

            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED -> {
                // 时间被改动：重新评估。进行中的会话由双时钟逻辑保护，不会提前结束
                LockController.evaluate(context, "time-changed")
            }
        }
    }
}
