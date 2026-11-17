package com.kosuke.tvadbremote.adb

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal ADB wire-protocol client (no external dependencies).
 *
 * Supports: CNXN handshake, RSA AUTH (token sign + public key offer),
 * shell: commands (text), exec: commands (raw bytes, for screencap),
 * and pushing a file through a shell stream (for APK install).
 *
 * One connection runs one stream at a time; all public operations are
 * synchronized, which is fine for a remote-control app.
 */
class AdbClient(
    val host: String,
    val port: Int = 5555,
    private val keyManager: AdbKeyManager
) {
    companion object {
        private const val CMD_CNXN = 0x4E584E43
        private const val CMD_AUTH = 0x48545541
        private const val CMD_OPEN = 0x4E45504F
        private const val CMD_OKAY = 0x59414B4F
        private const val CMD_CLSE = 0x45534C43
        private const val CMD_WRTE = 0x45545257
        private const val CMD_FAIL = 0x4C494146

        private const val VERSION = 0x01000000
        private const val MAX_DATA = 256 * 1024

        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_RSAPUBLICKEY = 3

        /** Quick check used by the scanner: is anything listening on host:port? */
        fun probe(host: String, port: Int, timeoutMs: Int = 700): Boolean {
            return try {
                Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    private data class Msg(val command: Int, val arg0: Int, val arg1: Int, val payload: ByteArray)

    private var socket: Socket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private val nextLocalId = AtomicInteger(1)

    var banner: String = ""
        private set
    val isConnected: Boolean get() = socket?.isConnected == true && socket?.isClosed == false

    @Synchronized
    fun connect(connectTimeoutMs: Int = 8000, readTimeoutMs: Int = 60_000) {
        close()
        val s = Socket()
        s.connect(InetSocketAddress(host, port), connectTimeoutMs)
        s.soTimeout = readTimeoutMs
        s.tcpNoDelay = true
        socket = s
        input = BufferedInputStream(s.getInputStream())
        output = BufferedOutputStream(s.getOutputStream())

        send(CMD_CNXN, VERSION, MAX_DATA, "host::\u0000".toByteArray(Charsets.UTF_8))
        var msg = read()
        if (msg.command == CMD_AUTH && msg.arg0 == AUTH_TOKEN) {
            // Device wants RSA auth: try signing the token first.
            send(CMD_AUTH, AUTH_SIGNATURE, 0, keyManager.signToken(msg.payload))
            msg = read()
            if (msg.command == CMD_AUTH) {
                // Key not known yet: offer the public key; the TV shows a dialog.
                send(CMD_AUTH, AUTH_RSAPUBLICKEY, 0, keyManager.publicKeyPayload())
                msg = read()
            }
        }
        when (msg.command) {
            CMD_CNXN -> banner = String(msg.payload, Charsets.UTF_8).trim('\u0000')
            CMD_FAIL -> throw IOException("设备拒绝连接 (FAIL)")
            else -> throw IOException("握手失败: 未知响应 0x${msg.command.toString(16)}")
        }
    }

    @Synchronized
    fun close() {
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }

    /** Run a shell command and return its text output. */
    @Synchronized
    fun shell(command: String): String {
        val bytes = runStream("shell:$command", null)
        return String(bytes, Charsets.UTF_8).replace("\r\n", "\n")
    }

    /** Run an exec command and return raw bytes (e.g. screencap PNG). */
    @Synchronized
    fun execRaw(command: String): ByteArray = runStream("exec:$command", null)

    /**
     * Push [data] into a shell stream (e.g. "cat > /data/local/tmp/x.apk"),
     * then close the stream. Used for APK upload without the SYNC service.
     */
    @Synchronized
    fun shellStdin(destination: String, data: ByteArray) {
        runStream("shell:$destination", data)
    }

    // ---- stream plumbing -------------------------------------------------

    private fun runStream(destination: String, stdinData: ByteArray?): ByteArray {
        check(isConnected) { "未连接" }
        val localId = nextLocalId.getAndIncrement()
        send(CMD_OPEN, localId, 0, (destination + "\u0000").toByteArray(Charsets.UTF_8))
        var msg = read()
        if (msg.command == CMD_FAIL) throw IOException("设备拒绝打开 $destination")
        if (msg.command == CMD_CLSE) throw IOException("设备关闭了 $destination")
        check(msg.command == CMD_OKAY) { "OPEN 失败: 0x${msg.command.toString(16)}" }
        val remoteId = msg.arg0

        val out = ByteArrayOutputStream()
        var sentAll = false
        var offset = 0
        if (stdinData == null) sentAll = true

        while (true) {
            // Send pending stdin in MAX_DATA chunks, one WRTE per OKAY cycle.
            if (!sentAll) {
                val chunkLen = minOf(MAX_DATA, stdinData!!.size - offset)
                if (chunkLen > 0) {
                    send(CMD_WRTE, localId, remoteId, stdinData.copyOfRange(offset, offset + chunkLen))
                    offset += chunkLen
                }
                if (offset >= stdinData.size) {
                    send(CMD_CLSE, localId, remoteId, ByteArray(0))
                    sentAll = true
                }
            }
            msg = read()
            when (msg.command) {
                CMD_WRTE -> {
                    out.write(msg.payload)
                    send(CMD_OKAY, localId, remoteId, ByteArray(0))
                }
                CMD_OKAY -> Unit // peer is ready for the next chunk
                CMD_CLSE -> {
                    runCatching { send(CMD_CLSE, localId, remoteId, ByteArray(0)) }
                    break
                }
                CMD_FAIL -> throw IOException("流失败 ($destination)")
            }
        }
        return out.toByteArray()
    }

    // ---- message io ------------------------------------------------------

    private fun send(command: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        val out = output ?: throw IOException("未连接")
        var checksum = 0
        for (b in payload) checksum += b.toInt() and 0xFF
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(command)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(payload.size)
        header.putInt(checksum)
        header.putInt(command.inv())
        out.write(header.array())
        if (payload.isNotEmpty()) out.write(payload)
        out.flush()
    }

    private fun read(): Msg {
        val inp = input ?: throw IOException("未连接")
        val header = ByteArray(24)
        readFully(inp, header)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buf.getInt()
        val arg0 = buf.getInt()
        val arg1 = buf.getInt()
        val len = buf.getInt()
        buf.getInt() // checksum
        buf.getInt() // magic
        val payload = ByteArray(len)
        if (len > 0) readFully(inp, payload)
        return Msg(command, arg0, arg1, payload)
    }

    private fun readFully(inp: InputStream, target: ByteArray) {
        var read = 0
        while (read < target.size) {
            val n = inp.read(target, read, target.size - read)
            if (n < 0) throw IOException("连接被设备关闭")
            read += n
        }
    }
}
