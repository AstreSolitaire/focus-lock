package com.focuslock.app.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.app.data.Prefs
import com.focuslock.app.logic.LockController
import com.focuslock.app.logic.LockRuntime
import com.focuslock.app.ui.theme.FocusLockTheme

/**
 * 锁机界面。全屏、置顶、不响应返回键，锁机结束前不会自行退出。
 *
 * 退出条件只有一个：LockRuntime 里的会话被清空（倒计时走完，或用户通过
 * 紧急密码主动结束）。
 */
class LockActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TURN_ON_SCREEN = "turn_on_screen"

        /**
         * [turnOnScreen] 只在「锁机刚开始、需要主动把用户叫醒」时为 true。
         *
         * 无障碍服务把锁屏顶回前台时**绝不能**点亮屏幕 —— 否则用户按电源键熄屏后
         * 会被反复点亮，表现出来就是「锁机期间没法息屏」。
         */
        fun intent(ctx: Context, turnOnScreen: Boolean = false): Intent =
            Intent(ctx, LockActivity::class.java)
                .putExtra(EXTRA_TURN_ON_SCREEN, turnOnScreen)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LockController.restoreIfNeeded(this)
        if (!LockRuntime.isLocked) {
            finish()
            return
        }

        // 显示在系统锁屏之上；只有锁机刚启动那一次才顺带点亮屏幕
        val turnOn = intent?.getBooleanExtra(EXTRA_TURN_ON_SCREEN, false) == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(turnOn)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            if (turnOn) {
                @Suppress("DEPRECATION")
                window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
        // 刻意不加 FLAG_KEEP_SCREEN_ON：锁机界面不该阻止屏幕自动熄灭。
        // 用户不碰手机时屏幕就该按系统超时正常黑掉，锁机照常计时。
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        // 返回键直接吞掉
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setContent {
            val state by LockRuntime.state.collectAsStateWithLifecycle()

            // 会话被清空（倒计时走完 / 紧急解锁）就立刻关闭锁屏
            LaunchedEffect(state.locked) {
                if (!state.locked) finish()
            }

            FocusLockTheme(Prefs.themeMode) {
                LockScreen(
                    state = state,
                    quote = Prefs.lockQuote,
                    whitelistCount = Prefs.whitelist.size,
                    emergencyEnabled = Prefs.emergencyPassword.isNotBlank(),
                    onEmergencyUnlock = { pw ->
                        val ok = pw == Prefs.emergencyPassword && pw.isNotBlank()
                        if (ok) LockController.forceEnd(this@LockActivity, com.focuslock.app.R.string.reason_emergency)
                        ok
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        LockController.restoreIfNeeded(this)
        if (!LockRuntime.isLocked) {
            finish()
            return
        }
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** 尽量吞掉可能让用户离开锁屏的按键；电源键绝不拦截 */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_APP_SWITCH -> true
        else -> super.onKeyDown(keyCode, event)
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
