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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.focuslock.app.data.Schedule
import com.focuslock.app.data.TimeRange
import com.focuslock.app.ui.AppViewModel
import com.focuslock.app.ui.components.CardTitle
import com.focuslock.app.ui.components.ScreenScaffold
import com.focuslock.app.ui.components.SectionCard
import com.focuslock.app.ui.components.SwitchRow
import com.focuslock.app.ui.components.ThinDivider
import com.focuslock.app.util.fmtDuration
import com.focuslock.app.util.weekdayCn

@Composable
fun ScheduleEditScreen(vm: AppViewModel, scheduleId: String?) {
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
            draft.name.isBlank() -> "请先给计划起个名字"
            draft.days.isEmpty() -> "请至少选择一天生效"
            draft.ranges.isEmpty() -> "请至少添加一个锁机时间段"
            else -> null
        }
        if (saveError != null) return
        vm.upsertSchedule(draft.copy(name = draft.name.trim()))
        vm.pop()
    }

    ScreenScaffold(
        title = if (isNew) "新建计划" else "编辑计划",
        subtitle = "每周合计 ${fmtDuration(draft.weeklyMinutes * 60_000L)}",
        onBack = { vm.pop() },
        actions = {
            TextButton(onClick = { trySave() }) {
                Text("保存", fontWeight = FontWeight.SemiBold)
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
                CardTitle("计划名称")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = {
                        draft = draft.copy(name = it)
                        saveError = null
                    },
                    singleLine = true,
                    isError = saveError != null,
                    placeholder = { Text("例如：早自习 / 晚自习 / 睡前断网") },
                    supportingText = if (saveError != null && draft.name.isBlank()) {
                        { Text(saveError!!) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard {
                CardTitle("生效星期") {
                    Text(
                        if (draft.days.isEmpty()) "未选择" else "已选 ${draft.days.size} 天",
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
                    QuickDays("每天") { draft = draft.copy(days = (1..7).toSet()) }
                    QuickDays("工作日") { draft = draft.copy(days = setOf(1, 2, 3, 4, 5)) }
                    QuickDays("周末") { draft = draft.copy(days = setOf(6, 7)) }
                }
            }

            Spacer(Modifier.height(12.dp))

            SectionCard {
                CardTitle("锁机时间段") {
                    Text(
                        "可添加多个",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))

                if (draft.ranges.isEmpty()) {
                    Text(
                        "还没有时间段，点下面的「添加时间段」设定",
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
                            Text(range.label(), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                fmtDuration(range.durationMinutes * 60_000L) +
                                    if (range.crossesMidnight) " · 跨到第二天" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (draft.ranges.size > 1) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "移除",
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
                    Text("添加时间段")
                }
                Text(
                    "结束时间早于开始时间表示跨过午夜，例如 22:30 - 06:00",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard {
                CardTitle("锁机行为")
                SwitchRow(
                    title = "启用白名单",
                    subtitle = "开启后，白名单里的应用在锁机期间仍可正常使用",
                    checked = draft.useWhitelist,
                    onCheckedChange = { draft = draft.copy(useWhitelist = it) }
                )
                ThinDivider()
                SwitchRow(
                    title = "严格模式",
                    subtitle = "屏蔽系统设置与下拉通知栏，并阻止卸载，退出难度最大",
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
                Text(if (isNew) "创建计划" else "保存修改")
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
                    Text("删除这条计划", color = MaterialTheme.colorScheme.error)
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
                    weekdayCn(d).removePrefix("周"),
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
        title = { Text(if (initial == null) "添加时间段" else "修改时间段") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("开始时间", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                TimeInput(state = startState)

                Spacer(Modifier.height(16.dp))
                Text("结束时间", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                TimeInput(state = endState)

                Spacer(Modifier.height(14.dp))
                Text(
                    when {
                        preview.isWholeDay -> "起止相同 = 全天 24 小时锁机"
                        else -> "共 ${fmtDuration(preview.durationMinutes * 60_000L)}" +
                            if (preview.crossesMidnight) "，跨到第二天" else ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(TimeRange(startNow, endNow).normalize())
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
