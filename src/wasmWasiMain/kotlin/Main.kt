/**
 * WASI-backed implementation of LineReader that delegates to wasiReadLine.
 */
object WasiStdinLineReader : LineReader {
    override fun readLine(): String? {
        while (true) {
            when (val result = wasiReadLine()) {
                is WasiLineReadResult.Line -> return result.value.also {
                    debugLog("read line (${it.length} chars)")
                }
                WasiLineReadResult.Eof -> return null
                is WasiLineReadResult.Error -> {
                    debugLog("stdin error: $result")
                    writeStderrLine("WASI stdin read failed: $result")
                    when (result.error) {
                        is WasiDecodeError -> continue
                        is WasiReadError -> return null
                    }
                }
            }
        }
    }
}

/**
 * LineWriter prints to the process standard output.
 */
object StdoutLineWriter : LineWriter {
    override fun writeLine(value: String) {
        println(value)
    }
}

fun main() {
    val reader: LineReader = WasiStdinLineReader
    val writer: LineWriter = StdoutLineWriter
    runEcho(reader, writer)
}
