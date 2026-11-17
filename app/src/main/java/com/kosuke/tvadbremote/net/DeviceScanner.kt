package com.kosuke.tvadbremote.net

import android.content.Context
import android.net.wifi.WifiManager
import com.kosuke.tvadbremote.adb.AdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Scans the phone's current Wi-Fi subnet for hosts with an open ADB port. */
object DeviceScanner {

    /** Local IPv4 address as dotted string, or null when not on Wi-Fi. */
    fun localIp(context: Context): String? {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val ip = wifi.connectionInfo.ipAddress
        if (ip == 0) return null
        return intToIp(ip)
    }

    private fun intToIp(ip: Int): String =
        "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"

    /**
     * Probes every host in the /24 of [localIp] on [port] with limited
     * parallelism; returns the hosts that answered, in address order.
     */
    suspend fun scan(localIp: String, port: Int = 5555, onProgress: (Int) -> Unit = {}): List<String> =
        coroutineScope {
            val prefix = localIp.substringBeforeLast('.')
            val results = mutableListOf<String>()
            var done = 0
            val jobs = (1..254).chunked(32).map { batch ->
                async(Dispatchers.IO) {
                    batch.mapNotNull { i ->
                        val host = "$prefix.$i"
                        val hit = host != localIp && AdbClient.probe(host, port)
                        synchronized(results) {
                            done++
                            onProgress(done)
                            if (hit) results.add(host)
                        }
                        if (hit) host else null
                    }
                }
            }
            jobs.awaitAll()
            synchronized(results) { results.sortedBy { it.substringAfterLast('.').toInt() } }
        }
}
