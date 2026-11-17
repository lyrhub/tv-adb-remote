package com.kosuke.tvadbremote.ui

import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kosuke.tvadbremote.data.TvApp
import com.kosuke.tvadbremote.vm.MainViewModel

private fun NotConnected() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("先在「设备」页连接一台电视")
    }
}

// ---- Remote -----------------------------------------------------------------

@Composable
fun RemoteScreen(vm: MainViewModel) {
    if (!vm.connected) { NotConnected(); return }
    var text by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(vm.currentDevice?.displayName ?: "", style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RemoteButton(Icons.Default.PowerSettingsNew, "电源") { vm.key(KeyEvent.KEYCODE_POWER) }
            RemoteButton(Icons.Default.Menu, "菜单") { vm.key(KeyEvent.KEYCODE_MENU) }
            RemoteButton(Icons.Default.Home, "主页") { vm.key(KeyEvent.KEYCODE_HOME) }
            RemoteButton(Icons.AutoMirrored.Filled.ArrowBack, "返回") { vm.key(KeyEvent.KEYCODE_BACK) }
        }

        Spacer(Modifier.height(4.dp))
        // D-pad
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RemoteButton(Icons.Default.KeyboardArrowUp, "上") { vm.key(KeyEvent.KEYCODE_DPAD_UP) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RemoteButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "左") { vm.key(KeyEvent.KEYCODE_DPAD_LEFT) }
                FilledIconButton(onClick = { vm.key(KeyEvent.KEYCODE_DPAD_CENTER) }, modifier = Modifier.size(64.dp)) {
                    Text("OK")
                }
                RemoteButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "右") { vm.key(KeyEvent.KEYCODE_DPAD_RIGHT) }
            }
            RemoteButton(Icons.Default.KeyboardArrowDown, "下") { vm.key(KeyEvent.KEYCODE_DPAD_DOWN) }
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RemoteButton(Icons.AutoMirrored.Filled.VolumeDown, "音量-") { vm.key(KeyEvent.KEYCODE_VOLUME_DOWN) }
            RemoteButton(Icons.Default.PlayArrow, "播放") { vm.key(KeyEvent.KEYCODE_MEDIA_PLAY) }
            RemoteButton(Icons.Default.Pause, "暂停") { vm.key(KeyEvent.KEYCODE_MEDIA_PAUSE) }
            RemoteButton(Icons.AutoMirrored.Filled.VolumeUp, "音量+") { vm.key(KeyEvent.KEYCODE_VOLUME_UP) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { vm.key(KeyEvent.KEYCODE_CHANNEL_DOWN) }) { Text("频道-") }
            OutlinedButton(onClick = { vm.key(KeyEvent.KEYCODE_CHANNEL_UP) }) { Text("频道+") }
            OutlinedButton(onClick = { vm.openTvSettings() }) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Text(" 电视设置")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("输入文字到电视") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { vm.inputText(text); text = "" }) { Text("发送") }
        }

        Button(onClick = { vm.takeScreenshot() }, modifier = Modifier.fillMaxWidth()) {
            Text("截取电视画面")
        }
        vm.screenshot?.let { shot ->
            Image(
                bitmap = shot,
                contentDescription = "电视截图",
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

@Composable
private fun RemoteButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    FilledIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Icon(icon, contentDescription = label)
    }
}

// ---- Apps -------------------------------------------------------------------

@Composable
fun AppsScreen(vm: MainViewModel) {
    if (!vm.connected) { NotConnected(); return }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.installApk(uri)
    }
    var confirmUninstall by remember { mutableStateOf<TvApp?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { vm.loadApps() }, enabled = !vm.appsLoading, modifier = Modifier.weight(1f)) {
                Text(if (vm.appsLoading) "加载中…" else "刷新应用列表")
            }
            Button(
                onClick = { picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) },
                modifier = Modifier.weight(1f)
            ) { Text("安装 APK") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vm.showSystemApps, onCheckedChange = { vm.showSystemApps = it })
            Text("显示系统应用", modifier = Modifier.padding(start = 8.dp))
        }

        val visible = vm.apps.filter { vm.showSystemApps || !it.isSystem }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.packageName }) { app ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(app.packageName, style = MaterialTheme.typography.titleSmall)
                        if (app.isSystem) {
                            Text("系统应用", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { vm.launchApp(app) }) { Text("打开") }
                            TextButton(onClick = { vm.stopApp(app) }) { Text("强停") }
                            TextButton(onClick = { vm.clearAppData(app) }) { Text("清数据") }
                            TextButton(onClick = { vm.appDetails(app) }) { Text("详情") }
                            TextButton(onClick = { confirmUninstall = app }) { Text("卸载") }
                        }
                    }
                }
            }
        }
    }

    confirmUninstall?.let { app ->
        AlertDialog(
            onDismissRequest = { confirmUninstall = null },
            title = { Text("卸载应用") },
            text = { Text("确定要卸载 ${app.packageName} 吗？") },
            confirmButton = {
                TextButton(onClick = { vm.uninstallApp(app); confirmUninstall = null }) { Text("卸载") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = null }) { Text("取消") }
            }
        )
    }
}

// ---- Info -------------------------------------------------------------------

@Composable
fun InfoScreen(vm: MainViewModel) {
    if (!vm.connected) { NotConnected(); return }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "${vm.currentDevice?.displayName} · ${vm.currentDevice?.host}:${vm.currentDevice?.port}",
            style = MaterialTheme.typography.titleMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.loadDeviceInfo() }, modifier = Modifier.weight(1f)) { Text("读取设备信息") }
            OutlinedButton(onClick = { vm.takeScreenshot() }, modifier = Modifier.weight(1f)) { Text("截图") }
        }
        if (vm.deviceInfo.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(vm.deviceInfo, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        vm.screenshot?.let { shot ->
            Image(bitmap = shot, contentDescription = "电视截图", modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = { vm.reboot() }, modifier = Modifier.fillMaxWidth()) { Text("重启电视") }
        Button(onClick = { vm.disconnect() }, modifier = Modifier.fillMaxWidth()) { Text("断开连接") }
    }
}
