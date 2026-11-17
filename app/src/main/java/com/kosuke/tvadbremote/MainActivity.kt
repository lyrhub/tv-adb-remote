package com.kosuke.tvadbremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kosuke.tvadbremote.ui.AppsScreen
import com.kosuke.tvadbremote.ui.DeviceScreen
import com.kosuke.tvadbremote.ui.InfoScreen
import com.kosuke.tvadbremote.ui.RemoteScreen
import com.kosuke.tvadbremote.vm.MainViewModel
import com.kosuke.tvadbremote.vm.Tab

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot(vm: MainViewModel = viewModel()) {
    Scaffold(
        bottomBar = {
            Column {
                Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = vm.status,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
                NavigationBar {
                    NavigationBarItem(
                        selected = vm.tab == Tab.DEVICES,
                        onClick = { vm.tab = Tab.DEVICES },
                        icon = { Text("📺") },
                        label = { Text("设备") }
                    )
                    NavigationBarItem(
                        selected = vm.tab == Tab.REMOTE,
                        onClick = { vm.tab = Tab.REMOTE },
                        icon = { Text("🎮") },
                        label = { Text("遥控") }
                    )
                    NavigationBarItem(
                        selected = vm.tab == Tab.APPS,
                        onClick = { vm.tab = Tab.APPS },
                        icon = { Text("📦") },
                        label = { Text("应用") }
                    )
                    NavigationBarItem(
                        selected = vm.tab == Tab.INFO,
                        onClick = { vm.tab = Tab.INFO },
                        icon = { Text("ℹ️") },
                        label = { Text("信息") }
                    )
                }
            }
        }
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (vm.tab) {
                Tab.DEVICES -> DeviceScreen(vm)
                Tab.REMOTE -> RemoteScreen(vm)
                Tab.APPS -> AppsScreen(vm)
                Tab.INFO -> InfoScreen(vm)
            }
        }
    }
}
