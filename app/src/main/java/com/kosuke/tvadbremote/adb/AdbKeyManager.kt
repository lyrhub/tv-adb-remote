package com.kosuke.tvadbremote.adb

import android.content.Context
import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * Holds the RSA keypair used for ADB authentication.
 *
 * Most TV boxes run adbd without auth, but stock Android TVs ask for it:
 * adbd sends a 20-byte token, we sign it (RSA PKCS#1 v1.5 over SHA-1(token),
 * done manually + raw RSA op, exactly like AOSP expects), and if the device
 * does not know our key yet we send the public key in ADB wire format and the
 * TV shows an "allow debugging?" dialog.
 */
class AdbKeyManager(context: Context) {

    private val keyFile = File(context.filesDir, "adb_keypair.b64")
    val keyPair: KeyPair by lazy { loadOrCreate() }

    private fun loadOrCreate(): KeyPair {
        if (keyFile.exists()) {
            runCatching {
                val lines = keyFile.readText().trim().split("\n")
                val priv = KeyFactory.getInstance("RSA").generatePrivate(
                    PKCS8EncodedKeySpec(Base64.decode(lines[0], Base64.NO_WRAP))
                )
                val pub = KeyFactory.getInstance("RSA").generatePublic(
                    X509EncodedKeySpec(Base64.decode(lines[1], Base64.NO_WRAP))
                )
                return KeyPair(pub, priv)
            }
        }
        val gen = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val pair = gen.generateKeyPair()
        val text = Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP) + "\n" +
            Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
        keyFile.writeText(text)
        return pair
    }

    /** Sign the 20-byte auth token the way adbd verifies it. */
    fun signToken(token: ByteArray): ByteArray {
        val keySize = 256 // 2048-bit key
        val digest = MessageDigest.getInstance("SHA-1").digest(token)
        // DER DigestInfo prefix for SHA-1
        val digestInfo = byteArrayOf(
            0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a,
            0x05, 0x00, 0x04, 0x14
        )
        val t = digestInfo + digest
        val padded = ByteArray(keySize)
        padded[0] = 0x00
        padded[1] = 0x01
        val psLen = keySize - t.size - 3
        for (i in 0 until psLen) padded[2 + i] = 0xFF.toByte()
        padded[2 + psLen] = 0x00
        System.arraycopy(t, 0, padded, 3 + psLen, t.size)

        val cipher = Cipher.getInstance("RSA/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keyPair.private)
        return cipher.doFinal(padded)
    }

    /**
     * ADB public key wire format (see AOSP adb_auth / rsapublickey):
     * little-endian struct { u32 words, u32 n0inv, u8 modulus[256], u8 rr[256], u32 exponent }
     * base64-encoded, followed by " user@host".
     */
    fun publicKeyPayload(): ByteArray {
        val pub = keyPair.public as RSAPublicKey
        val modulus = pub.modulus
        val words = 64 // 2048 / 32
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = modulus.modInverse(r32).negate().mod(r32)
        val r = BigInteger.ONE.shiftLeft(2048)
        val rr = r.multiply(r).mod(modulus)

        val buf = ByteBuffer.allocate(4 + 4 + 256 + 256 + 4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(words)
        buf.putInt(n0inv.toInt())
        buf.put(toLittleEndianFixed(modulus, 256))
        buf.put(toLittleEndianFixed(rr, 256))
        buf.putInt(pub.publicExponent.toInt())

        val b64 = Base64.encodeToString(buf.array(), Base64.NO_WRAP)
        return "$b64 unknown@tvadbremote\u0000".toByteArray(Charsets.UTF_8)
    }

    private fun toLittleEndianFixed(v: BigInteger, size: Int): ByteArray {
        val big = v.toByteArray() // big-endian, may include a sign byte
        val out = ByteArray(size)
        var src = big.size - 1
        var dst = 0
        while (src >= 0 && dst < size) {
            out[dst++] = big[src--]
        }
        return out
    }
}
