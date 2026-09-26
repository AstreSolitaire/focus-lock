package com.focuslock.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.focuslock.app.util.dayKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private const val TAG = "StatsStore"
private const val FILE = "focus_lock_stats"
private const val KEY_DAILY = "daily_ms"
private const val KEY_LOG = "sessions"
private const val MAX_LOG = 80

/** 统计落盘间隔。锁机期间每秒都在累加，攒够一批再写磁盘。 */
private const val FLUSH_INTERVAL_MS = 15_000L

data class SessionLog(
    val startAt: Long,
    val endAt: Long,
    val scheduleName: String,
    val completed: Boolean
) {
    val durationMs: Long get() = (endAt - startAt).coerceAtLeast(0L)
}

data class DayStat(val date: LocalDate, val ms: Long)

data class StatsSnapshot(
    val todayMs: Long = 0L,
    val weekMs: Long = 0L,
    val monthMs: Long = 0L,
    val totalMs: Long = 0L,
    val days: List<DayStat> = emptyList(),
    val recent: List<SessionLog> = emptyList(),
    val sessionCount: Int = 0
)

/**
 * 锁机时长统计。按自然日累加毫秒数，另存最近若干次会话明细。
 */
object StatsStore {

    private lateinit var sp: SharedPreferences
    private val _snapshot = MutableStateFlow(StatsSnapshot())
    val snapshot: StateFlow<StatsSnapshot> = _snapshot

    /** 上一次累加时刻，用于按真实时间差累加，而不是简单数 tick */
    @Volatile private var lastTickAt = 0L
    @Volatile private var sessionStartAt = 0L

    /** 尚未落盘的毫秒数，攒够一批再写，避免每秒一次磁盘写入 */
    @Volatile private var pendingMs = 0L
    @Volatile private var lastFlushAt = 0L

    fun init(ctx: Context) {
        sp = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        refresh()
    }

    // ------------------------------------------------------------ 写入

    fun beginSession(now: Long) {
        lastTickAt = 0L
        sessionStartAt = now
        pendingMs = 0L
        lastFlushAt = now
    }

    /** 由锁机服务每秒调用；按两次调用之间的真实间隔累加，最多补 5 秒 */
    fun tick(now: Long) {
        val last = lastTickAt
        lastTickAt = now
        if (last == 0L) {
            lastFlushAt = now
            return
        }
        val delta = (now - last).coerceIn(0L, 5_000L)
        if (delta <= 0L) return
        pendingMs += delta
        if (now - lastFlushAt >= FLUSH_INTERVAL_MS) flush(now)
    }

    fun endSession(now: Long, scheduleName: String, completed: Boolean) {
        // 把最后一段不足一秒的零头补上
        val last = lastTickAt
        if (last != 0L && now > last) {
            pendingMs += (now - last).coerceIn(0L, 5_000L)
        }
        lastTickAt = 0L
        val start = sessionStartAt.takeIf { it > 0 }
        sessionStartAt = 0L
        flush(now)
        if (start == null || now <= start) {
            refresh()
            return
        }
        appendLog(SessionLog(start, now, scheduleName, completed))
        refresh()
    }

    /** 把内存里攒下的时长写进当天桶 */
    private fun flush(now: Long) {
        val ms = pendingMs
        if (ms <= 0L) return
        pendingMs = 0L
        lastFlushAt = now
        addMs(dayKey(now), ms)
    }

    private fun addMs(day: String, ms: Long) {
        if (ms <= 0) return
        val map = dailyMap().toMutableMap()
        map[day] = (map[day] ?: 0L) + ms
        writeDaily(map)
    }

    private fun appendLog(entry: SessionLog) {
        val arr = runCatching { JSONArray(sp.getString(KEY_LOG, "[]")) }.getOrDefault(JSONArray())
        val out = JSONArray()
        out.put(JSONObject().apply {
            put("s", entry.startAt)
            put("e", entry.endAt)
            put("n", entry.scheduleName)
            put("c", entry.completed)
        })
        for (i in 0 until minOf(arr.length(), MAX_LOG - 1)) {
            arr.optJSONObject(i)?.let { out.put(it) }
        }
        sp.edit().putString(KEY_LOG, out.toString()).apply()
    }

    fun clearAll() {
        sp.edit().remove(KEY_DAILY).remove(KEY_LOG).apply()
        lastTickAt = 0L
        sessionStartAt = 0L
        pendingMs = 0L
        lastFlushAt = 0L
        refresh()
    }

    // ------------------------------------------------------------ 读取

    fun refresh() {
        val map = dailyMap()
        val today = LocalDate.now()
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val monthStart = today.withDayOfMonth(1)

        var week = 0L
        var month = 0L
        var total = 0L
        map.forEach { (k, v) ->
            total += v
            val d = runCatching { LocalDate.parse(k) }.getOrNull() ?: return@forEach
            if (!d.isBefore(weekStart)) week += v
            if (!d.isBefore(monthStart)) month += v
        }

        // 把还没落盘的零头也算进今天，界面上不会出现「明明在锁却一直是 0 秒」
        val todayKey = today.toString()
        val pendingToday = if (dayKey() == todayKey) pendingMs else 0L
        if (pendingToday > 0L) {
            total += pendingToday
            week += pendingToday
            month += pendingToday
        }

        val days = (0..6).map { back ->
            val d = today.minusDays(back.toLong())
            val stored = map[d.toString()] ?: 0L
            DayStat(d, stored + if (d.toString() == todayKey) pendingToday else 0L)
        }.reversed()

        _snapshot.value = StatsSnapshot(
            todayMs = (map[todayKey] ?: 0L) + pendingToday,
            weekMs = week,
            monthMs = month,
            totalMs = total,
            days = days,
            recent = readLog(),
            sessionCount = readLog().size
        )
    }

    private fun dailyMap(): Map<String, Long> {
        if (!::sp.isInitialized) return emptyMap()
        val raw = sp.getString(KEY_DAILY, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { k -> put(k, obj.optLong(k)) }
            }
        }.onFailure { Log.w(TAG, "解析每日统计失败", it) }.getOrDefault(emptyMap())
    }

    private fun writeDaily(map: Map<String, Long>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        sp.edit().putString(KEY_DAILY, obj.toString()).apply()
    }

    private fun readLog(): List<SessionLog> {
        if (!::sp.isInitialized) return emptyList()
        val raw = sp.getString(KEY_LOG, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let {
                    SessionLog(
                        startAt = it.optLong("s"),
                        endAt = it.optLong("e"),
                        scheduleName = it.optString("n"),
                        completed = it.optBoolean("c", true)
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
