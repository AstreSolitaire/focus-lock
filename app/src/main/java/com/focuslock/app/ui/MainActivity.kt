package com.focuslock.app.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focuslock.app.data.StatsStore
import com.focuslock.app.logic.LockController
import com.focuslock.app.ui.screens.HomeScreen
import com.focuslock.app.ui.screens.PermissionsScreen
import com.focuslock.app.ui.screens.ScheduleEditScreen
import com.focuslock.app.ui.screens.ScheduleListScreen
import com.focuslock.app.ui.screens.SettingsScreen
import com.focuslock.app.ui.screens.StatsScreen
import com.focuslock.app.ui.screens.WhitelistScreen
import com.focuslock.app.ui.theme.FocusLockTheme
import com.focuslock.app.util.PermissionChecks

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LockController.restoreIfNeeded(this)
        StatsStore.refresh()
        enableEdgeToEdge()

        setContent {
            val vm: AppViewModel = viewModel()
            FocusLockTheme(vm.themeMode) {
                MainRoot(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置页返回时，锁机状态可能已经该变了
        LockController.restoreIfNeeded(this)
        LockController.evaluate(this, "main-resume")
        StatsStore.refresh()
    }
}

@Composable
private fun MainRoot(vm: AppViewModel) {
    val ctx = LocalContext.current

    // 首次进入申请通知权限（33+）
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionChecks.notifications(ctx)
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val overlay = vm.stack.lastOrNull()

    BackHandler(enabled = overlay != null) { vm.pop() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (overlay) {
            null -> TabsRoot(vm)
            is Screen.ScheduleEdit -> ScheduleEditScreen(vm, overlay.scheduleId)
            Screen.Settings -> SettingsScreen(vm)
            Screen.Permissions -> PermissionsScreen(vm)
        }
    }
}

@Composable
private fun TabsRoot(vm: AppViewModel) {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = vm.tab == tab,
                        onClick = { vm.selectTab(tab) },
                        icon = { Icon(tab.icon(), contentDescription = stringResource(tab.labelRes)) },
                        label = { Text(stringResource(tab.labelRes)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (vm.tab) {
                Tab.Home -> HomeScreen(vm)
                Tab.Schedules -> ScheduleListScreen(vm)
                Tab.Whitelist -> WhitelistScreen(vm)
                Tab.Stats -> StatsScreen(vm)
            }
        }
    }
}

private fun Tab.icon(): ImageVector = when (this) {
    Tab.Home -> Icons.Filled.Home
    Tab.Schedules -> Icons.Filled.List
    Tab.Whitelist -> Icons.Filled.Lock
    Tab.Stats -> Icons.Filled.DateRange
}
