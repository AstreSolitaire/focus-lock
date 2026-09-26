package com.focuslock.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.focuslock.app.data.Prefs
import com.focuslock.app.logic.LockUiState
import com.focuslock.app.ui.theme.LocalLockPalette
import com.focuslock.app.util.fmtClock
import com.focuslock.app.util.fmtCountdown
import com.focuslock.app.util.fmtDate
import com.focuslock.app.util.fmtWeekday
import kotlinx.coroutines.delay
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import com.focuslock.app.data.AppCatalog
import com.focuslock.app.data.AppEntry
import com.focuslock.app.logic.LockRuntime

/**
 * 锁机全屏界面。
 *
 * 组成：自选背景图（或渐变兜底）→ 暗色蒙层 → 中心进度环（环内是当前时间）
 * → 剩余倒计时胶囊 → 计划信息 → 底部文案。
 */
@Composable
fun LockScreen(
    state: LockUiState,
    quote: String,
    whitelistCount: Int,
    emergencyEnabled: Boolean,
    onEmergencyUnlock: (String) -> Boolean
) {
    val palette = LocalLockPalette.current
    val ctx = LocalContext.current

    // 白名单里可以直接打开的应用。白名单没启用或为空时不做这件事。
    val allowedApps by produceState(initialValue = emptyList<AppEntry>(), state.useWhitelist) {
        value = if (state.useWhitelist) {
            AppCatalog.loadLaunchable(ctx, Prefs.whitelist)
        } else {
            emptyList()
        }
    }
    var showAppLauncher by remember { mutableStateOf(false) }

    // 每秒跳动一次的系统时间
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    var showEmergency by remember { mutableStateOf(false) }

    val progressTarget = if (state.totalMs > 0L) {
        (1f - state.remainingMs.toFloat() / state.totalMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val progress by animateFloatAsState(progressTarget, tween(600), label = "progress")

    val wallpaperUri = Prefs.wallpaperUri
    val hasWallpaper = !wallpaperUri.isNullOrBlank()

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(GRADIENT))) {
        if (hasWallpaper) {
            AsyncImage(
                model = wallpaperUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 蒙层：有背景图时压得重一些，保证任何图上文字都清晰；
        // 没有背景图时只轻轻压一层，让底下那层紫蓝渐变透出来。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        if (hasWallpaper) {
                            listOf(Color(0x99000000), Color(0xCC000000), Color(0xE6000000))
                        } else {
                            listOf(Color(0x14000000), Color(0x2E000000), Color(0x6B000000))
                        }
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusChip(name = state.scheduleName, strict = state.strict)

            Spacer(Modifier.weight(1f))

            ProgressRing(
                progress = progress,
                clockText = fmtClock(nowMs),
                dateText = "${fmtDate(nowMs)} ${fmtWeekday(nowMs)}",
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f)
                    .pointerInput(emergencyEnabled) {
                        if (emergencyEnabled) {
                            detectTapGestures(onLongPress = { showEmergency = true })
                        }
                    }
            )

            Spacer(Modifier.height(28.dp))

            RemainingPill(remainingMs = state.remainingMs)

            Spacer(Modifier.height(14.dp))

            Text(
                text = "预计 ${fmtClock(state.endAt)} 解锁",
                color = palette.faint,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (allowedApps.isNotEmpty()) {
                    AllowListButton(
                        count = allowedApps.size,
                        onClick = { showAppLauncher = true }
                    )
                    Spacer(Modifier.height(2.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoTag(
                        text = if (state.useWhitelist) "白名单 $whitelistCount 个应用可用" else "白名单已关闭"
                    )
                    InfoTag(text = if (state.strict) "严格模式" else "普通模式")
                }

                Text(
                    text = quote,
                    color = palette.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Text(
                    text = if (emergencyEnabled) "长按进度环可输入紧急解锁密码" else "专注期间请勿离开",
                    color = palette.faint,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }

    if (showAppLauncher) {
        WhitelistLauncherDialog(
            apps = allowedApps,
            onDismiss = { showAppLauncher = false },
            onLaunch = { pkg ->
                // 先开一段放行期，让系统跟着弹出来的确认框 / 权限框能正常显示
                LockRuntime.markAppLaunch()
                AppCatalog.launch(ctx, pkg)
                showAppLauncher = false
            }
        )
    }

    if (showEmergency) {
        EmergencyDialog(
            onDismiss = { showEmergency = false },
            onConfirm = { pw ->
                val ok = onEmergencyUnlock(pw)
                if (ok) showEmergency = false
                ok
            }
        )
    }
}

// ---------------------------------------------------------------- 子组件

@Composable
private fun StatusChip(name: String, strict: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x1FFFFFFF))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(if (strict) Color(0xFFFF6B6B) else Color(0xFF4ADEA8))
        )
        Text(
            text = name.ifBlank { "专注中" },
            color = Color.White,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
private fun ProgressRing(
    progress: Float,
    clockText: String,
    dateText: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalLockPalette.current
    val config = LocalConfiguration.current
    val clockSize = if (config.screenHeightDp < 640) 54.sp else 68.sp

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 环后面的一圈柔光，让界面不至于是一块死黑
            val glowRadius = size.minDimension * 0.78f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.ring.copy(alpha = 0.30f), Color.Transparent),
                    center = center,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = center
            )

            val strokePx = size.minDimension * 0.042f
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = palette.ringTrack,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            if (progress > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(palette.ring, Color(0xFFFFC664), palette.ring)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = clockText,
                color = palette.clock,
                fontSize = clockSize,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-1).sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = dateText,
                color = palette.subtitle,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun RemainingPill(remainingMs: Long) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x26FFFFFF))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "剩余",
            color = Color(0xCCFFFFFF),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = fmtCountdown(remainingMs),
            color = Color.White,
            style = TextStyle(
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
        )
    }
}

