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
     * Apakah gambar kamera yang tampil tegak berbentuk potret (tinggi > lebar)?
     * Bingkai sensor selalu lanskap; arah tampilan ditentukan orientasi sensor
     * dan rotasi layar (dalam derajat, searah jarum jam).
     */
    fun isPortrait(sensorOrientation: Int, displayDegrees: Int, front: Boolean): Boolean {
        val relative = if (front) {
            (sensorOrientation + displayDegrees) % 360
        } else {
            (sensorOrientation - displayDegrees + 360) % 360
        }
        return relative % 180 == 90
    }

    /**
     * TextureView meregangkan gambar mengikuti bentuk view-nya. Skala tambahan
     * (x, y) di sekitar titik tengah mengembalikan perbandingan asli gambar dan
     * memotong kelebihannya (center crop), sehingga wajah tidak tampak gepeng.
     */
    fun cropScale(contentWidth: Int, contentHeight: Int, viewWidth: Int, viewHeight: Int): Pair<Float, Float> {
        if (contentWidth <= 0 || contentHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return 1f to 1f
        val uniform = max(viewWidth.toFloat() / contentWidth, viewHeight.toFloat() / contentHeight)
        return uniform * contentWidth / viewWidth to uniform * contentHeight / viewHeight
    }
}
