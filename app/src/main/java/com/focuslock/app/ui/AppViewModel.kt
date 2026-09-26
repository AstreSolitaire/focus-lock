package com.focuslock.app.ui

import android.app.Application
import androidx.annotation.StringRes
import com.focuslock.app.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import com.focuslock.app.data.AppCatalog
import com.focuslock.app.data.AppEntry
import com.focuslock.app.data.Prefs
import com.focuslock.app.data.Schedule
import com.focuslock.app.data.StatsStore
import com.focuslock.app.logic.AlarmScheduler
import com.focuslock.app.logic.LockController

/**
 * 极简导航模型：底部四个 tab 常驻，需要全屏展开的页面压在上面的栈里。
 * 页面数量很少，不值得为此引入 navigation-compose。
 */
sealed interface Screen {
    data class ScheduleEdit(val scheduleId: String?) : Screen
    data object Settings : Screen
    data object Permissions : Screen
}

enum class Tab(@StringRes val labelRes: Int) {
    Home(R.string.tab_home),
    Schedules(R.string.tab_schedules),
    Whitelist(R.string.tab_whitelist),
    Stats(R.string.tab_stats)
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    var tab by mutableStateOf(Tab.Home)
        private set

    /** 全屏覆盖页面栈 */
    val stack = mutableStateListOf<Screen>()

    val stats = StatsStore.snapshot

    var schedules by mutableStateOf(Prefs.schedules)
        private set

    var whitelist by mutableStateOf(Prefs.whitelist)
        private set

    var masterEnabled by mutableStateOf(Prefs.masterEnabled)
        private set

    var wallpaperUri by mutableStateOf(Prefs.wallpaperUri)
        private set

    var lockQuote by mutableStateOf(Prefs.lockQuote)
        private set

    var emergencyPassword by mutableStateOf(Prefs.emergencyPassword)
        private set

    var themeMode by mutableStateOf(Prefs.themeMode)
        private set

    // 严格模式的三个细分开关
    var strictBlockSettings by mutableStateOf(Prefs.strictBlockSettings)
        private set
    var strictBlockShade by mutableStateOf(Prefs.strictBlockShade)
        private set
    var blockUninstall by mutableStateOf(Prefs.blockUninstall)
        private set
    var lockScreenOnStart by mutableStateOf(Prefs.lockScreenOnStart)
        private set
    var vibrate by mutableStateOf(Prefs.vibrate)
        private set

    /** 白名单页面的应用列表缓存 */
    var catalog by mutableStateOf<List<AppEntry>>(emptyList())
    var catalogLoading by mutableStateOf(false)
        private set

    // ------------------------------------------------------------ 导航

    fun selectTab(t: Tab) {
        stack.clear()
        tab = t
    }

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    fun popAll() {
        stack.clear()
    }

    // ------------------------------------------------------------ 计划

    fun scheduleById(id: String?): Schedule? =
        id?.let { sid -> schedules.firstOrNull { it.id == sid } }

    fun upsertSchedule(schedule: Schedule) {
        val list = schedules.toMutableList()
        val idx = list.indexOfFirst { it.id == schedule.id }
        if (idx >= 0) list[idx] = schedule else list.add(schedule)
        persistSchedules(list)
    }

    fun deleteSchedule(id: String) {
        persistSchedules(schedules.filterNot { it.id == id })
    }

    fun toggleSchedule(id: String) {
        persistSchedules(schedules.map { if (it.id == id) it.copy(enabled = !it.enabled) else it })
    }

    private fun persistSchedules(list: List<Schedule>) {
        Prefs.schedules = list
        schedules = list
        // 计划变了：立刻重新评估，并重排下一次开始闹钟
        AlarmScheduler.cancelStart(getApplication())
        LockController.evaluate(getApplication(), "schedules-changed")
    }

    fun newSchedule(): Schedule = Schedule(
        id = Prefs.newScheduleId(),
        name = "",
        days = setOf(1, 2, 3, 4, 5),
        ranges = emptyList(),
        useWhitelist = true,
        strict = true
    )

