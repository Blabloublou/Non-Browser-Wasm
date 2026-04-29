import java.io.File
import java.security.MessageDigest

private val wasmBinary = layout.buildDirectory.file(
    "compileSync/wasmWasi/main/productionExecutable/optimized/non-browser-wasm.wasm",
)

private val wasmEntry = layout.buildDirectory.file(
    "compileSync/wasmWasi/main/productionExecutable/optimized/non-browser-wasm.mjs",
)

tasks.register("packageWasmRelease") {
    group = "distribution"
    description = "Packages release .wasm/.mjs artifacts with SHA-256 checksums."
    dependsOn("compileProductionExecutableKotlinWasmWasiOptimize")
    val outputDir = layout.buildDirectory.dir("distributions")
    inputs.file(wasmBinary)
    inputs.file(wasmEntry)
    outputs.dir(outputDir)
    doLast {
        val destination = outputDir.get().asFile
        destination.mkdirs()

        val wasmSource = wasmBinary.get().asFile
        val mjsSource = wasmEntry.get().asFile
        val wasmOut = destination.resolve(wasmSource.name)
        val mjsOut = destination.resolve(mjsSource.name)
        wasmSource.copyTo(wasmOut, overwrite = true)
        mjsSource.copyTo(mjsOut, overwrite = true)

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }

        destination.resolve("SHA256SUMS.txt").writeText(
            buildString {
                appendLine("${sha256(wasmOut)}  ${wasmOut.name}")
                appendLine("${sha256(mjsOut)}  ${mjsOut.name}")
            },
        )
    }
}
