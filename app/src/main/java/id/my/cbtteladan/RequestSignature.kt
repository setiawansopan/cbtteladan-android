package id.my.cbtteladan

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Tanda tangan request aplikasi CBT Teladan (v5+), sama dengan aplikasi
 * desktop dan pemeriksaan server (StudentAccessPolicy::hasValidSignature):
 *
 *   X-CBT-Timestamp: detik sejak epoch
 *   X-CBT-Signature: hex HMAC-SHA256(kunci, "<timestamp>")
 *
 * Method dan path sengaja tidak ikut ditandatangani: request AJAX di WebView
 * membawa header yang sama saat mengikuti redirect, sehingga tanda tangan per
 * path akan selalu gagal setelah login atau mulai ujian.
 */
object RequestSignature {

    /** Tanda tangan untuk [epochSeconds], atau null bila build tanpa kunci. */
    fun sign(secret: String, epochSeconds: Long): String? {
        if (secret.isEmpty()) return null

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(epochSeconds.toString().toByteArray(Charsets.UTF_8))

        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    /** Kunci disimpan di BuildConfig dalam bentuk XOR; ini membukanya kembali. */
    fun unmask(data: ByteArray, mask: ByteArray): String {
        if (data.isEmpty() || mask.isEmpty()) return ""

        val plain = ByteArray(data.size) { i -> (data[i].toInt() xor mask[i % mask.size].toInt()).toByte() }
        return String(plain, Charsets.UTF_8)
    }
}
