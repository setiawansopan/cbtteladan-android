package id.my.cbtteladan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemCheckTest {

    @Test
    fun `format ukuran memakai MB di bawah 1 GB dan GB di atasnya`() {
        assertEquals("512 MB", SystemCheck.formatBytes(512L * 1024 * 1024))
        assertTrue(SystemCheck.formatBytes(8L * 1024 * 1024 * 1024).startsWith("8"))
        assertTrue(SystemCheck.formatBytes(8L * 1024 * 1024 * 1024).endsWith("GB"))
    }

    @Test
    fun `prosesor kurang dari 2 core diberi peringatan`() {
        assertEquals(CheckStatus.WARN, SystemCheck.cpu(1))
        assertEquals(CheckStatus.PASS, SystemCheck.cpu(2))
        assertEquals(CheckStatus.PASS, SystemCheck.cpu(8))
    }

    @Test
    fun `ponsel 2 GB yang melaporkan 1,8 GB tetap lulus, 1 GB tidak`() {
        assertEquals(CheckStatus.PASS, SystemCheck.ram((1.8 * 1024 * 1024 * 1024).toLong()))
        assertEquals(CheckStatus.WARN, SystemCheck.ram(1024L * 1024 * 1024))
    }

    @Test
    fun `penyimpanan kosong di bawah 500 MB diberi peringatan`() {
        assertEquals(CheckStatus.WARN, SystemCheck.storage(300L * 1024 * 1024))
        assertEquals(CheckStatus.PASS, SystemCheck.storage(500L * 1024 * 1024))
    }

    @Test
    fun `baterai rendah hanya masalah bila tidak mengisi daya`() {
        assertEquals(CheckStatus.WARN, SystemCheck.battery(19, charging = false))
        assertEquals(CheckStatus.PASS, SystemCheck.battery(19, charging = true))
        assertEquals(CheckStatus.PASS, SystemCheck.battery(20, charging = false))
        assertEquals(CheckStatus.PASS, SystemCheck.battery(null, charging = false))
    }

    @Test
    fun `WebView lama diberi peringatan dan versi tak dikenal dianggap aman`() {
        assertEquals(CheckStatus.WARN, SystemCheck.webView(79))
        assertEquals(CheckStatus.PASS, SystemCheck.webView(80))
        assertEquals(CheckStatus.PASS, SystemCheck.webView(null))
    }

    @Test
    fun `server tidak terhubung gagal dan server lambat diberi peringatan`() {
        assertEquals(CheckStatus.FAIL, SystemCheck.server(reachable = false, elapsedMs = 100))
        assertEquals(CheckStatus.WARN, SystemCheck.server(reachable = true, elapsedMs = 2500))
        assertEquals(CheckStatus.PASS, SystemCheck.server(reachable = true, elapsedMs = 300))
    }

    @Test
    fun `status terburuk diambil dari kumpulan hasil`() {
        assertEquals(CheckStatus.PASS, SystemCheck.worst(emptyList()))
        assertEquals(CheckStatus.WARN, SystemCheck.worst(listOf(CheckStatus.PASS, CheckStatus.WARN)))
        assertEquals(CheckStatus.FAIL, SystemCheck.worst(listOf(CheckStatus.WARN, CheckStatus.FAIL, CheckStatus.PASS)))
    }
}
