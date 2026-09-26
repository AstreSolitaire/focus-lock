package com.focuslock.app.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.focuslock.app.ui.AppViewModel
import com.focuslock.app.ui.Screen
import com.focuslock.app.ui.components.CardTitle
import com.focuslock.app.ui.components.ClickRow
import com.focuslock.app.ui.components.ScreenScaffold
import com.focuslock.app.ui.components.SectionCard
import com.focuslock.app.ui.components.SwitchRow
import com.focuslock.app.ui.components.ThinDivider
import com.focuslock.app.util.PermissionChecks

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val ctx = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            vm.setWallpaper(uri.toString())
        }
    }

    var quoteDraft by remember { mutableStateOf(vm.lockQuote) }
    var showQuoteDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "设置",
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

            // -------------------------------------------- 锁屏外观
            SectionCard {
                CardTitle("锁屏外观")
                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { pickImage.launch(arrayOf("image/*")) },
                    contentAlignment = Alignment.Center
                ) {
                    if (vm.wallpaperUri.isNullOrBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("点击选择锁屏背景图", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "建议竖版高清图，会自动裁切铺满",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        AsyncImage(
                            model = vm.wallpaperUri,
                            contentDescription = "锁屏背景预览",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                if (!vm.wallpaperUri.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        TextButton(onClick = { vm.setWallpaper(null) }) {
                            Text("移除背景图", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                ThinDivider()
                ClickRow(
                    title = "锁屏文案",
                    value = vm.lockQuote.take(10) + if (vm.lockQuote.length > 10) "…" else "",
                    onClick = {
                        quoteDraft = vm.lockQuote
                        showQuoteDialog = true
                    }
                )
                ThinDivider()

                Spacer(Modifier.height(12.dp))
                Text("深色模式", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("跟随系统" to 0, "浅色" to 1, "深色" to 2).forEach { (label, mode) ->
                        FilterChip(
                            selected = vm.themeMode == mode,
                            onClick = { vm.updateThemeMode(mode) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // -------------------------------------------- 严格模式
            Spacer(Modifier.height(12.dp))
            SectionCard {
                CardTitle("严格模式细则")
                Spacer(Modifier.height(4.dp))
                Text(
                    "只有「严格模式」的计划才会应用以下限制",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = "屏蔽「系统设置」",
                    subtitle = "防止在锁机期间关闭无障碍、修改系统时间或强行停止本应用",
                    checked = vm.strictBlockSettings,
                    onCheckedChange = { vm.updateStrictBlockSettings(it) }
                )
                ThinDivider()
                SwitchRow(
                    title = "屏蔽下拉通知栏",
                    subtitle = "长按电源键会有 8 秒宽限期，保证能正常关机 / 重启",
                    checked = vm.strictBlockShade,
                    onCheckedChange = { vm.updateStrictBlockShade(it) }
                )
                ThinDivider()
                SwitchRow(
                    title = "锁机期间阻止卸载",
                    subtitle = if (PermissionChecks.deviceAdmin(ctx)) {
                        "已生效（需设备管理员权限）"
                    } else {
                        "需要先授予设备管理员权限"
                    },
                    checked = vm.blockUninstall,
                    onCheckedChange = { vm.updateBlockUninstall(it) }
                )
                ThinDivider()
                SwitchRow(
                    title = "锁机开始时熄屏",
                    subtitle = "到点直接黑屏，减少一秒钟的犹豫",
                    checked = vm.lockScreenOnStart,
                    onCheckedChange = { vm.updateLockScreenOnStart(it) }
                )
            }

            // -------------------------------------------- 提醒
            Spacer(Modifier.height(12.dp))
            SectionCard {
                CardTitle("提醒")
                SwitchRow(
                    title = "震动提示",
                    subtitle = "锁机开始与结束时各震一下",
                    checked = vm.vibrate,
                    onCheckedChange = { vm.updateVibrate(it) }
                )
            }

            // -------------------------------------------- 紧急解锁
            Spacer(Modifier.height(12.dp))
            SectionCard {
                CardTitle("紧急解锁")
                Spacer(Modifier.height(4.dp))
                Text(
                    "设置一个密码后，锁机期间长按锁屏进度环可以输入密码提前结束。" +
                        "留空表示完全不给出口，只能等时间走完。密码忘记无法找回。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                ClickRow(
                    title = if (vm.emergencyPassword.isBlank()) "未设置" else "已设置（${"•".repeat(vm.emergencyPassword.length.coerceAtMost(8))}）",
                    subtitle = if (vm.emergencyPassword.isBlank()) "点按设置密码" else "点按修改或清除",
                    showArrow = false,
                    onClick = { showPasswordDialog = true }
                )
            }

            // -------------------------------------------- 权限与关于
            Spacer(Modifier.height(12.dp))
            SectionCard {
                ClickRow(
                    title = "权限中心",
                    subtitle = "逐项检查并申请锁机所需的全部权限",
                    onClick = { vm.push(Screen.Permissions) }
                )
                ThinDivider()
                ClickRow(
                    title = "关于",
                    subtitle = "专注锁机 1.0.0 · 本地运行，不联网，不上传任何数据",
                    showArrow = false,
                    onClick = { }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showQuoteDialog) {
        TextInputDialog(
            title = "锁屏文案",
            initial = quoteDraft,
            placeholder = "写一句鼓励自己的话",
            onDismiss = { showQuoteDialog = false },
            onConfirm = {
                vm.setQuote(it.ifBlank { com.focuslock.app.data.Prefs.DEFAULT_QUOTE })
                showQuoteDialog = false
            }
        )
    }

    if (showPasswordDialog) {
        TextInputDialog(
            title = "紧急解锁密码",
            initial = vm.emergencyPassword,
            placeholder = "留空表示取消紧急解锁",
            onDismiss = { showPasswordDialog = false },
            onConfirm = {
                vm.updateEmergencyPassword(it.trim())
                showPasswordDialog = false
            }
        )
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("保存", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
