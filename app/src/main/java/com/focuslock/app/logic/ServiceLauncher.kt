package com.focuslock.app.logic

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.focuslock.app.service.LockService

private const val TAG = "ServiceLauncher"

object ServiceLauncher {

    fun startLockService(ctx: Context) {
        try {
            val intent = Intent(ctx, LockService::class.java)
                .setAction(LockService.ACTION_ENSURE)
            ContextCompat.startForegroundService(ctx, intent)
        } catch (t: Throwable) {
            // Android 12+ 在后台启动前台服务会抛 ForegroundServiceStartNotAllowedException，
            // 此时靠闹钟心跳稍后重试
            Log.w(TAG, "启动锁机服务失败，稍后由心跳重试", t)
            AlarmScheduler.scheduleWatchdog(ctx)
        }
    }

    fun stopLockService(ctx: Context) {
        runCatching { ctx.stopService(Intent(ctx, LockService::class.java)) }
    }
}

/** 简单的震动封装，避免到处写 VIBRATOR_MANAGER 样板 */
object Haptics {
    fun pulse(ctx: Context, pattern: LongArray) {
        runCatching {
            val vm = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
            if (!vm.hasVibrator()) return
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vm.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vm.vibrate(pattern, -1)
            }
        }
    }

    fun short(ctx: Context) = pulse(ctx, longArrayOf(0, 30))
}
