package com.focuslock.app.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "DeviceAdmin"

class FocusDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "设备管理员已启用")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.w(TAG, "设备管理员已被关闭，锁机期间将无法阻止卸载")
    }
}
