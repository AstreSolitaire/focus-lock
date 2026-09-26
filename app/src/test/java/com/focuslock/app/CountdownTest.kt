package com.focuslock.app

import com.focuslock.app.logic.Countdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 双时钟倒计时的行为验证。
 *
 * 这些用例就是「重启 / 改时间都不能让锁机提前结束」这句话的可执行版本。
 */
class CountdownTest {

    private val hour = 3_600_000L
    private val minute = 60_000L

    /** 一切正常：墙钟和单调钟同步前进 */
    @Test
    fun `正常流逝时按墙钟给出剩余时间`() {
        val startWall = 1_700_000_000_000L
        val startElapsed = 500_000L
        val dur = 2 * hour

        val r = Countdown.evaluate(
            startedAt = startWall,
            startedElapsed = startElapsed,
            durMs = dur,
            nowWall = startWall + 30 * minute,
            nowElapsed = startElapsed + 30 * minute,
            maxWallSeen = startWall + 30 * minute
        )

        assertTrue(r is Countdown.Result.Remaining)
        assertEquals(90 * minute, (r as Countdown.Result.Remaining).ms)
    }

    /** 把系统时间往后拨 3 小时，倒计时不能因此走完 */
    @Test
    fun `把时间往后调不会让锁机提前结束`() {
        val startWall = 1_700_000_000_000L
        val startElapsed = 500_000L
        val dur = 2 * hour

        // 真实只过了 10 分钟，但用户把系统时间往前拨了 3 小时
        val r = Countdown.evaluate(
            startedAt = startWall,
            startedElapsed = startElapsed,
            durMs = dur,
            nowWall = startWall + 3 * hour + 10 * minute,
            nowElapsed = startElapsed + 10 * minute,
            maxWallSeen = startWall + 3 * hour + 10 * minute
        )

        assertTrue(r is Countdown.Result.Remaining)
        assertEquals("应当按单调钟算，还剩 110 分钟", 110 * minute, (r as Countdown.Result.Remaining).ms)
    }

    /** 把系统时间往回拨，不能因此把锁机无限延长 */
    @Test
    fun `把时间往回拨会以单调钟重建基线`() {
        val startWall = 1_700_000_000_000L
        val startElapsed = 500_000L
        val dur = 2 * hour

        val r = Countdown.evaluate(
            startedAt = startWall,
            startedElapsed = startElapsed,
            durMs = dur,
            nowWall = startWall - 5 * hour, // 时间被拨回 5 小时
            nowElapsed = startElapsed + 40 * minute,
            maxWallSeen = startWall
        )

        assertTrue("墙钟异常时应重建基线", r is Countdown.Result.Rebaseline)
        assertEquals(80 * minute, (r as Countdown.Result.Rebaseline).ms)
    }

    /** 刚过一点点的正常漂移不该被误判为改时间 */
    @Test
    fun `小幅时钟漂移不触发重建基线`() {
        val startWall = 1_700_000_000_000L
        val startElapsed = 500_000L
        val dur = 2 * hour

        val r = Countdown.evaluate(
            startedAt = startWall,
            startedElapsed = startElapsed,
            durMs = dur,
            nowWall = startWall + 10 * minute - 30_000L, // 墙钟比单调钟慢 30 秒
            nowElapsed = startElapsed + 10 * minute,
            maxWallSeen = startWall + 10 * minute
        )

        assertTrue(r is Countdown.Result.Remaining)
        assertEquals(110 * minute + 30_000L, (r as Countdown.Result.Remaining).ms)
    }

    /** 重启后单调钟归零，按墙钟剩余量重建基线继续锁 */
    @Test
    fun `重启后按墙钟恢复剩余时间`() {
        val startWall = 1_700_000_000_000L
        val startElapsed = 9_000_000L // 重启前系统已开机很久
        val dur = 4 * hour

        // 重启：nowElapsed 变成开机后的一点点，墙钟则正常前进
        val r = Countdown.evaluate(
            startedAt = startWall,
            startedElapsed = startElapsed,
            durMs = dur,
            nowWall = startWall + 1 * hour,
            nowElapsed = 12_000L,
            maxWallSeen = startWall
        )

        assertTrue(r is Countdown.Result.Rebaseline)
        assertEquals("还剩 3 小时", 3 * hour, (r as Countdown.Result.Rebaseline).ms)
    }

    /** 重启 + 把时间往回拨：用水位线兜住，不能让锁机被拖长 */
    @Test
    fun `重启并回拨时间时水位线兜底`() {
        val startWall = 1_700_000_000_000L
        val startElapsed = 9_000_000L
        val dur = 2 * hour
        val watermark = startWall + 30 * minute // 关机前已经观察到的时间

        val r = Countdown.evaluate(
            startedAt = startWall,
            startedElapsed = startElapsed,
            durMs = dur,
            nowWall = startWall - 10 * hour, // 开机后把时间拨回 10 小时
            nowElapsed = 5_000L,
            maxWallSeen = watermark
        )

        assertTrue(r is Countdown.Result.Rebaseline)
        val ms = (r as Countdown.Result.Rebaseline).ms
        // 有效「现在」被抬到 watermark - 1h，因此剩余不会超过这个数
        assertEquals(startWall + dur - (watermark - hour), ms)
    }

    /** 时间走完返回 0，不返回负数 */
    @Test
    fun `时间走完返回零`() {
        val startWall = 1_700_000_000_000L
        val startElapsed = 500_000L

        val r = Countdown.evaluate(
            startedAt = startWall,
            startedElapsed = startElapsed,
            durMs = 30 * minute,
            nowWall = startWall + 45 * minute,
            nowElapsed = startElapsed + 45 * minute,
            maxWallSeen = startWall + 45 * minute
        )

        assertEquals(0L, (r as Countdown.Result.Remaining).ms)
    }
}