    /** 锁机期间允许直接打开的白名单应用（只保留有启动入口的） */
    suspend fun launchableWhitelist(): List<AppEntry> =
        AppCatalog.loadLaunchable(getApplication(), Prefs.whitelist)

    // ------------------------------------------------------------ 白名单

    fun setWhitelisted(pkg: String, include: Boolean) {
        val next = whitelist.toMutableSet()
        if (include) next += pkg else next -= pkg
        Prefs.whitelist = next
        whitelist = next
        LockController.refreshAllowed(getApplication())
    }

    fun setWhitelistAll(pkgs: Collection<String>) {
        val next = whitelist.toMutableSet()
        next += pkgs
        Prefs.whitelist = next
        whitelist = next
        LockController.refreshAllowed(getApplication())
    }

    fun clearWhitelist() {
        Prefs.whitelist = emptySet()
        whitelist = emptySet()
        LockController.refreshAllowed(getApplication())
    }

    fun updateCatalog(list: List<AppEntry>) {
        catalog = list
        catalogLoading = false
    }

    fun markCatalogLoading() {
        catalogLoading = true
    }

    // ------------------------------------------------------------ 开关与设置

    fun updateMasterEnabled(enabled: Boolean) {
        Prefs.masterEnabled = enabled
        masterEnabled = enabled
        LockController.evaluate(getApplication(), "master-switch")
    }

    fun setWallpaper(uri: String?) {
        Prefs.wallpaperUri = uri
        wallpaperUri = uri
    }

    fun setQuote(text: String) {
        Prefs.lockQuote = text
        lockQuote = text
    }

    fun updateEmergencyPassword(pw: String) {
        Prefs.emergencyPassword = pw
        emergencyPassword = pw
    }

    /** 当前应用内语言：空串表示跟随系统 */
    val appLanguage: String
        get() = AppCompatDelegate.getApplicationLocales().toLanguageTags()

    /** 切换应用内语言。传空串表示跟随系统。 */
    fun updateAppLanguage(tag: String) {
        val locales = if (tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
        languageRevision++
    }

    /** 语言切换后用来触发重组的小计数器 */
    var languageRevision by mutableStateOf(0)
        private set

    fun updateThemeMode(mode: Int) {
        Prefs.themeMode = mode
        themeMode = mode
    }

    fun updateStrictBlockSettings(v: Boolean) {
        Prefs.strictBlockSettings = v
        strictBlockSettings = v
        LockController.refreshAllowed(getApplication())
    }

    fun updateStrictBlockShade(v: Boolean) {
        Prefs.strictBlockShade = v
        strictBlockShade = v
        LockController.refreshAllowed(getApplication())
    }

    fun updateBlockUninstall(v: Boolean) {
        Prefs.blockUninstall = v
        blockUninstall = v
        if (!v) com.focuslock.app.logic.FocusAdmin.setUninstallBlocked(getApplication(), false)
    }

    fun updateLockScreenOnStart(v: Boolean) {
        Prefs.lockScreenOnStart = v
        lockScreenOnStart = v
    }

    fun updateVibrate(v: Boolean) {
        Prefs.vibrate = v
        vibrate = v
    }

    fun refreshStats() {
        StatsStore.refresh()
    }

    /** 立即手动开始一次锁机（用于「现在就开始专注」） */
    fun startManualLock(minutes: Int) {
        val app = getApplication<Application>()
        val now = System.currentTimeMillis()
        val window = com.focuslock.app.data.LockWindow(
            scheduleId = "manual",
            scheduleName = app.getString(R.string.lock_default_schedule_name),
            startAt = now,
            endAt = now + minutes * 60_000L,
            useWhitelist = true,
            strict = false,
            keys = setOf(now)
        )
        LockController.startLock(app, window)
    }

    fun endCurrentLock() {
        LockController.forceEnd(getApplication(), com.focuslock.app.R.string.reason_manual)
    }
}
