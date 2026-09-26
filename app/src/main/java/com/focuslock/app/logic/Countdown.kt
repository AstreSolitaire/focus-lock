package com.focuslock.app.logic

/**
 * 「重启 / 改时间都不能让锁机提前结束」的纯计算部分。
 *
 * 抽成不依赖任何 Android API 的对象，是为了能直接被 JVM 单元测试覆盖
 * （见 `src/test/java/.../CountdownTest.kt`）。真正的时间来源由调用方注入。
 */
object Countdown {

    /**
     * 允许的墙钟漂移。墙钟剩余比单调钟剩余多出这个量，才判定为「时间被往回拨」。
     * 给 NTP 校时和夏令时留出余量。
     */
    const val CLOCK_SLACK_MS = 90_000L

    /** 重启后允许墙钟往回走的量，照顾跨时区飞行等正常场景 */
    const val REBOOT_ROLLBACK_TOLERANCE_MS = 3_600_000L

    sealed interface Result {
        /** 剩余毫秒，直接使用即可 */
        data class Remaining(val ms: Long) : Result

        /** 需要以 [ms] 为新时长重建计时基线（重启后 / 墙钟被回拨后） */
        data class Rebaseline(val ms: Long) : Result
    }

    /**
     * @param startedAt       本次锁机的基线墙钟时刻
     * @param startedElapsed  基线时刻的单调钟读数（SystemClock.elapsedRealtime）
     * @param durMs           本次锁机总时长
     * @param nowWall         当前墙钟（System.currentTimeMillis）
     * @param nowElapsed      当前单调钟读数
     * @param maxWallSeen     本机观测到过的最大墙钟，用于识别重启后的时间回拨
     */
    fun evaluate(
        startedAt: Long,
        startedElapsed: Long,
        durMs: Long,
        nowWall: Long,
        nowElapsed: Long,
        maxWallSeen: Long
    ): Result {
        // 单调钟读数小于基线 → 设备重启过，单调钟已归零
        if (nowElapsed < startedElapsed) {
            val effectiveNow = maxOf(nowWall, maxWallSeen - REBOOT_ROLLBACK_TOLERANCE_MS)
            return Result.Rebaseline((startedAt + durMs - effectiveNow).coerceAtLeast(0L))
        }

        val monoRemain = (durMs - (nowElapsed - startedElapsed)).coerceAtLeast(0L)
        val wallRemain = startedAt + durMs - nowWall

        // 墙钟剩余明显大于单调钟剩余 → 系统时间被往回拨了。
        // 以单调钟为准重建基线，避免锁机被无限延长。
        if (wallRemain > monoRemain + CLOCK_SLACK_MS) {
            return Result.Rebaseline(monoRemain)
        }

        // 取较大值：把系统时间往后调，墙钟剩余变成负数，但单调钟不受影响
        return Result.Remaining(maxOf(wallRemain, monoRemain))
    }
}
