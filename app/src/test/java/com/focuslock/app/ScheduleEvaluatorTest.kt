package com.focuslock.app

import com.focuslock.app.data.Schedule
import com.focuslock.app.data.TimeRange
import com.focuslock.app.logic.ScheduleEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class ScheduleEvaluatorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 固定一个周一，保证用例与运行机器的时区、当前日期无关 */
    private val monday: LocalDate =
        LocalDate.of(2026, 9, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun schedule(
        days: Set<Int>,
        ranges: List<TimeRange>,
        enabled: Boolean = true,
        name: String = "测试计划",
        strict: Boolean = true,
        useWhitelist: Boolean = true,
        id: String = "s1"
    ) = Schedule(
        id = id,
        name = name,
        enabled = enabled,
        days = days,
        ranges = ranges,
        useWhitelist = useWhitelist,
        strict = strict
    )

    // ------------------------------------------------------------ TimeRange

    @Test
    fun `普通时段的时长`() {
        val r = TimeRange(8 * 60, 12 * 60)
        assertEquals(240, r.durationMinutes)
        assertTrue(!r.crossesMidnight)
    }

    @Test
    fun `跨午夜时段的时长`() {
        val r = TimeRange(22 * 60 + 30, 6 * 60)
        assertEquals(450, r.durationMinutes)
        assertTrue(r.crossesMidnight)
    }

    @Test
    fun `起止相同时按整一天处理`() {
        assertEquals(1440, TimeRange(0, 0).durationMinutes)
    }

    // ------------------------------------------------------------ 区间展开

    @Test
    fun `只在周一产生 08-12 的区间`() {
        val s = schedule(days = setOf(1), ranges = listOf(TimeRange(8 * 60, 12 * 60)))
        val list = ScheduleEvaluator.intervals(listOf(s), at(monday, 10), zone = zone)

        assertTrue(list.isNotEmpty())
        // 展开结果会覆盖前后若干个周一，逐个校验落在周一的 08:00、长度为 4 小时
        list.forEach { iv ->
            val start = Instant.ofEpochMilli(iv.start).atZone(zone)
            assertEquals("开始必须在周一", DayOfWeek.MONDAY, start.dayOfWeek)
            assertEquals("开始必须在 8 点", 8, start.hour)
            assertEquals("开始必须整点", 0, start.minute)
            assertEquals("时长必须 4 小时", 4 * 3_600_000L, iv.end - iv.start)
        }
        assertTrue("应当包含以 now 所在那一周为基准的区间", list.any { it.start == at(monday, 8) })
    }

    @Test
    fun `跨午夜时段的结束落在第二天`() {
        val s = schedule(days = setOf(1), ranges = listOf(TimeRange(22 * 60 + 30, 6 * 60)))
        val list = ScheduleEvaluator.intervals(listOf(s), at(monday, 23), zone = zone)

        val hit = list.first { it.start == at(monday, 22, 30) }
        assertEquals(at(monday.plusDays(1), 6), hit.end)
    }

    @Test
    fun `停用的计划不产生任何区间`() {
        val s = schedule(
            days = setOf(1),
            ranges = listOf(TimeRange(8 * 60, 12 * 60)),
            enabled = false
        )
        assertTrue(ScheduleEvaluator.intervals(listOf(s), at(monday, 10), zone = zone).isEmpty())
    }

    @Test
    fun `一周七天的计划每天都有区间`() {
        val s = schedule(days = (1..7).toSet(), ranges = listOf(TimeRange(9 * 60, 10 * 60)))
        val list = ScheduleEvaluator.intervals(listOf(s), at(monday, 10), backDays = 0, forwardDays = 6, zone = zone)
        assertEquals(7, list.size)
    }

    // ------------------------------------------------------------ 当前生效

    @Test
    fun `区间内判定为锁机中`() {
        val s = schedule(days = setOf(1), ranges = listOf(TimeRange(8 * 60, 12 * 60)))
        val w = ScheduleEvaluator.activeWindow(listOf(s), at(monday, 10), zone = zone)

        assertNotNull(w)
        assertEquals(at(monday, 8), w!!.startAt)
        assertEquals(at(monday, 12), w.endAt)
        assertEquals(setOf(at(monday, 8)), w.keys)
    }

    @Test
    fun `区间外判定为未锁机`() {
        val s = schedule(days = setOf(1), ranges = listOf(TimeRange(8 * 60, 12 * 60)))
        assertNull(ScheduleEvaluator.activeWindow(listOf(s), at(monday, 13), zone = zone))
        assertNull(ScheduleEvaluator.activeWindow(listOf(s), at(monday, 7), zone = zone))
    }

    @Test
    fun `跨午夜的区间在次日凌晨仍然生效`() {
        val s = schedule(days = setOf(1), ranges = listOf(TimeRange(22 * 60 + 30, 6 * 60)))
        val w = ScheduleEvaluator.activeWindow(listOf(s), at(monday.plusDays(1), 2), zone = zone)

        assertNotNull(w)
        assertEquals(at(monday, 22, 30), w!!.startAt)
        assertEquals(at(monday.plusDays(1), 6), w.endAt)
    }

    @Test
    fun `多个计划重叠时结束时间取最晚且严格模式取或`() {
        val a = schedule(setOf(1), listOf(TimeRange(8 * 60, 10 * 60)), strict = false, name = "A", id = "a")
        val b = schedule(setOf(1), listOf(TimeRange(9 * 60, 12 * 60)), strict = true, name = "B", id = "b")

        val w = ScheduleEvaluator.activeWindow(listOf(a, b), at(monday, 9, 30), zone = zone)

        assertNotNull(w)
        assertEquals(at(monday, 8), w!!.startAt)
        assertEquals(at(monday, 12), w.endAt)
        assertTrue("任一计划要求严格即严格", w.strict)
        assertEquals(setOf(at(monday, 8), at(monday, 9)), w.keys)
        assertTrue(w.scheduleName.contains("A"))
        assertTrue(w.scheduleName.contains("B"))
    }

    @Test
    fun `已完成的区间会被排除掉`() {
        val s = schedule(days = setOf(1), ranges = listOf(TimeRange(8 * 60, 12 * 60)))
        val key = at(monday, 8)

        assertNotNull(ScheduleEvaluator.activeWindow(listOf(s), at(monday, 10), zone = zone))
        assertNull(
            "时钟回拨后同一时段不应被重复开启",
            ScheduleEvaluator.activeWindow(listOf(s), at(monday, 10), setOf(key), zone = zone)
        )
    }

    // ------------------------------------------------------------ 未来安排

    @Test
    fun `能找到下一个即将开始的时段`() {
        val s = schedule(days = setOf(1), ranges = listOf(TimeRange(8 * 60, 12 * 60)))
        val next = ScheduleEvaluator.nextWindow(listOf(s), at(monday, 0, 30), zone = zone)

        assertNotNull(next)
        assertEquals(at(monday, 8), next!!.start)
    }

    @Test
    fun `进行中的时段不算下一个`() {
        val s = schedule(days = setOf(1), ranges = listOf(TimeRange(8 * 60, 12 * 60)))
        val next = ScheduleEvaluator.nextWindow(listOf(s), at(monday, 10), zone = zone)

        // 下一个应该是下周一同一时间
        assertNotNull(next)
        assertEquals(at(monday.plusWeeks(1), 8), next!!.start)
    }

    @Test
    fun `停用后没有下一个时段`() {
        val s = schedule(setOf(1), listOf(TimeRange(8 * 60, 12 * 60)), enabled = false)
        assertNull(ScheduleEvaluator.nextWindow(listOf(s), at(monday, 0, 30), zone = zone))
    }

    @Test
    fun `今天的时间轴只包含当天区间`() {
        val morning = schedule(days = (1..7).toSet(), listOf(TimeRange(8 * 60, 9 * 60)), id = "am", name = "早读")
        val night = schedule(days = (1..7).toSet(), listOf(TimeRange(22 * 60, 23 * 60)), id = "pm", name = "晚修")

        val list = ScheduleEvaluator.todayIntervals(listOf(morning, night), at(monday, 12), zone = zone)
        assertEquals(2, list.size)
        assertEquals(at(monday, 8), list[0].start)
        assertEquals(at(monday, 22), list[1].start)
    }

    // ------------------------------------------------------------ 周合计

    @Test
    fun `周合计分钟数按星期数与时段数相乘`() {
        val s = schedule(
            days = setOf(1, 3, 5),
            ranges = listOf(TimeRange(8 * 60, 10 * 60), TimeRange(14 * 60, 15 * 60 + 30))
        )
        assertEquals(3 * (120 + 90), s.weeklyMinutes)
    }
}
