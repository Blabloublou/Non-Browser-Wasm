private const val PREFIX: String = "Wasm received: "

internal fun debugLog(message: String) {
    if (!DEBUG_ENABLED) return
    println("[debug] $message")
}

interface LineReader {
    fun readLine(): String?
}

interface LineWriter {
    fun writeLine(value: String)
}

fun runEcho(reader: LineReader, writer: LineWriter) {
    while (true) {
        val line = reader.readLine() ?: return
        writer.writeLine("$PREFIX$line")
    }
}

