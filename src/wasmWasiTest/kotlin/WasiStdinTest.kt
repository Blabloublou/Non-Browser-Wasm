import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WasiStdinTest {

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

    @Test
    fun wasiLineReadResultLineContainsValue() {
        val line = "hello"
        val result = WasiLineReadResult.Line(line)
        assertIs<WasiLineReadResult.Line>(result)
        assertEquals(line, result.value)
    }

    @Test
    fun wasiReadErrorUnexpectedRetainsErrno() {
        val error = WasiReadError.Unexpected(42)
        assertEquals(42, error.errno)
    }

    @Test
    fun wasiReadErrorLineTooLongRetainsLimit() {
        val error = WasiDecodeError.LineTooLong(MAX_LINE_BYTES)
        assertEquals(MAX_LINE_BYTES, error.maxBytes)
    }

    @Test
    fun invalidUtf8MapsToDeterministicReadError() {
        val invalid = byteArrayOf(0xC3.toByte(), 0x28).toList()
        val result = invalid.toLineResult()
        val error = assertIs<WasiLineReadResult.Error>(result)
        assertIs<WasiDecodeError.InvalidUtf8>(error.error)
    }

    private fun String.toBytes(): List<Byte> = encodeToByteArray().toList()
}
