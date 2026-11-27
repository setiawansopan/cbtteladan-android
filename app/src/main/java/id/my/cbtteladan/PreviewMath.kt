package id.my.cbtteladan

import kotlin.math.abs
import kotlin.math.max

/**
 * Hitungan murni untuk preview kamera depan (tanpa API Android) agar bisa diuji di JVM.
 */
object PreviewMath {

    /** Ukuran preview yang diinginkan: kecil dan ringan, cukup untuk cermin diri. */
    private const val TARGET_WIDTH = 640
    private const val TARGET_HEIGHT = 480

    /**
     * Pilih ukuran preview (lebar, tinggi dalam bingkai sensor) dari daftar yang
     * didukung kamera: utamakan rasio 4:3 dengan luas paling dekat 640x480.
     */
    fun chooseSize(sizes: List<Pair<Int, Int>>): Pair<Int, Int>? {
        if (sizes.isEmpty()) return null
        val targetArea = TARGET_WIDTH * TARGET_HEIGHT
        val fourThree = sizes.filter { (w, h) -> h > 0 && abs(w.toFloat() / h - 4f / 3f) < 0.02f }
        val pool = fourThree.ifEmpty { sizes }
        return pool.minByOrNull { (w, h) -> abs(w * h - targetArea) }
    }

    /**
     * Ukuran gambar tegak dalam orientasi ALAMI perangkat. Camera2 sudah memutar
     * gambar sesuai SENSOR_ORIENTATION agar tegak pada orientasi alami (sensor
     * 90/270 berarti potret); rotasi layar saat ini tidak ikut dihitung.
     */
    fun uprightSize(sensorOrientation: Int, frameWidth: Int, frameHeight: Int): Pair<Int, Int> =
        if (sensorOrientation % 180 == 90) frameHeight to frameWidth else frameWidth to frameHeight

    /** Hasil perhitungan matriks: skala awal (x, y), putaran searah jarum jam, lalu skala seragam. */
    data class Fit(val preX: Float, val preY: Float, val degrees: Float, val scale: Float)

    /**
     * Matriks agar gambar tegak, tidak gepeng, dan memenuhi kotak (center crop).
     *
     * TextureView meregangkan gambar tegak (orientasi alami) ke seluruh view.
     * Urutan: kembalikan ke ukuran asli (preX, preY), putar mengimbangi rotasi
     * layar (tablet yang dipakai lanskap, rotasi 1 atau 3), lalu skala seragam
     * sampai kotak penuh. [rotationIndex] = Surface.ROTATION_* (0-3);
     * [extraTurns] = putaran tambahan 90 derajat pilihan siswa (ketuk kotak).
     */
    fun fit(uprightWidth: Int, uprightHeight: Int, viewWidth: Int, viewHeight: Int, rotationIndex: Int, extraTurns: Int = 0): Fit {
        if (uprightWidth <= 0 || uprightHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return Fit(1f, 1f, 0f, 1f)
        val turns = Math.floorMod(rotationIndex + extraTurns, 4)
        val sideways = turns % 2 == 1
        val shownWidth = if (sideways) uprightHeight else uprightWidth
        val shownHeight = if (sideways) uprightWidth else uprightHeight
        val uniform = max(viewWidth.toFloat() / shownWidth, viewHeight.toFloat() / shownHeight)
        return Fit(
            preX = uprightWidth.toFloat() / viewWidth,
            preY = uprightHeight.toFloat() / viewHeight,
            degrees = compensationDegrees(turns),
            scale = uniform,
        )
    }

    /** Putaran searah jarum jam untuk [turns] kali 90 derajat rotasi layar (0, -90, 180, 90). */
    fun compensationDegrees(turns: Int): Float = when (Math.floorMod(turns, 4)) {
        1 -> -90f
        2 -> 180f
        3 -> 90f
        else -> 0f
    }
}
