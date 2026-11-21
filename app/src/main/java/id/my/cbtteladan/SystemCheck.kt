package id.my.cbtteladan

import java.util.Locale

/** Hasil satu pemeriksaan di halaman pengecekan perangkat. */
enum class CheckStatus { PASS, WARN, FAIL }

/**
 * Aturan penilaian halaman pengecekan perangkat. Semua pemeriksaan hanya
 * memberi peringatan dan tidak menghalangi siswa memulai ujian (pengawas yang
 * memutuskan), kecuali jaringan/server yang memang wajib terhubung.
 */
object SystemCheck {

    const val MIN_CPU_CORES = 2

    /** Ponsel berlabel 2 GB melaporkan sekitar 1,6-1,9 GB, jadi batasnya sedikit di bawah 2 GB. */
    const val MIN_RAM_BYTES = 1_500L * 1024 * 1024

    const val MIN_FREE_BYTES = 500L * 1024 * 1024

    const val LOW_BATTERY_PERCENT = 20

    const val MIN_WEBVIEW_MAJOR = 80

    /** Respons server lebih lambat dari ini dianggap lambat. */
    const val SLOW_SERVER_MS = 2000

    private val indonesia = Locale("id", "ID")

    /** "7,6 GB" atau "512 MB". */
    fun formatBytes(bytes: Long): String {
        val gigabytes = bytes / (1024.0 * 1024 * 1024)
        return if (gigabytes >= 1) {
            String.format(indonesia, "%.1f GB", gigabytes)
        } else {
            String.format(indonesia, "%d MB", bytes / (1024 * 1024))
        }
    }

    fun cpu(cores: Int) = if (cores >= MIN_CPU_CORES) CheckStatus.PASS else CheckStatus.WARN

    fun ram(totalBytes: Long) = if (totalBytes >= MIN_RAM_BYTES) CheckStatus.PASS else CheckStatus.WARN

    fun storage(freeBytes: Long) = if (freeBytes >= MIN_FREE_BYTES) CheckStatus.PASS else CheckStatus.WARN

    /** Baterai rendah hanya masalah bila tidak sedang mengisi daya; level tak diketahui dianggap aman. */
    fun battery(percent: Int?, charging: Boolean) =
        if (percent == null || charging || percent >= LOW_BATTERY_PERCENT) CheckStatus.PASS else CheckStatus.WARN

    /** Versi WebView tak diketahui dianggap aman. */
    fun webView(majorVersion: Int?) =
        if (majorVersion == null || majorVersion >= MIN_WEBVIEW_MAJOR) CheckStatus.PASS else CheckStatus.WARN

    fun server(reachable: Boolean, elapsedMs: Int) = when {
        !reachable -> CheckStatus.FAIL
        elapsedMs > SLOW_SERVER_MS -> CheckStatus.WARN
        else -> CheckStatus.PASS
    }

    /** Status terburuk dari kumpulan hasil; kosong dianggap lulus. */
    fun worst(statuses: Collection<CheckStatus>): CheckStatus = statuses.maxOrNull() ?: CheckStatus.PASS
}
