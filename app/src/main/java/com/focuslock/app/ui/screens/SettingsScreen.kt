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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.focuslock.app.R
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
        title = stringResource(R.string.set_title),
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
                CardTitle(stringResource(R.string.set_appearance))
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
                            Text(stringResource(R.string.set_wallpaper_pick), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.set_wallpaper_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        AsyncImage(
                            model = vm.wallpaperUri,
                            contentDescription = stringResource(R.string.set_wallpaper_preview),
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
                            Text(stringResource(R.string.set_wallpaper_remove), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                ThinDivider()
                ClickRow(
                    title = stringResource(R.string.set_quote),
                    value = vm.lockQuote.take(10) + if (vm.lockQuote.length > 10) "…" else "",
                    onClick = {
                        quoteDraft = vm.lockQuote
                        showQuoteDialog = true
                    }
                )
                ThinDivider()

                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.set_dark_mode), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        stringResource(R.string.set_theme_system) to 0,
                        stringResource(R.string.set_theme_light) to 1,
                        stringResource(R.string.set_theme_dark) to 2
                    ).forEach { (label, mode) ->
                        FilterChip(
                            selected = vm.themeMode == mode,
                            onClick = { vm.updateThemeMode(mode) },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                ThinDivider()
                Spacer(Modifier.height(12.dp))

                // 语言名按惯例用各自的语言书写，不翻译
                val currentLang = vm.appLanguage.let { vm.languageRevision; it }
                Text(stringResource(R.string.set_language), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        stringResource(R.string.set_language_system) to "",
                        "简体中文" to "zh-CN",
                        "English" to "en"
                    ).forEach { (label, tag) ->
                        FilterChip(
                            selected = currentLang.equals(tag, ignoreCase = true) ||
                                (tag.isEmpty() && currentLang.isEmpty()),
                            onClick = { vm.updateAppLanguage(tag) },
                            label = { Text(label) }
                        )
                    }
                }
            }

            // -------------------------------------------- 严格模式
            Spacer(Modifier.height(12.dp))
            SectionCard {
                CardTitle(stringResource(R.string.set_strict_rules))
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.set_strict_rules_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    title = stringResource(R.string.set_strict_settings),
                    subtitle = stringResource(R.string.set_strict_settings_desc),
                    checked = vm.strictBlockSettings,
                    onCheckedChange = { vm.updateStrictBlockSettings(it) }
                )
                ThinDivider()
                SwitchRow(
                    title = stringResource(R.string.set_strict_shade),
                    subtitle = stringResource(R.string.set_strict_shade_desc),
                    checked = vm.strictBlockSystemUi,
                    onCheckedChange = { vm.updateStrictBlockSystemUi(it) }
                )
                ThinDivider()
                SwitchRow(
                    title = stringResource(R.string.set_strict_assistant),
                    subtitle = stringResource(R.string.set_strict_assistant_desc),
                    checked = vm.strictBlockAssistant,
                    onCheckedChange = { vm.updateStrictBlockAssistant(it) }
                )
                ThinDivider()
                SwitchRow(
                    title = stringResource(R.string.set_block_uninstall),
                    subtitle = if (PermissionChecks.deviceAdmin(ctx)) {
                        stringResource(R.string.set_block_uninstall_on)
                    } else {
                        stringResource(R.string.set_block_uninstall_off)
                    },
                    checked = vm.blockUninstall,
                    onCheckedChange = { vm.updateBlockUninstall(it) }
                )
                ThinDivider()
                SwitchRow(
                    title = stringResource(R.string.set_lock_screen_on_start),
                    subtitle = stringResource(R.string.set_lock_screen_on_start_desc),
                    checked = vm.lockScreenOnStart,
                    onCheckedChange = { vm.updateLockScreenOnStart(it) }
                )
            }

            // -------------------------------------------- 提醒
            Spacer(Modifier.height(12.dp))
            SectionCard {
                CardTitle(stringResource(R.string.set_reminders))
                SwitchRow(
                    title = stringResource(R.string.set_vibrate),
                    subtitle = stringResource(R.string.set_vibrate_desc),
                    checked = vm.vibrate,
                    onCheckedChange = { vm.updateVibrate(it) }
                )
            }

            // -------------------------------------------- 紧急解锁
            Spacer(Modifier.height(12.dp))
            SectionCard {
                CardTitle(stringResource(R.string.set_emergency))
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.set_emergency_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                ClickRow(
                    title = if (vm.emergencyPassword.isBlank()) stringResource(R.string.set_emergency_none)
                    else stringResource(
                        R.string.set_emergency_set,
                        "•".repeat(vm.emergencyPassword.length.coerceAtMost(8))
                    ),
                    subtitle = if (vm.emergencyPassword.isBlank()) stringResource(R.string.set_emergency_none_action)
                    else stringResource(R.string.set_emergency_set_action),
                    showArrow = false,
                    onClick = { showPasswordDialog = true }
                )
            }

            // -------------------------------------------- 权限与关于
            Spacer(Modifier.height(12.dp))
            SectionCard {
                ClickRow(
                    title = stringResource(R.string.set_permissions),
                    subtitle = stringResource(R.string.set_permissions_desc),
                    onClick = { vm.push(Screen.Permissions) }
                )
                ThinDivider()
                ClickRow(
                    title = stringResource(R.string.set_about),
                    subtitle = stringResource(R.string.set_about_desc),
                    showArrow = false,
                    onClick = { }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showQuoteDialog) {
        TextInputDialog(
            title = stringResource(R.string.set_quote),
            initial = quoteDraft,
            placeholder = stringResource(R.string.set_quote_hint),
            onDismiss = { showQuoteDialog = false },
            onConfirm = {
                vm.setQuote(it.trim())
                showQuoteDialog = false
            }
        )
    }

    if (showPasswordDialog) {
        TextInputDialog(
            title = stringResource(R.string.set_emergency_pw_title),
            initial = vm.emergencyPassword,
            placeholder = stringResource(R.string.set_emergency_pw_hint),
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
            TextButton(onClick = { onConfirm(value) }) { Text(stringResource(R.string.common_save), fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
