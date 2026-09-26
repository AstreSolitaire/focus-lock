package com.focuslock.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val TAG = "Prefs"
private const val FILE = "focus_lock_prefs"

/** 完成窗口记录最多保留 3 天，避免无限增长 */
private const val COMPLETED_TTL_MS = 3L * 24 * 3600 * 1000

/**
 * 全局偏好与持久化状态。
 *
 * 全部使用 SharedPreferences 同步读写：接收器（开机、闹钟）与无障碍服务可能在
 * 任何时刻被系统唤起，同步接口比协程/挂起函数更省心。
 */
object Prefs {

    private lateinit var sp: SharedPreferences

    /** 墙钟水位线的内存副本，避免读取时反复访问 SharedPreferences */
    @Volatile private var wallWatermark: Long = -1L

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    private fun safeSp(): SharedPreferences? = if (::sp.isInitialized) sp else null

    // ---------------------------------------------------------------- 计划

    var schedules: List<Schedule>
        get() {
            val raw = safeSp()?.getString(KEY_SCHEDULES, null) ?: return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let(::scheduleFromJson)
                }
            }.onFailure { Log.w(TAG, "解析计划失败", it) }.getOrDefault(emptyList())
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { arr.put(scheduleToJson(it)) }
            safeSp()?.edit()?.putString(KEY_SCHEDULES, arr.toString())?.apply()
        }

    // ---------------------------------------------------------------- 总开关

    var masterEnabled: Boolean
        get() = safeSp()?.getBoolean(KEY_MASTER, true) ?: true
        set(v) { safeSp()?.edit()?.putBoolean(KEY_MASTER, v)?.apply() }

    // ---------------------------------------------------------------- 白名单

    var whitelist: Set<String>
        get() {
            val raw = safeSp()?.getString(KEY_WHITELIST, null) ?: return emptySet()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }.toSet()
            }.getOrDefault(emptySet())
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { arr.put(it) }
            safeSp()?.edit()?.putString(KEY_WHITELIST, arr.toString())?.apply()
        }

    // ---------------------------------------------------------------- 锁屏外观

    /** 锁屏背景图（由系统相册选择器返回的持久化 URI） */
    var wallpaperUri: String?
        get() = safeSp()?.getString(KEY_WALLPAPER, null)
        set(v) { safeSp()?.edit()?.putString(KEY_WALLPAPER, v)?.apply() }

    /** 锁屏文案。空串表示「用语言相关的那句默认文案」。 */
    var lockQuote: String
        get() = safeSp()?.getString(KEY_QUOTE, "").orEmpty()
        set(v) { safeSp()?.edit()?.putString(KEY_QUOTE, v)?.apply() }

    var showFloatingClock: Boolean
        get() = safeSp()?.getBoolean(KEY_FLOAT_CLOCK, false) ?: false
        set(v) { safeSp()?.edit()?.putBoolean(KEY_FLOAT_CLOCK, v)?.apply() }

    // ---------------------------------------------------------------- 行为开关

    /** 严格模式下是否屏蔽「系统设置」 */
    var strictBlockSettings: Boolean
        get() = safeSp()?.getBoolean(KEY_STRICT_SETTINGS, true) ?: true
        set(v) { safeSp()?.edit()?.putBoolean(KEY_STRICT_SETTINGS, v)?.apply() }

    /** 严格模式下是否屏蔽下拉通知栏（长按电源键会有 8 秒宽限期） */
    var strictBlockShade: Boolean
        get() = safeSp()?.getBoolean(KEY_STRICT_SHADE, true) ?: true
        set(v) { safeSp()?.edit()?.putBoolean(KEY_STRICT_SHADE, v)?.apply() }

    /** 锁机期间通过设备管理员阻止卸载 */
    var blockUninstall: Boolean
        get() = safeSp()?.getBoolean(KEY_BLOCK_UNINSTALL, true) ?: true
        set(v) { safeSp()?.edit()?.putBoolean(KEY_BLOCK_UNINSTALL, v)?.apply() }

    /** 锁机开始时是否立刻熄屏 */
    var lockScreenOnStart: Boolean
        get() = safeSp()?.getBoolean(KEY_LOCK_SCREEN, false) ?: false
        set(v) { safeSp()?.edit()?.putBoolean(KEY_LOCK_SCREEN, v)?.apply() }

    /** 锁机开始/结束时震动提醒 */
    var vibrate: Boolean
        get() = safeSp()?.getBoolean(KEY_VIBRATE, true) ?: true
        set(v) { safeSp()?.edit()?.putBoolean(KEY_VIBRATE, v)?.apply() }

    var themeMode: Int
        get() = safeSp()?.getInt(KEY_THEME, 0) ?: 0
        set(v) { safeSp()?.edit()?.putInt(KEY_THEME, v)?.apply() }

    /**
     * 紧急解锁密码。为空表示不提供任何中途退出方式。
     * 注意：用户自己设置的密码无法找回，忘记只能等锁机结束。
     */
    var emergencyPassword: String
        get() = safeSp()?.getString(KEY_EMERGENCY_PW, "").orEmpty()
        set(v) { safeSp()?.edit()?.putString(KEY_EMERGENCY_PW, v)?.apply() }

    var firstRunDone: Boolean
        get() = safeSp()?.getBoolean(KEY_FIRST_RUN, false) ?: false
        set(v) { safeSp()?.edit()?.putBoolean(KEY_FIRST_RUN, v)?.apply() }

    // ---------------------------------------------------------------- 锁机会话

    var session: LockSession?
        get() {
            val raw = safeSp()?.getString(KEY_SESSION, null) ?: return null
            return runCatching { sessionFromJson(JSONObject(raw)) }
                .onFailure { Log.w(TAG, "解析会话失败", it) }
                .getOrNull()
        }
        set(value) {
            val editor = safeSp()?.edit() ?: return
            if (value == null) {
                editor.remove(KEY_SESSION)
            } else {
                editor.putString(KEY_SESSION, sessionToJson(value).toString())
            }
            editor.apply()
        }

    /** 已结束的时段起点集合，防止时钟回拨后重复开启同一时段 */
    fun completedWindowKeys(): Set<Long> {
        val raw = safeSp()?.getString(KEY_COMPLETED, null) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.optLong(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    fun addCompletedWindow(key: Long) {
        val cutoff = System.currentTimeMillis() - COMPLETED_TTL_MS
        val next = (completedWindowKeys().filter { it > cutoff } + key).toSet()
        val arr = JSONArray()
        next.sorted().forEach { arr.put(it) }
        safeSp()?.edit()?.putString(KEY_COMPLETED, arr.toString())?.apply()
    }

    fun clearCompletedWindows() {
        safeSp()?.edit()?.remove(KEY_COMPLETED)?.apply()
    }

    /** 观测到的最大墙钟时刻，用于识别「重启 + 把时间往回拨」 */
    val maxWallSeen: Long
        get() {
            if (wallWatermark < 0L) {
                wallWatermark = safeSp()?.getLong(KEY_MAX_WALL, 0L) ?: 0L
            }
            return wallWatermark
        }

    /**
     * 推进墙钟水位线。
     *
     * 锁机期间每秒都会调用，所以只在推进超过一分钟时才真正落盘，
     * 否则会变成每秒一次磁盘写入。
     */
    fun observeWall(now: Long) {
        if (wallWatermark < 0L) {
            wallWatermark = safeSp()?.getLong(KEY_MAX_WALL, 0L) ?: 0L
        }
        if (now - wallWatermark < WALL_WRITE_INTERVAL_MS) return
        wallWatermark = now
        safeSp()?.edit()?.putLong(KEY_MAX_WALL, now)?.apply()
    }

    fun newScheduleId(): String = UUID.randomUUID().toString().take(8)

    // ---------------------------------------------------------------- JSON 编解码

    private fun scheduleToJson(s: Schedule): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("name", s.name)
        put("enabled", s.enabled)
        put("days", JSONArray(s.days.sorted()))
        put("ranges", JSONArray().apply {
            s.ranges.forEach { r ->
                put(JSONObject().put("s", r.startMinute).put("e", r.endMinute))
            }
        })
        put("wl", s.useWhitelist)
        put("strict", s.strict)
    }

    private fun scheduleFromJson(o: JSONObject): Schedule {
        val days = o.optJSONArray("days")?.let { arr ->
            (0 until arr.length()).map { arr.optInt(it) }.filter { it in 1..7 }.toSet()
        } ?: emptySet()
        val ranges = o.optJSONArray("ranges")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { r ->
                    TimeRange(r.optInt("s", 480), r.optInt("e", 720)).normalize()
                }
            }
        } ?: emptyList()
        return Schedule(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString().take(8) },
            name = o.optString("name", ""),
            enabled = o.optBoolean("enabled", true),
            days = days,
            // 允许为空：用户完全可以先建好计划、之后再补时间段
            ranges = ranges,
            useWhitelist = o.optBoolean("wl", true),
            strict = o.optBoolean("strict", true)
        )
    }

    private fun sessionToJson(s: LockSession): JSONObject = JSONObject().apply {
        put("sid", s.scheduleId)
        put("name", s.scheduleName)
        put("st", s.startedAt)
        put("se", s.startedElapsed)
        put("dur", s.durMs)
        put("wl", s.useWhitelist)
        put("strict", s.strict)
        put("keys", JSONArray(s.intervalKeys))
    }

    private fun sessionFromJson(o: JSONObject): LockSession = LockSession(
        scheduleId = o.optString("sid"),
        scheduleName = o.optString("name"),
        startedAt = o.optLong("st"),
        startedElapsed = o.optLong("se"),
        durMs = o.optLong("dur"),
        useWhitelist = o.optBoolean("wl", true),
        strict = o.optBoolean("strict", true),
        intervalKeys = o.optJSONArray("keys")?.let { arr ->
            (0 until arr.length()).map { arr.optLong(it) }
        } ?: emptyList()
    )

    /** 墙钟水位线的最小落盘间隔 */
    private const val WALL_WRITE_INTERVAL_MS = 60_000L

    private const val KEY_SCHEDULES = "schedules"
    private const val KEY_MASTER = "master_enabled"
    private const val KEY_WHITELIST = "whitelist"
    private const val KEY_WALLPAPER = "wallpaper_uri"
    private const val KEY_QUOTE = "lock_quote"
    private const val KEY_FLOAT_CLOCK = "floating_clock"
    private const val KEY_STRICT_SETTINGS = "strict_block_settings"
    private const val KEY_STRICT_SHADE = "strict_block_shade"
    private const val KEY_BLOCK_UNINSTALL = "block_uninstall"
    private const val KEY_LOCK_SCREEN = "lock_screen_on_start"
    private const val KEY_VIBRATE = "vibrate"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_EMERGENCY_PW = "emergency_password"
    private const val KEY_FIRST_RUN = "first_run_done"
    private const val KEY_SESSION = "active_session"
    private const val KEY_COMPLETED = "completed_windows"
    private const val KEY_MAX_WALL = "max_wall_seen"
}