/** 锁屏上的「白名单应用」入口。没有它，白名单就是摆设——用户根本够不着那些应用。 */
@Composable
private fun AllowListButton(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x2EFFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
        Text(
            "白名单应用（$count）",
            color = Color.White,
            style = MaterialTheme.typography.titleSmall
        )
        Icon(
            Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = Color(0xCCFFFFFF),
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 点开后的白名单应用列表，点一下直接启动 */
@Composable
private fun WhitelistLauncherDialog(
    apps: List<AppEntry>,
    onDismiss: () -> Unit,
    onLaunch: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 18.dp)
        ) {
            Text(
                "白名单应用",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "点按即可打开；按 Home 键会回到锁机界面",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 22.dp)
            )
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
            ) {
                items(apps, key = { it.pkg }) { app ->
                    LauncherRow(app = app, onClick = { onLaunch(app.pkg) })
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Text("继续专注")
            }
        }
    }
}

@Composable
private fun LauncherRow(app: AppEntry, onClick: () -> Unit) {
    val ctx = LocalContext.current
    var icon by remember(app.pkg) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(app.pkg) {
        icon = AppCatalog.loadIcon(ctx, app.pkg)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val bmp = icon
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp)
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
        Text(
            app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun InfoTag(text: String) {
    Text(
        text = text,
        color = Color(0xB3FFFFFF),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x1AFFFFFF))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun EmergencyDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Boolean
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("紧急解锁") },
        text = {
            Column {
                Text(
                    "输入你设置的紧急密码可以立刻结束本次锁机，" +
                        "这次记录会被标记为「未完成」。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(14.dp))
                BasicTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        error = false
                    },
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
                if (error) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "密码不正确",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (!onConfirm(input)) error = true
            }) { Text("解锁") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("继续专注") }
        }
    )
}

/** 默认背景：顶部深邃、中段被进度环的光晕提亮、底部收暗 */
private val GRADIENT = listOf(
    Color(0xFF1B1440),
    Color(0xFF34268A),
    Color(0xFF120D2E)
)
