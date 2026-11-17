package com.kosuke.tvadbremote.vm

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kosuke.tvadbremote.adb.AdbClient
import com.kosuke.tvadbremote.adb.AdbKeyManager
import com.kosuke.tvadbremote.data.DeviceStore
import com.kosuke.tvadbremote.data.TvApp
import com.kosuke.tvadbremote.data.TvDevice
import com.kosuke.tvadbremote.net.DeviceScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab { DEVICES, REMOTE, APPS, INFO }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val keyManager = AdbKeyManager(app)
    private val store = DeviceStore(app)

    var tab by mutableStateOf(Tab.DEVICES)
    var status by mutableStateOf("未连接")
        private set
    var busy by mutableStateOf(false)
        private set

    // devices / scanning
    var savedDevices by mutableStateOf(store.load())
        private set
    var scannedDevices by mutableStateOf<List<TvDevice>>(emptyList())
        private set
    var scanning by mutableStateOf(false)
        private set
    var scanProgress by mutableStateOf(0)
        private set
    var localIp by mutableStateOf(DeviceScanner.localIp(app))
        private set

    // connection
    var currentDevice by mutableStateOf<TvDevice?>(null)
        private set
    private var client: AdbClient? = null
    val connected: Boolean get() = client?.isConnected == true && currentDevice != null

    // apps
    var apps by mutableStateOf<List<TvApp>>(emptyList())
        private set
    var appsLoading by mutableStateOf(false)
        private set
    var showSystemApps by mutableStateOf(false)

    // info / screenshot
    var deviceInfo by mutableStateOf("")
        private set
    var screenshot by mutableStateOf<ImageBitmap?>(null)
        private set

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            try {
                block()
            } catch (e: Exception) {
                status = "出错: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    // ---- scanning --------------------------------------------------------

    fun scan() {
        val ip = DeviceScanner.localIp(getApplication()).also { localIp = it }
        if (ip == null) {
            status = "未连接 Wi-Fi，无法扫描"
            return
        }
        viewModelScope.launch {
            scanning = true
            scanProgress = 0
            scannedDevices = emptyList()
            try {
                val hosts = DeviceScanner.scan(ip) { scanProgress = it }
                scannedDevices = hosts.map { host ->
                    TvDevice(host, saved = savedDevices.any { it.host == host })
                }
                status = "扫描完成，找到 ${hosts.size} 台设备"
            } catch (e: Exception) {
                status = "扫描失败: ${e.message}"
            } finally {
                scanning = false
            }
        }
    }

    fun saveDevice(device: TvDevice) {
        store.add(device)
        savedDevices = store.load()
        scannedDevices = scannedDevices.map {
            if (it.host == device.host) it.copy(saved = true) else it
        }
    }

    fun removeSaved(device: TvDevice) {
        store.remove(device)
        savedDevices = store.load()
    }

    // ---- connection --------------------------------------------------------

    fun connect(device: TvDevice) {
        launchBusy {
            withContext(Dispatchers.IO) {
                client?.close()
                val c = AdbClient(device.host, device.port, keyManager)
                c.connect()
                client = c
                val model = runCatching {
                    c.shell("getprop ro.product.brand; getprop ro.product.model")
                        .lines().map { it.trim() }.filter { it.isNotEmpty() }
                        .joinToString(" ")
                }.getOrDefault("")
                val named = device.copy(name = model.ifBlank { device.name })
                currentDevice = named
                if (device.saved && model.isNotBlank()) {
                    store.add(named)
                    savedDevices = store.load()
                }
                status = "已连接: ${named.displayName} (${device.host})"
            }
            tab = Tab.REMOTE
        }
    }

    fun connectManual(host: String, port: Int) {
        if (host.isBlank()) {
            status = "请输入 IP 地址"
            return
        }
        connect(TvDevice(host.trim(), port))
    }

    fun disconnect() {
        client?.close()
        client = null
        currentDevice = null
        apps = emptyList()
        deviceInfo = ""
        screenshot = null
        status = "已断开"
    }

    private suspend fun shell(cmd: String): String = withContext(Dispatchers.IO) {
        val c = client ?: throw IllegalStateException("未连接设备")
        try {
            c.shell(cmd)
        } catch (e: Exception) {
            // One transparent reconnect attempt (TVs drop idle connections).
            val d = currentDevice ?: throw e
            val nc = AdbClient(d.host, d.port, keyManager)
            nc.connect()
            client = nc
            nc.shell(cmd)
        }
    }

    // ---- remote ------------------------------------------------------------

    fun key(code: Int) {
        if (!connected) return
        launchBusy { shell("input keyevent $code") }
    }

    fun inputText(text: String) {
        if (text.isBlank()) return
        launchBusy {
            // `input text` needs spaces escaped as %s
            shell("input text ${text.replace(" ", "%s")}")
        }
    }

    fun takeScreenshot() {
        if (!connected) return
        launchBusy {
            val png = withContext(Dispatchers.IO) {
                val c = client ?: throw IllegalStateException("未连接设备")
                c.execRaw("screencap -p")
            }
            val bmp = BitmapFactory.decodeByteArray(png, 0, png.size)
            screenshot = bmp?.asImageBitmap()
            status = if (bmp == null) "截图解析失败" else "截图已更新"
        }
    }

    // ---- apps ---------------------------------------------------------------

    fun loadApps() {
        if (!connected) return
        viewModelScope.launch {
            appsLoading = true
            try {
                val out = shell("pm list packages -f")
                val parsed = out.lines().mapNotNull { line ->
                    // package:/path/base.apk=com.example.app
                    val pkg = line.substringAfterLast('=', "").trim()
                    if (pkg.isEmpty()) null
                    else TvApp(pkg, isSystem = line.contains("/system/") || line.contains("/product/"))
                }.distinctBy { it.packageName }
                apps = parsed.sortedWith(compareBy({ it.isSystem }, { it.packageName }))
                status = "已加载 ${apps.size} 个应用"
            } catch (e: Exception) {
                status = "加载应用失败: ${e.message}"
            } finally {
                appsLoading = false
            }
        }
    }

    fun launchApp(app: TvApp) = launchBusy {
        shell("monkey -p ${app.packageName} -c android.intent.category.LAUNCHER 1")
        status = "已启动 ${app.packageName}"
    }

    fun stopApp(app: TvApp) = launchBusy {
        shell("am force-stop ${app.packageName}")
        status = "已强停 ${app.packageName}"
    }

    fun clearAppData(app: TvApp) = launchBusy {
        shell("pm clear ${app.packageName}")
        status = "已清除 ${app.packageName} 的数据"
    }

    fun uninstallApp(app: TvApp) = launchBusy {
        val out = shell("pm uninstall ${app.packageName}")
        status = "卸载 ${app.packageName}: ${out.trim()}"
        loadApps()
    }

    fun appDetails(app: TvApp) = launchBusy {
        val out = shell("dumpsys package ${app.packageName} | grep -E 'versionName|versionCode|firstInstallTime|lastUpdateTime|installerPackageName'")
        deviceInfo = "${app.packageName}\n$out"
        tab = Tab.INFO
    }

    /** Install an APK picked on the phone: push to /data/local/tmp, then pm install. */
    fun installApk(uri: Uri) {
        if (!connected) return
        val ctx = getApplication<Application>()
        launchBusy {
            val bytes = withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)?.readBytes()
                    ?: throw IllegalStateException("读不到所选文件")
            }
            status = "正在上传 APK (${bytes.size / 1024} KB)…"
            withContext(Dispatchers.IO) {
                val c = client ?: throw IllegalStateException("未连接设备")
                c.shellStdin("cat > /data/local/tmp/upload.apk", bytes)
            }
            val out = shell("pm install -r /data/local/tmp/upload.apk; rm -f /data/local/tmp/upload.apk")
            status = "安装结果: ${out.trim().ifBlank { "(无输出)" }}"
            loadApps()
        }
    }

    // ---- device info / power -------------------------------------------------

    fun loadDeviceInfo() {
        if (!connected) return
        launchBusy {
            val props = shell(
                "echo 品牌:; getprop ro.product.brand; echo 型号:; getprop ro.product.model;" +
                    " echo Android:; getprop ro.build.version.release; echo SDK:; getprop ro.build.version.sdk;" +
                    " echo 分辨率:; wm size; echo 密度:; wm density;" +
                    " echo 存储:; df -h /data | tail -1; echo 内存:; cat /proc/meminfo | head -3;" +
                    " echo 电量:; dumpsys battery | grep -E 'level|status' | head -3"
            )
            deviceInfo = props
        }
    }

    fun reboot() = launchBusy {
        shell("reboot")
        status = "已发送重启指令"
        disconnect()
    }

    fun openTvSettings() = launchBusy {
        shell("am start -a android.settings.SETTINGS")
    }
}
