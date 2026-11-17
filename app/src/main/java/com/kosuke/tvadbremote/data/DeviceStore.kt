package com.kosuke.tvadbremote.data

import android.content.Context

/** Persists saved devices (host|port|name lines) in SharedPreferences. */
class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("devices", Context.MODE_PRIVATE)

    fun load(): List<TvDevice> {
        val raw = prefs.getString("saved", "") ?: ""
        return raw.lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size < 2) null
            else TvDevice(parts[0], parts[1].toIntOrNull() ?: 5555, parts.getOrNull(2) ?: "", saved = true)
        }
    }

    fun add(device: TvDevice) {
        val list = load().filter { it.id != device.id } + device.copy(saved = true)
        save(list)
    }

    fun remove(device: TvDevice) {
        save(load().filter { it.id != device.id })
    }

    private fun save(list: List<TvDevice>) {
        val raw = list.joinToString("\n") { "${it.host}|${it.port}|${it.name}" }
        prefs.edit().putString("saved", raw).apply()
    }
}
