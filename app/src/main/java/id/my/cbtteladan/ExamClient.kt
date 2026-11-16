package id.my.cbtteladan

import android.net.Uri
import android.util.Base64

/**
 * Identitas aplikasi yang dikirim ke server ujian. Server mengenali aplikasi
 * dari User-Agent "CBTTeladan/<versi>" dan "cbt-teladan/<versi>", header
 * X-CBT-Client/X-CBT-Version, dan tanda tangan X-CBT-Timestamp/X-CBT-Signature.
 */
object ExamClient {

    const val CLIENT_ID = "CBTTeladan"

    /** Versi tanpa akhiran build (mis. "-debug"), sama dengan versi rilis. */
    val version: String = BuildConfig.VERSION_NAME.substringBefore('-')

    val examUrl: String = BuildConfig.EXAM_URL

    private val examUri: Uri = Uri.parse(examUrl)

    /** Origin halaman ujian, mis. "https://cbtteladan.com". */
    val examOrigin: String = buildString {
        append(examUri.scheme).append("://").append(examUri.host)
        if (examUri.port != -1) append(':').append(examUri.port)
    }

    private val secret: String by lazy {
        RequestSignature.unmask(
            Base64.decode(BuildConfig.SECRET_DATA, Base64.NO_WRAP),
            Base64.decode(BuildConfig.SECRET_MASK, Base64.NO_WRAP),
        )
    }

    /** Hanya server ujian (skema, host, dan port sama) yang boleh dibuka. */
    fun isExamUrl(uri: Uri?): Boolean =
        uri != null &&
            uri.scheme.equals(examUri.scheme, ignoreCase = true) &&
            uri.host.equals(examUri.host, ignoreCase = true) &&
            uri.port == examUri.port

    fun userAgent(base: String): String = "$base $CLIENT_ID/$version cbt-teladan/$version"

    /** Header untuk setiap request ke server ujian, dengan tanda tangan baru. */
    fun headers(nowMillis: Long = System.currentTimeMillis()): Map<String, String> {
        val headers = linkedMapOf(
            "X-CBT-Client" to CLIENT_ID,
            "X-CBT-Version" to version,
        )
        val timestamp = nowMillis / 1000
        RequestSignature.sign(secret, timestamp)?.let { signature ->
            headers["X-CBT-Timestamp"] = timestamp.toString()
            headers["X-CBT-Signature"] = signature
        }
        return headers
    }
}
