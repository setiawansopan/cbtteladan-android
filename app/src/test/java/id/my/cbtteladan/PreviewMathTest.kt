package id.my.cbtteladan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `ponsel potret dengan kamera depan sensor 270 tampil potret`() {
        assertTrue(PreviewMath.isPortrait(sensorOrientation = 270, displayDegrees = 0, front = true))
    }

    @Test
    fun `ponsel diputar lanskap tampil lanskap`() {
        assertFalse(PreviewMath.isPortrait(270, 90, front = true))
        assertFalse(PreviewMath.isPortrait(270, 270, front = true))
    }

    @Test
    fun `tablet lanskap alami dengan sensor 0 tampil lanskap`() {
        assertFalse(PreviewMath.isPortrait(0, 0, front = true))
        assertTrue(PreviewMath.isPortrait(0, 90, front = true))
    }

    @Test
    fun `potong tengah ke kotak memperbesar sisi pendek`() {
        // Gambar potret 480x640 diregangkan ke kotak 100x100
        val (sx, sy) = PreviewMath.cropScale(480, 640, 100, 100)
        assertEquals(1.0f, sx, 0.001f)
        assertEquals(1.3333f, sy, 0.001f)
    }

    @Test
    fun `bentuk sama tidak diskalakan`() {
        val (sx, sy) = PreviewMath.cropScale(640, 480, 160, 120)
        assertEquals(1.0f, sx, 0.001f)
        assertEquals(1.0f, sy, 0.001f)
    }

    @Test
    fun `ukuran tidak valid dibiarkan`() {
        assertEquals(1f to 1f, PreviewMath.cropScale(0, 480, 100, 100))
        assertEquals(1f to 1f, PreviewMath.cropScale(640, 480, 0, 0))
    }
}
