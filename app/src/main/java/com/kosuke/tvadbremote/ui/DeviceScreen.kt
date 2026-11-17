package com.kosuke.tvadbremote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kosuke.tvadbremote.data.TvDevice
import com.kosuke.tvadbremote.vm.MainViewModel

@Composable
fun DeviceScreen(vm: MainViewModel) {
    var manualIp by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("5555") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("局域网设备", style = MaterialTheme.typography.titleLarge)
            Text(
                "本机 IP: ${vm.localIp ?: "未连接 Wi-Fi"} · 扫描电视的 ADB 端口 (5555)",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        item {
            Button(onClick = { vm.scan() }, enabled = !vm.scanning, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.scanning) "扫描中… ${vm.scanProgress}/254" else "扫描局域网")
            }
            if (vm.scanning) {
                LinearProgressIndicator(
                    progress = { vm.scanProgress / 254f },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
        }

        if (vm.savedDevices.isNotEmpty()) {
            item { Text("已保存", style = MaterialTheme.typography.titleMedium) }
            items(vm.savedDevices, key = { "saved-" + it.id }) { device ->
                DeviceRow(
                    device = device,
                    onClick = { vm.connect(device) },
                    trailing = {
                        IconButton(onClick = { vm.removeSaved(device) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                )
            }
        }

        if (vm.scannedDevices.isNotEmpty()) {
            item { Text("扫描结果", style = MaterialTheme.typography.titleMedium) }
            items(vm.scannedDevices, key = { "scan-" + it.id }) { device ->
                DeviceRow(
                    device = device,
                    onClick = { vm.connect(device) },
                    trailing = {
                        if (!device.saved) {
                            IconButton(onClick = { vm.saveDevice(device) }) {
                                Icon(Icons.Default.Star, contentDescription = "保存")
                            }
                        }
                    }
                )
            }
        }

        item {
            Text("手动连接", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = { manualIp = it },
                    label = { Text("电视 IP") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = manualPort,
                    onValueChange = { manualPort = it.filter { c -> c.isDigit() } },
                    label = { Text("端口") },
                    singleLine = true,
                    modifier = Modifier.weight(0.4f)
                )
            }
            Button(
                onClick = { vm.connectManual(manualIp, manualPort.toIntOrNull() ?: 5555) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("连接") }
            Text(
                "提示: 电视需开启「网络调试/ADB 调试」；首次连接若电视弹出授权框，请在电视上点允许。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun DeviceRow(
    device: TvDevice,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(device.displayName, style = MaterialTheme.typography.titleMedium)
                Text("${device.host}:${device.port}", style = MaterialTheme.typography.bodySmall)
            }
            trailing()
        }
    }
}
