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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
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
                title = "无障碍服务（应用监视）",
                desc = "锁机期间识别当前打开的应用，白名单之外的一律弹回锁机界面。" +
                    "这是拦截功能的核心，不开启则锁机只能靠自觉。",
                required = true,
                granted = PermissionChecks.accessibility(ctx),
                onGo = { PermissionIntents.open(ctx, PermissionIntents.accessibility()) }
            ),
            PermItem(
                key = "overlay",
                title = "悬浮窗权限",
                desc = "Android 10 起禁止应用在后台直接弹出界面。授予悬浮窗权限后，" +
                    "锁机到点才能自动全屏弹出。不开的话到点只会有通知，需要手动点开。",
                required = true,
                granted = PermissionChecks.overlay(ctx),
                onGo = { PermissionIntents.open(ctx, PermissionIntents.overlay(ctx)) }
            ),
            PermItem(
                key = "notification",
                title = "通知权限",
                desc = "用于显示锁机剩余时间的常驻通知，以及到点提醒。",
                required = true,
                granted = PermissionChecks.notifications(ctx),
                onGo = { PermissionIntents.open(ctx, PermissionIntents.appNotificationSettings(ctx)) }
            ),
            PermItem(
                key = "alarm",
                title = "精确闹钟",
                desc = "保证锁机在整点准时开始、准时结束。未授权时系统会延迟几分钟，锁机会晚一点生效。",
                required = true,
                granted = PermissionChecks.exactAlarm(ctx),
                onGo = { PermissionIntents.open(ctx, PermissionIntents.exactAlarm(ctx)) }
            ),
            PermItem(
                key = "battery",
                title = "忽略电池优化",
                desc = "把本应用加入电池优化白名单，避免锁机服务在后台被系统冻结或杀掉。",
                required = true,
                granted = PermissionChecks.batteryUnrestricted(ctx),
                onGo = { PermissionIntents.open(ctx, PermissionIntents.battery(ctx)) }
            ),
            PermItem(
                key = "usage",
                title = "使用情况访问（可选）",
                desc = "在无障碍之外提供一层兜底判定。绝大多数情况下不开启也能正常工作。",
                required = false,
                granted = PermissionChecks.usageAccess(ctx),
                onGo = { PermissionIntents.open(ctx, PermissionIntents.usageAccess()) }
            ),
            PermItem(
                key = "admin",
                title = "设备管理员（可选）",
                desc = "锁机期间阻止卸载或强行停止本应用，让锁机更难被中途破坏。",
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
        title = "权限中心",
        subtitle = "必需项 $doneRequired/$totalRequired 已完成",
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
                            if (allGood) "全部必需权限已就绪" else "还有 ${totalRequired - doneRequired} 项必需权限未完成",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (allGood) Mint else Amber
                        )
                        Text(
                            if (allGood) "到点会自动锁机，白名单之外的应用会被拦下"
                            else "权限不全时，锁机可能不会自动弹出或不生效",
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
                            text = if (item.granted) "已开启" else "未开启",
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
                                "可选",
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
                                Text("去开启")
                            }
                        } else {
                            TextButton(onClick = item.onGo) { Text("查看") }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "提示：不同品牌的手机（小米 / 华为 / OPPO / vivo 等）在「无障碍」和「后台运行」" +
                    "上还有各自的自启动开关，建议一并允许，锁机才会稳定。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}
