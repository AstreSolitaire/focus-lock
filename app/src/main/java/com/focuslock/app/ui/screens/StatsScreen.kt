package com.focuslock.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.app.data.DayStat
import com.focuslock.app.data.StatsStore
import com.focuslock.app.ui.AppViewModel
import com.focuslock.app.ui.components.CardTitle
import com.focuslock.app.ui.components.EmptyState
import com.focuslock.app.ui.components.ScreenScaffold
import com.focuslock.app.ui.components.SectionCard
import com.focuslock.app.ui.components.StatBadge
import com.focuslock.app.ui.components.StatusPill
import com.focuslock.app.ui.components.ThinDivider
import com.focuslock.app.ui.theme.Amber
import com.focuslock.app.ui.theme.Coral
import com.focuslock.app.ui.theme.Mint
import com.focuslock.app.util.fmtClock
import com.focuslock.app.util.fmtDate
import com.focuslock.app.util.fmtDuration
import com.focuslock.app.util.weekdayCn

@Composable
fun StatsScreen(vm: AppViewModel) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    val best = stats.days.maxByOrNull { it.ms }

    ScreenScaffold(
        title = "锁机数据",
        subtitle = if (stats.totalMs > 0) "累计专注 ${fmtDuration(stats.totalMs)}" else "还没有记录"
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBadge("今日", fmtDuration(stats.todayMs), Modifier.weight(1f))
                StatBadge("本周", fmtDuration(stats.weekMs), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBadge("本月", fmtDuration(stats.monthMs), Modifier.weight(1f))
                StatBadge("累计", fmtDuration(stats.totalMs), Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))

            SectionCard {
                CardTitle("最近 7 天") {
                    if (best != null && best.ms > 0) {
                        Text(
                            "最佳 ${fmtDuration(best.ms)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Amber
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                WeekChart(stats.days)
            }

            Spacer(Modifier.height(12.dp))

            SectionCard {
                CardTitle("锁机记录") {
                    Text(
                        "${stats.recent.size} 条",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (stats.recent.isEmpty()) {
                    EmptyState(
                        title = "还没有完成过锁机",
                        subtitle = "锁机到点结束后，这里会留下每一次的记录"
                    )
                } else {
                    stats.recent.take(20).forEachIndexed { index, log ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${fmtDate(log.startAt)} ${fmtClock(log.startAt)} - ${fmtClock(log.endAt)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    log.scheduleName.ifBlank { "未命名计划" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                fmtDuration(log.durationMs),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(8.dp))
                            StatusPill(
                                text = if (log.completed) "完成" else "中断",
                                container = if (log.completed) Mint.copy(alpha = 0.15f)
                                else Coral.copy(alpha = 0.15f),
                                content = if (log.completed) Mint else Coral
                            )
                        }
                        if (index != stats.recent.take(20).lastIndex) ThinDivider()
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(
                onClick = { confirmClear = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清空全部统计数据", color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空统计数据") },
            text = { Text("累计时长与全部锁机记录都会被删除，计划与白名单不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    StatsStore.clearAll()
                    vm.refreshStats()
                    confirmClear = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun WeekChart(days: List<DayStat>) {
    val maxMs = remember(days) { days.maxOfOrNull { it.ms }?.coerceAtLeast(1L) ?: 1L }
    val today = remember { java.time.LocalDate.now() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        days.forEach { day ->
            val ratio = day.ms.toFloat() / maxMs.toFloat()
            val barHeight = (24f + 86f * ratio).dp
            val isToday = day.date == today

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    if (day.ms > 0) "${day.ms / 60_000L}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (day.ms <= 0) {
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            } else if (isToday) {
                                Brush.verticalGradient(
                                    listOf(MaterialTheme.colorScheme.primary, Color_IndigoLight)
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    weekdayCn(day.date.dayOfWeek.value).removePrefix("周"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private val Color_IndigoLight = androidx.compose.ui.graphics.Color(0xFF8B7BFF)
