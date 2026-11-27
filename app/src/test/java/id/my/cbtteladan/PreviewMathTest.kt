package id.my.cbtteladan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewMathTest {

    @Test
    fun `memilih 4 banding 3 terdekat dengan 640x480`() {
        val sizes = listOf(1920 to 1080, 1280 to 720, 1024 to 768, 640 to 480, 320 to 240, 176 to 144)
        assertEquals(640 to 480, PreviewMath.chooseSize(sizes))
    }

    @Test
    fun `tanpa 4 banding 3 memilih luas terdekat`() {
        assertEquals(1280 to 720, PreviewMath.chooseSize(listOf(1920 to 1080, 1280 to 720)))
    }

    @Test
    fun `daftar kosong tidak menghasilkan ukuran`() {
        assertNull(PreviewMath.chooseSize(emptyList()))
    }

    @Test
    fun `sensor 90 atau 270 menghasilkan gambar potret pada orientasi alami`() {
        assertEquals(480 to 640, PreviewMath.uprightSize(270, 640, 480))
        assertEquals(480 to 640, PreviewMath.uprightSize(90, 640, 480))
    }

    @Test
    fun `sensor 0 atau 180 menghasilkan gambar lanskap`() {
        assertEquals(640 to 480, PreviewMath.uprightSize(0, 640, 480))
        assertEquals(640 to 480, PreviewMath.uprightSize(180, 640, 480))
    }

    @Test
    fun `putaran mengimbangi rotasi layar`() {
        assertEquals(0f, PreviewMath.compensationDegrees(0), 0f)
        assertEquals(-90f, PreviewMath.compensationDegrees(1), 0f)
        assertEquals(180f, PreviewMath.compensationDegrees(2), 0f)
        assertEquals(90f, PreviewMath.compensationDegrees(3), 0f)
        assertEquals(90f, PreviewMath.compensationDegrees(-1), 0f)
    }

    @Test
    fun `ponsel potret ke kotak persegi memotong sisi panjang tanpa memutar`() {
        // Gambar tegak 480x640 diregangkan ke kotak 100x100
        val fit = PreviewMath.fit(480, 640, 100, 100, rotationIndex = 0)
        assertEquals(0f, fit.degrees, 0f)
        assertEquals(4.8f, fit.preX, 0.001f)
        assertEquals(6.4f, fit.preY, 0.001f)
        // Lebar gambar akhir = 480 * skala = tepat 100
        assertEquals(100f, 480 * fit.scale, 0.01f)
    }

    @Test
    fun `tablet potret alami dipakai lanskap diputar 90 derajat dan dipotong`() {
        // Gambar tegak 480x640; setelah diputar tampil 640x480 di kotak 128x128
        val fit = PreviewMath.fit(480, 640, 128, 128, rotationIndex = 1)
        assertEquals(-90f, fit.degrees, 0f)
        // Sisi pendek (480) harus memenuhi kotak: 480 * skala = 128
        assertEquals(128f, 480 * fit.scale, 0.01f)
    }

    @Test
    fun `rotasi layar 3 memutar ke arah sebaliknya`() {
        assertEquals(90f, PreviewMath.fit(480, 640, 128, 128, rotationIndex = 3).degrees, 0f)
    }

    @Test
    fun `putaran tambahan siswa digabung dengan rotasi layar`() {
        assertEquals(180f, PreviewMath.fit(480, 640, 128, 128, rotationIndex = 1, extraTurns = 1).degrees, 0f)
        assertEquals(0f, PreviewMath.fit(480, 640, 128, 128, rotationIndex = 1, extraTurns = 3).degrees, 0f)
    }

    @Test
    fun `ukuran tidak valid dibiarkan`() {
        assertEquals(PreviewMath.Fit(1f, 1f, 0f, 1f), PreviewMath.fit(0, 480, 100, 100, 0))
        assertEquals(PreviewMath.Fit(1f, 1f, 0f, 1f), PreviewMath.fit(640, 480, 0, 0, 0))
    }
}
