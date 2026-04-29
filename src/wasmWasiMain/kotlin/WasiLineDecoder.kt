internal const val CR: Byte = 0x0D

internal fun List<Byte>.toLine(): String {
    return decodeLineUtf8StrictOrNull()
        ?: error("Invalid UTF-8 in stdin line")
}

internal fun List<Byte>.toLineResult(): WasiLineReadResult =
    decodeLineUtf8StrictOrNull()?.let { line ->
        WasiLineReadResult.Line(line)
    } ?: WasiLineReadResult.Error(WasiDecodeError.InvalidUtf8)

private fun List<Byte>.decodeLineUtf8StrictOrNull(): String? {
    val end = if (isNotEmpty() && last() == CR) size - 1 else size
    val bytes = ByteArray(end)
    for (i in 0 until end) bytes[i] = this[i]
    val decoded = bytes.decodeToString()
    return if (decoded.contains('\uFFFD')) {
        null
    } else {
        decoded
    }
}
