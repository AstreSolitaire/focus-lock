package com.focuslock.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focuslock.app.R
import android.util.Log
import com.focuslock.app.logic.AlarmScheduler
import com.focuslock.app.logic.LockController
import com.focuslock.app.logic.LockRuntime
import com.focuslock.app.logic.ServiceLauncher

private const val TAG = "AlarmReceiver"

/**
 * 三类闹钟的统一入口：
 *  - start：下一个时段开始
 *  - end：本次锁机结束
 *  - watchdog：每分钟心跳，负责把被系统杀掉的锁机服务拉回来
 *
 * 进程可能是被闹钟冷启动的，所以每次都要先从持久化状态恢复会话。
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_KIND = "kind"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        LockController.restoreIfNeeded(context)

        when (action) {
            AlarmScheduler.ACTION_ALARM -> {
                val kind = intent.getStringExtra(EXTRA_KIND) ?: return
                Log.i(TAG, "闹钟触发：$kind，锁机中=${LockRuntime.isLocked}")
                when (kind) {
                    AlarmScheduler.KIND_WATCHDOG -> {
                        if (LockRuntime.isLocked) {
                            ServiceLauncher.startLockService(context)
                            AlarmScheduler.scheduleWatchdog(context)
                        }
                        LockController.evaluate(context, "watchdog")
                    }
                    AlarmScheduler.KIND_ARM -> {
                        // 预备闹钟：距开始不足 30 分钟了，重新评估会把开始闹钟
                        // 升级成「闹钟式」下发，保证准点
                        LockController.evaluate(context, "pre-arm")
                    }
                    else -> LockController.evaluate(context, "alarm-$kind")
                }
            }

            AlarmScheduler.ACTION_END_NOW -> {
                // 只有非严格模式的常驻通知才会挂这个入口
                LockController.forceEnd(context, R.string.reason_manual)
            }
        }
    }
}
