sealed class WasiLineReadResult {

    data class Line(val value: String) : WasiLineReadResult()

    
    data object Eof : WasiLineReadResult()

    data class Error(val error: WasiLineError) : WasiLineReadResult()
}

sealed interface WasiLineError

sealed class WasiReadError : WasiLineError {
    data object BadFileDescriptor : WasiReadError()

    data object Interrupted : WasiReadError()

    data class Unexpected(val errno: Int) : WasiReadError()
}

sealed class WasiDecodeError : WasiLineError {
    data class LineTooLong(val maxBytes: Int) : WasiDecodeError()

    data object InvalidUtf8 : WasiDecodeError()
}
