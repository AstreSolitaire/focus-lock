package com.focuslock.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.app.logic.LockRuntime
import com.focuslock.app.logic.ScheduleEvaluator
import com.focuslock.app.ui.AppViewModel
import com.focuslock.app.ui.Screen
import com.focuslock.app.ui.Tab
import com.focuslock.app.ui.components.CardTitle
import com.focuslock.app.ui.components.EmptyState
import com.focuslock.app.ui.components.ScreenScaffold
import com.focuslock.app.ui.components.SectionCard
import com.focuslock.app.ui.components.StatBadge
import com.focuslock.app.ui.components.StatusPill
import com.focuslock.app.ui.theme.Amber
import com.focuslock.app.ui.theme.Coral
import com.focuslock.app.ui.theme.Mint
import com.focuslock.app.util.PermissionChecks
import com.focuslock.app.util.fmtClock
import com.focuslock.app.util.fmtCountdown
import com.focuslock.app.util.fmtDateFull
import com.focuslock.app.util.fmtDuration
import com.focuslock.app.util.fmtNextHint
import com.focuslock.app.util.fmtWeekday
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val lockState by LockRuntime.state.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val schedules = vm.schedules
    // 每分钟重算一次时间轴即可
    val minuteBucket = now / 60_000L
    val today = remember(schedules, minuteBucket) {
        ScheduleEvaluator.todayIntervals(schedules, now)
    }
    val next = remember(schedules, minuteBucket) {
        ScheduleEvaluator.nextWindow(schedules, now)
    }

    val issues = remember(minuteBucket) {
        val list = mutableListOf<String>()
        if (!PermissionChecks.accessibility(ctx)) list += "无障碍服务未开启"
        if (!PermissionChecks.overlay(ctx)) list += "悬浮窗权限未授予"
        if (!PermissionChecks.exactAlarm(ctx)) list += "精确闹钟未授权"
        if (!PermissionChecks.batteryUnrestricted(ctx)) list += "未加入电池优化白名单"
        list
    }

    ScreenScaffold(
        title = "专注锁机",
        subtitle = "${fmtDateFull(now)} ${fmtWeekday(now)}",
        actions = {
            IconButton(onClick = { vm.popAll(); vm.push(Screen.Permissions) }) {
                Icon(Icons.Filled.Warning, contentDescription = "权限检查")
            }
            IconButton(onClick = { vm.popAll(); vm.push(Screen.Settings) }) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ---------------------------------------------------- 状态卡
            if (lockState.locked) {
                LockingCard(
                    scheduleName = lockState.scheduleName,
                    remainingMs = lockState.remainingMs,
                    endAt = lockState.endAt,
                    strict = lockState.strict,
                    whitelistCount = vm.whitelist.size,
                    useWhitelist = lockState.useWhitelist,
                    onEnd = { vm.endCurrentLock() }
                )
            } else {
                IdleCard(
                    nextStart = next?.start,
                    nextEnd = next?.end,
                    nextName = next?.schedule?.name,
                    now = now,
                    totalEnabled = schedules.count { it.enabled }
                )
            }

            Spacer(Modifier.height(12.dp))

            // ---------------------------------------------------- 总开关
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("锁机总开关", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (vm.masterEnabled) "到点会自动锁机" else "已暂停，所有计划都不生效",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = vm.masterEnabled,
                        onCheckedChange = { vm.updateMasterEnabled(it) }
                    )
                }
            }

            // ---------------------------------------------------- 权限提示
            if (issues.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionCard(
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Amber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "还有 ${issues.size} 项设置未完成",
                            style = MaterialTheme.typography.titleSmall,
                            color = Amber
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        issues.joinToString("、"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { vm.popAll(); vm.push(Screen.Permissions) }) {
                        Text("去完成设置")
                    }
                }
            }

            // ---------------------------------------------------- 今日时间轴
            Spacer(Modifier.height(12.dp))
            SectionCard {
                CardTitle("今日时间轴") {
                    Text(
                        fmtDuration(today.sumOf { it.end - it.start }),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (today.isEmpty()) {
                    EmptyState(
                        title = "今天没有锁机安排",
                        subtitle = "到「计划」页新建一条，或选择需要生效的星期几"
                    )
                } else {
                    today.forEach { interval ->
                        TimelineRow(
                            name = interval.schedule.name,
                            start = interval.start,
                            end = interval.end,
                            now = now
                        )
                    }
                }
            }

            // ---------------------------------------------------- 手动专注
            Spacer(Modifier.height(12.dp))
            SectionCard {
                CardTitle("立即专注")
                Spacer(Modifier.height(4.dp))
                Text(
                    "不等计划，现在就开始一段锁机（不启用严格模式，可随时结束）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(25, 45, 60, 90).forEach { min ->
                        QuickStartChip(
                            minutes = min,
                            enabled = !lockState.locked,
                            modifier = Modifier.weight(1f),
                            onClick = { vm.startManualLock(min) }
                        )
                    }
                }
            }

            // ---------------------------------------------------- 统计速览
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBadge("今日", fmtDuration(stats.todayMs), Modifier.weight(1f))
                StatBadge("本周", fmtDuration(stats.weekMs), Modifier.weight(1f))
                StatBadge("累计", fmtDuration(stats.totalMs), Modifier.weight(1f))
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

// ---------------------------------------------------------------- 子组件

@Composable
private fun LockingCard(
    scheduleName: String,
    remainingMs: Long,
    endAt: Long,
    strict: Boolean,
    whitelistCount: Int,
    useWhitelist: Boolean,
    onEnd: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF4B3BD6), Color(0xFF7A5AF8)))
            )
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = "锁机中",
                    container = Color(0x33FFFFFF)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    scheduleName,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                fmtCountdown(remainingMs),
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${fmtClock(endAt)} 自动解锁 · " +
                    if (useWhitelist) "白名单 $whitelistCount 个应用可用" else "仅系统必需应用可用",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xCCFFFFFF)
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = if (strict) "严格模式" else "普通模式",
                    container = Color(0x26FFFFFF)
                )
                Spacer(Modifier.weight(1f))
                if (!strict) {
                    TextButton(onClick = onEnd) {
                        Text("结束本次", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleCard(
    nextStart: Long?,
    nextEnd: Long?,
    nextName: String?,
    now: Long,
    totalEnabled: Int
) {
    SectionCard {
        Column {
            StatusPill(
                text = "未锁机",
                container = Mint.copy(alpha = 0.15f),
                content = Mint
            )
            Spacer(Modifier.height(14.dp))
            if (nextStart != null) {
                Text(
                    "距下次锁机",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    fmtCountdown(nextStart - now),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${nextName.orEmpty()} · ${fmtNextHint(nextStart)} 开始，${fmtClock(nextEnd ?: nextStart)} 结束",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "近期没有锁机安排",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (totalEnabled == 0) "当前没有启用中的计划，去「计划」页新建一条吧"
                    else "已启用 $totalEnabled 条计划，但今天不生效",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(name: String, start: Long, end: Long, now: Long) {
    val state = when {
        now >= end -> 0
        now >= start -> 1
        else -> 2
    }
    val (label, color) = when (state) {
        0 -> "已结束" to MaterialTheme.colorScheme.onSurfaceVariant
        1 -> "进行中" to Coral
        else -> "待开始" to MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name.ifBlank { "未命名计划" }, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${fmtClock(start)} - ${fmtClock(end)} · ${fmtDuration(end - start)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StatusPill(
            text = label,
            container = color.copy(alpha = 0.14f),
            content = color
        )
    }
}

@Composable
private fun QuickStartChip(
    minutes: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (enabled) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$minutes 分钟",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
