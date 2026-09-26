package com.focuslock.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.focuslock.app.logic.LockController
import com.focuslock.app.logic.LockRuntime

private const val TAG = "AppWatchService"

/**
 * 前台应用监视。锁机期间一旦发现切到了白名单之外的应用，
 * 立刻把锁机界面重新顶到最前面。
 *
 * 本服务不读取任何屏幕内容（配置里 canRetrieveWindowContent=false），
 * 只用系统给出的前台包名做判断。
 */
class AppWatchService : AccessibilityService() {

    companion object {
        @Volatile
        var running: Boolean = false
            private set

        private const val OWN_ACTIVITY_PREFIX = "com.focuslock.app."
        private const val LOCK_ACTIVITY_SUFFIX = "LockActivity"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        LockController.restoreIfNeeded(this)
        Log.i(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val type = e.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }
        val pkg = e.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        enforce(pkg, e.className?.toString(), type)
    }

    private fun enforce(pkg: String, cls: String?, type: Int) {
        if (!LockRuntime.isLocked) return

        if (pkg == packageName) {
            // 本应用自己：只有锁屏界面能待在前台。
            // 如果跑上来的是主界面（例如从最近任务切回来），说明锁屏被绕过了，
            // 立刻把锁屏顶回去 —— 否则「锁机中的手机还能打开设置页」就成了漏洞。
            //
            // 只对「本应用自己的 Activity 类名」下手。锁屏上的白名单对话框是独立
            // 窗口，className 是 android.widget.FrameLayout 之类，必须放行，
            // 否则弹窗会把自己顶掉。
            val isOwnActivity = cls != null && cls.startsWith(OWN_ACTIVITY_PREFIX)
            val isLockScreen = cls != null && cls.endsWith(LOCK_ACTIVITY_SUFFIX)
            if (isOwnActivity && !isLockScreen &&
                type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            ) {
                Log.i(TAG, "本应用的非锁屏界面出现在前台（$cls），顶回锁屏")
                LockController.launchLockUi(this)
            }
            return
        }

        // AI 助手、系统桌面这类必须先判：它们连电源键宽限期都不能放过，
        // 否则长按电源键唤起小布助手就能绕开锁机
        if (LockController.isNeverAllowed(pkg)) {
            Log.i(TAG, "拦截（不可放行）$pkg")
            LockController.launchLockUi(this)
            return
        }

        // 刚按过电源键：给系统电源菜单留出时间，保证能正常关机 / 重启
        if (LockRuntime.inPowerGrace()) return

        // 刚从锁屏点开某个白名单应用：放行系统跟着弹出来的确认框 / 权限框
        if (LockRuntime.inLaunchGrace()) return

        if (LockController.isAllowed(pkg)) return
        Log.i(TAG, "拦截 $pkg")
        LockController.launchLockUi(this)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_POWER) {
            // 只记录时刻，绝不拦截电源键本身——开关机必须保持正常
            if (event.action == KeyEvent.ACTION_DOWN) {
                LockRuntime.lastPowerKeyAt = System.currentTimeMillis()
            }
            return false
        }
        if (!LockRuntime.isLocked) return false
        if (LockRuntime.inPowerGrace()) return false
        if (event.action != KeyEvent.ACTION_DOWN) return false
        return when (code) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU -> {
                // 直接把锁机界面顶回来，比等系统切换过去再拦截更干脆
                LockController.launchLockUi(this)
                true
            }
            else -> false
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务被中断")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        Log.w(TAG, "无障碍服务已断开")
        return super.onUnbind(intent)
    }
}
