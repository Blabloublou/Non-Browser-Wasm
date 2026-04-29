private const val PREFIX: String = "Wasm received: "

fun main() {
    while (true) {
        val line = wasiReadLine() ?: return
        println("$PREFIX$line")
    }
}
