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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.focuslock.app.R
import com.focuslock.app.ui.AppViewModel
import com.focuslock.app.ui.components.ScreenScaffold
import com.focuslock.app.ui.components.SectionCard
import com.focuslock.app.ui.components.StatusPill
import com.focuslock.app.ui.components.ThinDivider
import com.focuslock.app.ui.theme.Amber
import com.focuslock.app.ui.theme.Mint
import com.focuslock.app.util.PermissionChecks
import com.focuslock.app.util.PermissionIntents

private data class PermItem(
    val key: String,
    val title: String,
    val desc: String,
    val required: Boolean,
    val granted: Boolean,
    val onGo: () -> Unit
)

@Composable
fun PermissionsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }

    // 从系统设置页返回时自动重新检测
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_500L)
            tick++
        }
    }

    val items = remember(tick, vm.masterEnabled) {
        listOf(
            PermItem(
                key = "accessibility",
                title = ctx.getString(R.string.perm_accessibility_title),
                desc = ctx.getString(R.string.perm_accessibility_desc),
                required = true,
                granted = PermissionChecks.accessibility(ctx),
                onGo = { PermissionIntents.open(ctx, PermissionIntents.accessibility()) }
            ),
            PermItem(
                key = "overlay",
                title = ctx.getString(R.string.perm_overlay_title),
                desc = ctx.getString(R.string.perm_overlay_desc),
                required = true,
                granted = PermissionChecks.overlay(ctx),
                onGo = { PermissionIntents.open(ctx, PermissionIntents.overlay(ctx)) }
            ),
            PermItem(
                key = "notification",
                title = ctx.getString(R.string.perm_notification_title),
                desc = ctx.getString(R.string.perm_notification_desc),
                required = true,
                granted = PermissionChecks.notifications(ctx),
                onGo = { PermissionIntents.open(ctx, PermissionIntents.appNotificationSettings(ctx)) }
            ),
            PermItem(
                key = "alarm",
                title = ctx.getString(R.string.perm_alarm_title),
                desc = ctx.getString(R.string.perm_alarm_desc),
                required = true,
                granted = PermissionChecks.exactAlarm(ctx),
                onGo = { PermissionIntents.open(ctx, PermissionIntents.exactAlarm(ctx)) }
            ),
            PermItem(
                key = "battery",
                title = ctx.getString(R.string.perm_battery_title),
                desc = ctx.getString(R.string.perm_battery_desc),
                required = true,
                granted = PermissionChecks.batteryUnrestricted(ctx),
                onGo = { PermissionIntents.open(ctx, PermissionIntents.battery(ctx)) }
            ),
            PermItem(
                key = "usage",
                title = ctx.getString(R.string.perm_usage_title),
                desc = ctx.getString(R.string.perm_usage_desc),
                required = false,
                granted = PermissionChecks.usageAccess(ctx),
                onGo = { PermissionIntents.open(ctx, PermissionIntents.usageAccess()) }
            ),
            PermItem(
                key = "admin",
                title = ctx.getString(R.string.perm_admin_title),
                desc = ctx.getString(R.string.perm_admin_desc),
                required = false,
                granted = PermissionChecks.deviceAdmin(ctx),
                onGo = { PermissionIntents.open(ctx, PermissionIntents.deviceAdmin(ctx)) }
            )
        )
    }

    val doneRequired = items.count { it.required && it.granted }
    val totalRequired = items.count { it.required }
    val allGood = doneRequired == totalRequired

    ScreenScaffold(
        title = stringResource(R.string.perm_title),
        subtitle = stringResource(R.string.perm_subtitle, doneRequired, totalRequired),
        onBack = { vm.pop() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (allGood) Mint.copy(alpha = 0.12f)
                        else Amber.copy(alpha = 0.12f)
                    )
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (allGood) Icons.Filled.Check else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (allGood) Mint else Amber,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (allGood) stringResource(R.string.perm_all_ok)
                            else pluralStringResource(
                                R.plurals.perm_still_missing,
                                totalRequired - doneRequired,
                                totalRequired - doneRequired
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (allGood) Mint else Amber
                        )
                        Text(
                            if (allGood) stringResource(R.string.perm_all_ok_desc)
                            else stringResource(R.string.perm_missing_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            items.forEach { item ->
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        StatusPill(
                            text = if (item.granted) stringResource(R.string.common_enabled)
                            else stringResource(R.string.common_disabled),
                            container = if (item.granted) Mint.copy(alpha = 0.15f)
                            else Color(0x1FEF4444),
                            content = if (item.granted) Mint else Color(0xFFEF4444)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        item.desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!item.required) {
                            Text(
                                stringResource(R.string.common_optional),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        if (!item.granted) {
                            Button(
                                onClick = item.onGo,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(stringResource(R.string.perm_go))
                            }
                        } else {
                            TextButton(onClick = item.onGo) { Text(stringResource(R.string.common_view)) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.perm_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
