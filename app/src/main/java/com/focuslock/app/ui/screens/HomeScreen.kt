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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.focuslock.app.R
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
        if (!PermissionChecks.accessibility(ctx)) list += ctx.getString(R.string.home_issue_accessibility)
        if (!PermissionChecks.overlay(ctx)) list += ctx.getString(R.string.home_issue_overlay)
        if (!PermissionChecks.exactAlarm(ctx)) list += ctx.getString(R.string.home_issue_alarm)
        if (!PermissionChecks.batteryUnrestricted(ctx)) list += ctx.getString(R.string.home_issue_battery)
        list
    }

    ScreenScaffold(
        title = stringResource(R.string.app_title),
        subtitle = "${fmtDateFull(ctx, now)} ${fmtWeekday(ctx, now)}",
        actions = {
            IconButton(onClick = { vm.popAll(); vm.push(Screen.Permissions) }) {
                Icon(Icons.Filled.Warning, contentDescription = stringResource(R.string.home_check_permissions))
            }
            IconButton(onClick = { vm.popAll(); vm.push(Screen.Settings) }) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.common_settings))
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
                        Text(stringResource(R.string.home_master_switch), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (vm.masterEnabled) stringResource(R.string.home_master_on) else stringResource(R.string.home_master_off),
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
                            pluralStringResource(R.plurals.home_issues_title, issues.size, issues.size),
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
                        Text(stringResource(R.string.home_issues_action))
                    }
                }
            }

            // ---------------------------------------------------- 今日时间轴
            Spacer(Modifier.height(12.dp))
            SectionCard {
                CardTitle(stringResource(R.string.home_timeline)) {
                    Text(
                        fmtDuration(ctx, today.sumOf { it.end - it.start }),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (today.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.home_timeline_empty_title),
                        subtitle = stringResource(R.string.home_timeline_empty_body)
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
                CardTitle(stringResource(R.string.home_quick_focus))
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.home_quick_focus_hint),
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
                StatBadge(stringResource(R.string.home_stat_today), fmtDuration(ctx, stats.todayMs), Modifier.weight(1f))
                StatBadge(stringResource(R.string.home_stat_week), fmtDuration(ctx, stats.weekMs), Modifier.weight(1f))
                StatBadge(stringResource(R.string.home_stat_total), fmtDuration(ctx, stats.totalMs), Modifier.weight(1f))
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
                    text = stringResource(R.string.home_state_locked),
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
                stringResource(R.string.home_unlock_at, fmtClock(endAt)) + " · " +
                    if (useWhitelist) {
                        pluralStringResource(R.plurals.home_whitelist_n, whitelistCount, whitelistCount)
                    } else {
                        stringResource(R.string.home_essentials_only)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xCCFFFFFF)
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = if (strict) stringResource(R.string.common_strict_mode) else stringResource(R.string.common_normal_mode),
                    container = Color(0x26FFFFFF)
                )
                Spacer(Modifier.weight(1f))
                if (!strict) {
                    TextButton(onClick = onEnd) {
                        Text(stringResource(R.string.home_end_now), color = Color.White)
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
    val ctx = LocalContext.current
    SectionCard {
        Column {
            StatusPill(
                text = stringResource(R.string.home_state_idle),
                container = Mint.copy(alpha = 0.15f),
                content = Mint
            )
            Spacer(Modifier.height(14.dp))
            if (nextStart != null) {
                Text(
                    stringResource(R.string.home_until_next),
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
                    stringResource(R.string.home_next_detail, nextName.orEmpty(), fmtNextHint(ctx, nextStart), fmtClock(nextEnd ?: nextStart)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    stringResource(R.string.home_no_upcoming),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (totalEnabled == 0) stringResource(R.string.home_no_enabled)
                    else pluralStringResource(R.plurals.home_enabled_but_not_today, totalEnabled, totalEnabled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(name: String, start: Long, end: Long, now: Long) {
    val ctx = LocalContext.current
    val state = when {
        now >= end -> 0
        now >= start -> 1
        else -> 2
    }
    val (label, color) = when (state) {
        0 -> stringResource(R.string.home_status_done) to MaterialTheme.colorScheme.onSurfaceVariant
        1 -> stringResource(R.string.home_status_active) to Coral
        else -> stringResource(R.string.home_status_upcoming) to MaterialTheme.colorScheme.primary
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
            Text(name.ifBlank { stringResource(R.string.common_unnamed_schedule) }, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${fmtClock(start)} - ${fmtClock(end)} · ${fmtDuration(ctx, end - start)}",
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
            text = stringResource(R.string.common_minutes, minutes),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
