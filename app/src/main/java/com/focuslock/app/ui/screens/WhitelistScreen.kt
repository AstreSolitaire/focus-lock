package com.focuslock.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.focuslock.app.data.AppCatalog
import com.focuslock.app.data.AppEntry
import com.focuslock.app.ui.AppViewModel
import com.focuslock.app.ui.components.EmptyState
import com.focuslock.app.ui.components.ScreenScaffold
import com.focuslock.app.ui.components.StatusPill
import com.focuslock.app.logic.LockController
import com.focuslock.app.ui.theme.Coral

@Composable
fun WhitelistScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var onlySelected by remember { mutableStateOf(false) }
    var showSystem by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (vm.catalog.isEmpty()) {
            vm.markCatalogLoading()
            vm.updateCatalog(AppCatalog.load(ctx))
        }
    }

    val selected = vm.whitelist
    val filtered = remember(vm.catalog, query, onlySelected, showSystem, selected) {
        vm.catalog.filter { app ->
            (!onlySelected || app.pkg in selected) &&
                (showSystem || !app.isSystem) &&
                (query.isBlank() || app.label.contains(query, true) || app.pkg.contains(query, true))
        }
    }

    ScreenScaffold(
        title = "白名单",
        subtitle = "锁机期间允许正常使用的应用 · 已选 ${selected.size} 个",
        actions = {
            if (selected.isNotEmpty()) {
                TextButton(onClick = { vm.clearWhitelist() }) { Text("清空") }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "清空",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { query = "" }
                            )
                        }
                    },
                    placeholder = { Text("搜索应用名或包名") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = onlySelected,
                        onClick = { onlySelected = !onlySelected },
                        label = { Text("只看已选") }
                    )
                    FilterChip(
                        selected = showSystem,
                        onClick = { showSystem = !showSystem },
                        label = { Text("显示系统应用") }
                    )
                }
                Spacer(Modifier.height(6.dp))

                if (selected.isNotEmpty()) {
                    val labels = remember(selected) { AppCatalog.resolveLabels(ctx, selected) }
                    Text(
                        "已选：" + labels.values.joinToString("、").take(80) +
                            if (labels.size > 4) " 等 ${labels.size} 个" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(6.dp))
                }
            }

            if (vm.catalogLoading) {
                EmptyState(title = "正在读取应用列表", subtitle = "首次加载需要一点时间")
            } else if (filtered.isEmpty()) {
                EmptyState(
                    title = "没有匹配的应用",
                    subtitle = if (vm.catalog.isEmpty()) "读取不到已安装应用，请检查权限" else "换个关键词试试"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp
                    )
                ) {
                    items(filtered, key = { it.pkg }) { app ->
                        AppRow(
                            app = app,
                            checked = app.pkg in selected,
                            onToggle = { vm.setWhitelisted(app.pkg, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var icon by remember(app.pkg) { mutableStateOf<Bitmap?>(null) }

    // 列表可能有几百项，图标按行懒加载
    LaunchedEffect(app.pkg) {
        icon = AppCatalog.loadIcon(ctx, app.pkg)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle(!checked) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val bmp = icon
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Text(
                    app.label.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (LockController.isNeverAllowed(app.pkg)) {
                    // 本机 AI 助手、系统桌面这类，选了也不生效 —— 明说，免得当成 bug
                    Spacer(Modifier.width(6.dp))
                    StatusPill(
                        text = "锁机时仍拦住",
                        container = Coral.copy(alpha = 0.15f),
                        content = Coral
                    )
                } else if (!app.launchable) {
                    Spacer(Modifier.width(6.dp))
                    StatusPill(
                        text = "无界面",
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        content = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                if (app.isSystem) "系统应用 · ${app.pkg}" else app.pkg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        Checkbox(checked = checked, onCheckedChange = onToggle)
    }
}
