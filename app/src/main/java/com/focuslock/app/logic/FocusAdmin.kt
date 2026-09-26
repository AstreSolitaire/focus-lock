package com.focuslock.app.logic

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.focuslock.app.R
import com.focuslock.app.service.FocusDeviceAdminReceiver

private const val TAG = "FocusAdmin"

/**
 * 设备管理员能力，只用来做一件事：锁机期间阻止卸载本应用。
 * 用户随时可以在系统设置里撤销——只是严格模式下「系统设置」被屏蔽了。
 */
object FocusAdmin {

    private fun dpm(ctx: Context): DevicePolicyManager? =
        ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    private fun cn(ctx: Context) = ComponentName(ctx, FocusDeviceAdminReceiver::class.java)

    fun isAdminActive(ctx: Context): Boolean =
        runCatching { dpm(ctx)?.isAdminActive(cn(ctx)) == true }.getOrDefault(false)

    fun requestIntent(ctx: Context): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn(ctx))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                ctx.getString(R.string.device_admin_explain)
            )
        }

    fun removeAdmin(ctx: Context) {
        runCatching { dpm(ctx)?.removeActiveAdmin(cn(ctx)) }
    }

    fun setUninstallBlocked(ctx: Context, blocked: Boolean) {
        val d = dpm(ctx) ?: return
        if (!isAdminActive(ctx)) return
        runCatching { d.setUninstallBlocked(cn(ctx), ctx.packageName, blocked) }
            .onFailure { Log.w(TAG, "设置卸载拦截失败", it) }
    }

    /** 立刻熄屏（锁机开始时可选） */
    fun lockNow(ctx: Context) {
        val d = dpm(ctx) ?: return
        if (!isAdminActive(ctx)) return
        runCatching { d.lockNow() }.onFailure { Log.w(TAG, "熄屏失败", it) }
    }
}
