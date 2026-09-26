package com.focuslock.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.focuslock.app.R
import com.focuslock.app.data.Schedule
import com.focuslock.app.ui.AppViewModel
import com.focuslock.app.ui.Screen
import com.focuslock.app.ui.components.EmptyState
import com.focuslock.app.ui.components.ScreenScaffold
import com.focuslock.app.ui.components.SectionCard
import com.focuslock.app.ui.components.StatusPill
import com.focuslock.app.ui.components.ThinDivider
import com.focuslock.app.ui.theme.Mint
import com.focuslock.app.util.fmtDuration
import com.focuslock.app.util.weekdayShort

@Composable
fun ScheduleListScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val schedules = vm.schedules
    var pendingDelete by remember { mutableStateOf<Schedule?>(null) }

    val weeklyTotal = schedules.filter { it.enabled }
        .sumOf { it.weeklyMinutes.toLong() } * 60_000L

    ScreenScaffold(
        title = stringResource(R.string.sched_title),
        subtitle = stringResource(
            R.string.sched_subtitle,
            schedules.count { it.enabled },
            fmtDuration(ctx, weeklyTotal)
        ),
        floating = {
            FloatingActionButton(
                onClick = { vm.push(Screen.ScheduleEdit(null)) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.sched_new))
            }
        }
    ) { padding ->
        if (schedules.isEmpty()) {
            Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
                EmptyState(
                    title = stringResource(R.string.sched_empty_title),
                    subtitle = stringResource(R.string.sched_empty_body)
                )
            }
            return@ScreenScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(schedules, key = { it.id }) { schedule ->
                ScheduleCard(
                    schedule = schedule,
                    onToggle = { vm.toggleSchedule(schedule.id) },
                    onEdit = { vm.push(Screen.ScheduleEdit(schedule.id)) },
                    onDelete = { pendingDelete = schedule }
                )
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.sched_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.sched_delete_body,
                        target.name.ifBlank { stringResource(R.string.common_unnamed_schedule) }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSchedule(target.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: Schedule,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val ctx = LocalContext.current
    val dim = !schedule.enabled

    SectionCard(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onEdit)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    schedule.name.ifBlank { stringResource(R.string.common_unnamed_schedule) },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.sched_weekly, fmtDuration(ctx, schedule.weeklyMinutes * 60_000L)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = schedule.enabled, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.common_delete),
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onDelete)
            )
        }

        Spacer(Modifier.height(12.dp))
        ThinDivider()
        Spacer(Modifier.height(10.dp))

        DayStrip(days = schedule.days, dim = dim)

        Spacer(Modifier.height(10.dp))

        schedule.ranges.forEach { range ->
            Row(
                modifier = Modifier.padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (dim) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    range.label(ctx),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    fmtDuration(ctx, range.durationMinutes * 60_000L),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(
                text = if (schedule.useWhitelist) stringResource(R.string.sched_pill_whitelist)
                else stringResource(R.string.sched_pill_block_all),
                container = if (schedule.useWhitelist) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                content = if (schedule.useWhitelist) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusPill(
                text = if (schedule.strict) stringResource(R.string.common_strict_mode)
                else stringResource(R.string.common_normal_mode),
                container = if (schedule.strict) Color(0x1FEF4444) else Color(0x1F10B981),
                content = if (schedule.strict) Color(0xFFEF4444) else Mint
            )
        }
    }
}

@Composable
fun DayStrip(days: Set<Int>, dim: Boolean = false) {
    val ctx = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { d ->
            val on = d in days
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        when {
                            !on -> MaterialTheme.colorScheme.surfaceVariant
                            dim -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = weekdayShort(ctx, d),
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        !on -> MaterialTheme.colorScheme.onSurfaceVariant
                        dim -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onPrimary
                    }
                )
            }
        }
    }
}
