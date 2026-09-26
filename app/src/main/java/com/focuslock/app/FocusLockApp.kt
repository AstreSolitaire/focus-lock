package com.focuslock.app

import android.app.Application
import android.util.Log
import com.focuslock.app.data.Prefs
import com.focuslock.app.data.StatsStore
import com.focuslock.app.logic.LockController
import com.focuslock.app.logic.LockRuntime
import com.focuslock.app.util.Notifications

private const val TAG = "FocusLockApp"

class FocusLockApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 每个进程启动都要先初始化：闹钟、开机广播、无障碍服务都可能冷启动进程
        Prefs.init(this)
        StatsStore.init(this)
        Notifications.ensureChannels(this)

        // 恢复可能进行中的锁机会话，然后重新评估当前该不该锁
        LockController.restoreIfNeeded(this)
        LockController.evaluate(this, "app-start")

        Log.i(TAG, "启动完成，锁机中=${LockRuntime.isLocked}")
    }
}
