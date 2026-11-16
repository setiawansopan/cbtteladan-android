package id.my.cbtteladan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RequestSignatureTest {

    @Test
    fun `cocok dengan vektor uji server dan aplikasi desktop`() {
        assertEquals(
            "9c1344be7afacfec0610dc3fb68daa5e7917129e124f27a2743132f0088caa57",
            RequestSignature.sign("rahasia-uji", 1763290000L),
        )
    }

    @Test
    fun `build tanpa kunci tidak menandatangani`() {
        assertNull(RequestSignature.sign("", 1763290000L))
    }

    @Test
    fun `unmask membuka kunci yang disamarkan XOR`() {
        val mask = byteArrayOf(0x5a, -0x11, 0x33)
        val plain = "kunci-rahasia".toByteArray(Charsets.UTF_8)
        val masked = ByteArray(plain.size) { i -> (plain[i].toInt() xor mask[i % mask.size].toInt()).toByte() }

        assertEquals("kunci-rahasia", RequestSignature.unmask(masked, mask))
        assertEquals("", RequestSignature.unmask(ByteArray(0), ByteArray(0)))
    }
}
