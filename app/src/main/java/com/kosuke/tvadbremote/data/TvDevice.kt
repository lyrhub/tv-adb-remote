package com.kosuke.tvadbremote.data

/** A TV (or box) reachable over ADB on the LAN. */
data class TvDevice(
    val host: String,
    val port: Int = 5555,
    val name: String = "",        // ro.product.model / brand, filled after connect
    val saved: Boolean = false    // in the user's saved list
) {
    val id: String get() = "$host:$port"
    val displayName: String get() = if (name.isNotBlank()) name else host
}

/** An installed app on the TV. */
data class TvApp(
    val packageName: String,
    val isSystem: Boolean,
    val versionName: String = ""
)
