package com.focuslock.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 白名单里的一行。
 *
 * [launchable] 表示这个包有没有可以启动的入口 Activity —— 没有的话
 * （纯后台服务、输入法、壁纸这类）即使加进白名单也没法「打开」，
 * 界面上会标出来。
 */
data class AppEntry(
    val pkg: String,
    val label: String,
    val isSystem: Boolean,
    val launchable: Boolean
)

object AppCatalog {

    private const val ICON_PX = 132

    /**
     * 列出全部已安装应用（除本应用自己）。
     *
     * 用 getInstalledApplications 而不是查 LAUNCHER 入口，是为了让系统应用
     * 也能进白名单 —— 有些系统应用不给桌面图标，但用户确实需要它可用。
     */
    suspend fun load(ctx: Context): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS
        val installed = runCatching { pm.getInstalledApplications(flags) }.getOrDefault(emptyList())

        val out = ArrayList<AppEntry>(installed.size)
        for (info in installed) {
            val pkg = info.packageName
            if (pkg == ctx.packageName) continue
            val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(pkg)
            val launchable = runCatching { pm.getLaunchIntentForPackage(pkg) != null }
                .getOrDefault(false)
            out += AppEntry(
                pkg = pkg,
                label = label,
                isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                launchable = launchable
            )
        }
        // 能打开的排前面，其次按名称；非字母开头的（数字、符号）排最后
        out.sortWith(
            compareByDescending<AppEntry> { it.launchable }
                .thenBy { it.label.firstOrNull()?.let { c -> !c.isLetter() } ?: true }
                .thenBy { it.label }
        )
        out
    }

    /** 按需加载单个应用的图标。列表有几百项，一次性全加载太浪费。 */
    suspend fun loadIcon(ctx: Context, pkg: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            ctx.packageManager.getApplicationIcon(pkg).toBitmap(ICON_PX, ICON_PX)
        }.getOrNull()
    }

    /** 取出指定包名里「能打开」的那些，用于锁屏上的白名单启动器 */
    suspend fun loadLaunchable(ctx: Context, packages: Set<String>): List<AppEntry> {
        if (packages.isEmpty()) return emptyList()
        val all = load(ctx)
        return all.filter { it.pkg in packages && it.launchable }
    }

    fun launch(ctx: Context, pkg: String): Boolean = runCatching {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        )
        ctx.startActivity(intent)
        true
    }.getOrDefault(false)

    /** 把已保存的包名映射成显示名，找不到的（已卸载）退回包名 */
    fun resolveLabels(ctx: Context, packages: Set<String>): Map<String, String> {
        val pm = ctx.packageManager
        return packages.associateWith { pkg ->
            runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
        }
    }
}
