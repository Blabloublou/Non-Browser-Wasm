import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WasiStdinTest {

    @Test
    fun emptyBufferReturnsNull() {
        assertNull(emptyList<Byte>().toLineOrNull())
    }

    @Test
    fun asciiLineDecodesAsIs() {
        assertEquals("hello", "hello".toBytes().toLine())
    }

    @Test
    fun trailingCarriageReturnIsStripped() {
        assertEquals("hello", "hello\r".toBytes().toLine())
    }

    @Test
    fun utf8MultiByteCharactersArePreserved() {
        assertEquals("bonjour à toi", "bonjour à toi".toBytes().toLine())
    }

    @Test
    fun utf8SpecialCharactersAcrossScriptsArePreserved() {
        val input = "Español ñ € — 日本語 こんにちは 😀 Привет"
        assertEquals(input, input.toBytes().toLine())
    }

    private fun String.toBytes(): List<Byte> = encodeToByteArray().toList()
}
