package id.my.cbtteladan

/** Hasil membandingkan versi aplikasi dengan info dari server. */
enum class UpdateStatus {
    /** Sudah versi terbaru, atau info dari server tidak tersedia. */
    NONE,

    /** Ada versi yang lebih baru; siswa boleh tetap ujian. */
    AVAILABLE,

    /** Di bawah versi minimum server: login akan ditolak sampai diperbarui. */
    REQUIRED,
}

/**
 * Perbandingan versi untuk pemberitahuan pembaruan (murni, diuji di JVM).
 * Info diambil dari server di /app-update; aplikasi hanya memberi tahu dan
 * tidak memasang pembaruan sendiri.
 */
object UpdateCheck {

    /** "5.0.2" menjadi [5, 0, 2]; akhiran build seperti "-debug" diabaikan. Tidak valid menjadi null. */
    fun parse(version: String?): List<Int>? {
        val clean = version?.trim()?.substringBefore('-').orEmpty()
        if (!Regex("""\d+(\.\d+){0,3}""").matches(clean)) return null
        return clean.split('.').map { it.toInt() }
    }

    /** Positif bila [a] lebih baru dari [b]; null bila salah satunya tidak valid. */
    fun compare(a: String?, b: String?): Int? {
        val left = parse(a) ?: return null
        val right = parse(b) ?: return null
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    fun status(current: String, latest: String?, minimum: String?): UpdateStatus {
        val belowMinimum = compare(current, minimum)?.let { it < 0 } ?: false
        if (belowMinimum) return UpdateStatus.REQUIRED
        val newer = compare(latest, current)?.let { it > 0 } ?: false
        return if (newer) UpdateStatus.AVAILABLE else UpdateStatus.NONE
    }

    /** Link unduhan hanya dibuka bila https, agar link aneh dari server tidak ikut terbuka. */
    fun isSafeUrl(url: String?): Boolean =
        url != null && url.startsWith("https://") && url.length <= 500 && !url.any { it.isWhitespace() }
}
