package com.focuslock.app.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.focuslock.app.R
import com.focuslock.app.logic.LockRuntime
import com.focuslock.app.ui.LockActivity
import com.focuslock.app.ui.MainActivity

private const val TAG = "Notifications"

object Notifications {

    const val CH_LOCK = "lock_status"
    const val CH_ALERT = "lock_alert"

    const val ID_LOCK = 1001
    const val ID_ALERT = 1002
    const val ID_FULLSCREEN = 1003

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return

        if (nm.getNotificationChannel(CH_LOCK) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CH_LOCK,
                    ctx.getString(R.string.lock_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = ctx.getString(R.string.lock_channel_desc)
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
            )
        }
        if (nm.getNotificationChannel(CH_ALERT) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CH_ALERT,
                    "锁机提醒",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "锁机开始、结束与异常提醒"
                    setShowBadge(true)
                }
            )
        }
    }

    fun hasPermission(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        }
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    private fun mainPending(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx,
            1,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** 锁机进行中的常驻通知 */
    fun buildLockNotification(ctx: Context, scheduleName: String, remainingMs: Long, strict: Boolean): Notification {
        val open = PendingIntent.getActivity(
            ctx,
            3,
            LockActivity.intent(ctx),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(ctx, CH_LOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("专注中 · $scheduleName")
            .setContentText("剩余 ${fmtCountdown(remainingMs)}")
            .setSubText("已锁 ${fmtDuration(LockRuntime.session?.durMs ?: 0L)}")
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)

        if (!strict) {
            // 非严格模式给一个「提前结束」出口；严格模式不给
            val end = PendingIntent.getBroadcast(
                ctx,
                4,
                Intent(ctx, com.focuslock.app.service.AlarmReceiver::class.java).apply {
                    action = com.focuslock.app.logic.AlarmScheduler.ACTION_END_NOW
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(R.drawable.ic_notification, "结束锁机", end)
        }
        return builder.build()
    }

    fun updateLock(ctx: Context, scheduleName: String, remainingMs: Long, strict: Boolean) {
        if (!hasPermission(ctx)) return
        runCatching {
            NotificationManagerCompat.from(ctx)
                .notify(ID_LOCK, buildLockNotification(ctx, scheduleName, remainingMs, strict))
        }
    }

    fun notifyLockEnd(ctx: Context, scheduleName: String, reason: String) {
        if (!hasPermission(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("锁机已结束")
            .setContentText("「$scheduleName」$reason，辛苦了")
            .setAutoCancel(true)
            .setContentIntent(mainPending(ctx))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_ALERT, n) }
    }

    fun notifyWarning(ctx: Context, title: String, text: String) {
        if (!hasPermission(ctx)) return
        val n = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(mainPending(ctx))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_ALERT, n) }
    }

    fun cancelLock(ctx: Context) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(ID_LOCK) }
    }

    /**
     * 兜底：某些机型禁止后台直接启动 Activity 时，用全屏意图通知把锁屏顶出来。
     */
    fun notifyFullScreenFallback(ctx: Context) {
        if (!hasPermission(ctx)) return
        val full = PendingIntent.getActivity(
            ctx,
            5,
            LockActivity.intent(ctx),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CH_LOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("专注中")
            .setContentText("点按返回锁机界面")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(ID_FULLSCREEN, n) }
            .onFailure { Log.w(TAG, "全屏通知失败", it) }
    }
}
