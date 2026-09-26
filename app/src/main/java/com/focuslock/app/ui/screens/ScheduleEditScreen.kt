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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focuslock.app.R
import com.focuslock.app.data.Schedule
import com.focuslock.app.data.TimeRange
import com.focuslock.app.ui.AppViewModel
import com.focuslock.app.ui.components.CardTitle
import com.focuslock.app.ui.components.ScreenScaffold
import com.focuslock.app.ui.components.SectionCard
import com.focuslock.app.ui.components.SwitchRow
import com.focuslock.app.ui.components.ThinDivider
import com.focuslock.app.util.fmtDuration
import com.focuslock.app.util.weekdayShort

@Composable
fun ScheduleEditScreen(vm: AppViewModel, scheduleId: String?) {
    val ctx = LocalContext.current
    val existing = vm.scheduleById(scheduleId)
    var draft by remember(scheduleId) {
        mutableStateOf(existing ?: vm.newSchedule())
    }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showRangeDialog by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    val isNew = existing == null

    // 保存前的校验：缺什么就明确告诉用户缺什么，不要点了没反应
    fun trySave() {
        saveError = when {
            draft.name.isBlank() -> ctx.getString(R.string.edit_err_name)
            draft.days.isEmpty() -> ctx.getString(R.string.edit_err_days)
            draft.ranges.isEmpty() -> ctx.getString(R.string.edit_err_ranges)
            else -> null
        }
        if (saveError != null) return
        vm.upsertSchedule(draft.copy(name = draft.name.trim()))
        vm.pop()
    }

    ScreenScaffold(
        title = if (isNew) stringResource(R.string.edit_new_title) else stringResource(R.string.edit_edit_title),
        subtitle = stringResource(R.string.edit_weekly_total, fmtDuration(ctx, draft.weeklyMinutes * 60_000L)),
        onBack = { vm.pop() },
        actions = {
            TextButton(onClick = { trySave() }) {
                Text(stringResource(R.string.common_save), fontWeight = FontWeight.SemiBold)
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

            SectionCard {
                CardTitle(stringResource(R.string.edit_name_label))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = {
                        draft = draft.copy(name = it)
                        saveError = null
                    },
                    singleLine = true,
                    isError = saveError != null,
                    placeholder = { Text(stringResource(R.string.edit_name_hint)) },
                    supportingText = if (saveError != null && draft.name.isBlank()) {
                        { Text(saveError!!) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard {
                CardTitle(stringResource(R.string.edit_days_label)) {
                    Text(
                        if (draft.days.isEmpty()) stringResource(R.string.edit_days_none)
                        else pluralStringResource(R.plurals.edit_days_n, draft.days.size, draft.days.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (draft.days.isEmpty()) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                DayPicker(
                    selected = draft.days,
                    onToggle = { d ->
                        val next = draft.days.toMutableSet()
                        if (!next.add(d)) next.remove(d)
                        draft = draft.copy(days = next)
                    }
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickDays(stringResource(R.string.edit_days_every)) { draft = draft.copy(days = (1..7).toSet()) }
                    QuickDays(stringResource(R.string.edit_days_weekdays)) { draft = draft.copy(days = setOf(1, 2, 3, 4, 5)) }
                    QuickDays(stringResource(R.string.edit_days_weekend)) { draft = draft.copy(days = setOf(6, 7)) }
                }
            }

            Spacer(Modifier.height(12.dp))

            SectionCard {
                CardTitle(stringResource(R.string.edit_ranges_label)) {
                    Text(
                        stringResource(R.string.edit_ranges_multi),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))

                if (draft.ranges.isEmpty()) {
                    Text(
                        stringResource(R.string.edit_ranges_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }

                draft.ranges.forEachIndexed { index, range ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                editingIndex = index
                                showRangeDialog = true
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(range.label(ctx), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                fmtDuration(ctx, range.durationMinutes * 60_000L) +
                                    if (range.crossesMidnight) stringResource(R.string.edit_range_next_day) else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (draft.ranges.size > 1) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.common_remove),
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        draft = draft.copy(ranges = draft.ranges.filterIndexed { i, _ -> i != index })
                                    }
                            )
                        }
                    }
                    if (index != draft.ranges.lastIndex) ThinDivider()
                }

                Spacer(Modifier.height(6.dp))
                TextButton(onClick = {
                    editingIndex = null
                    showRangeDialog = true
                }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.edit_add_range))
                }
                Text(
                    stringResource(R.string.edit_range_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard {
                CardTitle(stringResource(R.string.edit_behavior))
                SwitchRow(
                    title = stringResource(R.string.edit_use_whitelist),
                    subtitle = stringResource(R.string.edit_use_whitelist_desc),
                    checked = draft.useWhitelist,
                    onCheckedChange = { draft = draft.copy(useWhitelist = it) }
                )
                ThinDivider()
                SwitchRow(
                    title = stringResource(R.string.common_strict_mode),
                    subtitle = stringResource(R.string.edit_strict_desc),
                    checked = draft.strict,
                    onCheckedChange = { draft = draft.copy(strict = it) }
                )
            }

            saveError?.let { msg ->
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { trySave() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (isNew) stringResource(R.string.edit_create) else stringResource(R.string.edit_save_changes))
            }

            if (!isNew) {
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = {
                        vm.deleteSchedule(draft.id)
                        vm.pop()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.edit_delete), color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showRangeDialog) {
        val editing = editingIndex?.let { draft.ranges.getOrNull(it) }
        RangeDialog(
            initial = editing,
            onDismiss = {
                showRangeDialog = false
                editingIndex = null
            },
            onConfirm = { range ->
                val list = draft.ranges.toMutableList()
                val idx = editingIndex
                if (idx != null && idx in list.indices) list[idx] = range else list.add(range)
                draft = draft.copy(ranges = list.sortedBy { it.startMinute })
                showRangeDialog = false
                editingIndex = null
            }
        )
    }
}

// ---------------------------------------------------------------- 子组件

@Composable
private fun DayPicker(selected: Set<Int>, onToggle: (Int) -> Unit) {
    val ctx = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        (1..7).forEach { d ->
            val on = d in selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (on) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onToggle(d) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    weekdayShort(ctx, d),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuickDays(label: String, onClick: () -> Unit) {
    FilterChip(selected = false, onClick = onClick, label = { Text(label) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeDialog(
    initial: TimeRange?,
    onDismiss: () -> Unit,
    onConfirm: (TimeRange) -> Unit
) {
    val ctx = LocalContext.current
    // 新建时间段时的初值是 00:00 - 00:00，由用户自己改
    val startMin = initial?.startMinute ?: 0
    val endMin = initial?.endMinute ?: 0

    val startState = rememberTimePickerState(
        initialHour = startMin / 60,
        initialMinute = startMin % 60,
        is24Hour = true
    )
    val endState = rememberTimePickerState(
        initialHour = endMin / 60,
        initialMinute = endMin % 60,
        is24Hour = true
    )

    val startNow = startState.hour * 60 + startState.minute
    val endNow = endState.hour * 60 + endState.minute
    val preview = TimeRange(startNow, endNow)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.range_title_new) else stringResource(R.string.range_title_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.range_start), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                TimeInput(state = startState)

                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.range_end), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                TimeInput(state = endState)

                Spacer(Modifier.height(14.dp))
                Text(
                    when {
                        preview.isWholeDay -> stringResource(R.string.range_whole_day)
                        else -> stringResource(
                            if (preview.crossesMidnight) R.string.range_total_next_day
                            else R.string.range_total,
                            fmtDuration(ctx, preview.durationMinutes * 60_000L)
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(TimeRange(startNow, endNow).normalize())
            }) { Text(stringResource(R.string.common_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
