package com.focuslock.app.logic

import com.focuslock.app.data.LockSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 锁机界面与首页共享的实时状态 */
data class LockUiState(
    val locked: Boolean = false,
    val scheduleName: String = "",
    val remainingMs: Long = 0L,
    val totalMs: Long = 0L,
    val strict: Boolean = false,
    val useWhitelist: Boolean = false,
    val startedAt: Long = 0L,
    val endAt: Long = 0L
)

/**
 * 进程内的锁机状态缓存。
 *
 * 无障碍服务需要以「最快、不加锁」的方式判断当前能不能放行某个包，
 * 因此这里全部用 @Volatile 字段而不是 Flow —— Flow 只负责驱动界面。
 */
object LockRuntime {

    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state

    @Volatile
    var session: LockSession? = null
        private set

    /** 当前允许启动的包名集合 */
    @Volatile
    var allowedPackages: Set<String> = emptySet()
        private set

    /** 系统默认桌面。锁机期间它属于「必须拦住」，否则按 Home 就离开锁屏了。 */
    @Volatile
    var defaultLauncher: String? = null
        private set

    /** 最近一次按下电源键的时刻：给电源菜单留出宽限期，保证能正常关机/重启 */
    @Volatile
    var lastPowerKeyAt: Long = 0L

    @Volatile
    var lastForceShowAt: Long = 0L

    /**
     * 用户主动从锁屏打开白名单应用后的短暂放行期。
     *
     * 启动一个应用时，系统（尤其 ColorOS）可能先弹自己的确认框或权限框。
     * 这些框不属于白名单，如果照拦，应用就永远打不开 —— 所以在这段时间里
     * 放行系统弹窗，等应用真正起来后再恢复拦截。
     */
    @Volatile
    var launchGraceUntil: Long = 0L
        private set

    fun markAppLaunch(windowMs: Long = 10_000L) {
        launchGraceUntil = System.currentTimeMillis() + windowMs
    }

    fun inLaunchGrace(): Boolean =
        launchGraceUntil > 0L && System.currentTimeMillis() < launchGraceUntil

    val isLocked: Boolean get() = session != null

    fun setSession(s: LockSession?) {
        session = s
        val cur = _state.value
        _state.value = if (s == null) {
            LockUiState()
        } else {
            cur.copy(
                locked = true,
                scheduleName = s.scheduleName,
                totalMs = s.durMs,
                strict = s.strict,
                useWhitelist = s.useWhitelist,
                startedAt = s.startedAt,
                endAt = s.endAt
            )
        }
    }

    fun setAllowed(set: Set<String>) {
        allowedPackages = set
    }

    fun setDefaultLauncher(pkg: String?) {
        defaultLauncher = pkg
    }

    fun setRemaining(ms: Long) {
        val cur = _state.value
        if (cur.remainingMs != ms) _state.value = cur.copy(remainingMs = ms)
    }

    /** 电源键宽限期内暂停拦截，让系统电源菜单能正常弹出 */
    fun inPowerGrace(windowMs: Long = 8_000L): Boolean =
        lastPowerKeyAt > 0 && System.currentTimeMillis() - lastPowerKeyAt < windowMs
}
