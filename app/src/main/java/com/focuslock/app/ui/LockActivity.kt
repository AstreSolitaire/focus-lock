package com.focuslock.app.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
class LockActivity : ComponentActivity() {

    companion object {
        fun intent(ctx: Context): Intent =
            Intent(ctx, LockActivity::class.java).addFlags(
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

        // 显示在系统锁屏之上；到点自动亮屏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                        if (ok) LockController.forceEnd(this@LockActivity, "紧急解锁")
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
