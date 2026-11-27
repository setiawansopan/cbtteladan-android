package id.my.cbtteladan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    @Test
    fun `membaca versi dan mengabaikan akhiran build`() {
        assertEquals(listOf(5, 0, 2), UpdateCheck.parse("5.0.2"))
        assertEquals(listOf(5, 0, 2), UpdateCheck.parse("5.0.2-debug"))
        assertEquals(listOf(5), UpdateCheck.parse(" 5 "))
        assertNull(UpdateCheck.parse("v5"))
        assertNull(UpdateCheck.parse(""))
        assertNull(UpdateCheck.parse(null))
    }

    @Test
    fun `membandingkan angka bukan teks`() {
        assertTrue(UpdateCheck.compare("5.0.10", "5.0.9")!! > 0)
        assertTrue(UpdateCheck.compare("5.1", "5.0.9")!! > 0)
        assertEquals(0, UpdateCheck.compare("5.0", "5.0.0"))
        assertTrue(UpdateCheck.compare("4.9.9", "5.0.0")!! < 0)
        assertNull(UpdateCheck.compare("abc", "5.0.0"))
    }

    @Test
    fun `status tersedia bila server punya versi lebih baru`() {
        assertEquals(UpdateStatus.AVAILABLE, UpdateCheck.status("5.0.2", "5.0.3", "5.0.0"))
    }

    @Test
    fun `status tidak ada bila sudah terbaru atau lebih baru`() {
        assertEquals(UpdateStatus.NONE, UpdateCheck.status("5.0.3", "5.0.3", "5.0.0"))
        assertEquals(UpdateStatus.NONE, UpdateCheck.status("5.0.4", "5.0.3", "5.0.0"))
    }

    @Test
    fun `status wajib bila di bawah versi minimum`() {
        assertEquals(UpdateStatus.REQUIRED, UpdateCheck.status("4.0.1", "5.0.3", "5.0.0"))
    }

    @Test
    fun `info kosong atau rusak tidak memunculkan pemberitahuan`() {
        assertEquals(UpdateStatus.NONE, UpdateCheck.status("5.0.2", null, null))
        assertEquals(UpdateStatus.NONE, UpdateCheck.status("5.0.2", "rusak", "rusak"))
    }

    @Test
    fun `hanya link https yang aman`() {
        assertTrue(UpdateCheck.isSafeUrl("https://github.com/x/y/releases/latest"))
        assertFalse(UpdateCheck.isSafeUrl("http://example.test/a.apk"))
        assertFalse(UpdateCheck.isSafeUrl("javascript:alert(1)"))
        assertFalse(UpdateCheck.isSafeUrl("https://a.test/ b"))
        assertFalse(UpdateCheck.isSafeUrl(null))
    }
}
