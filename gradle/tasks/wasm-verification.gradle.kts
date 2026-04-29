import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project

val maxLineBytes = (providers.gradleProperty("maxLineBytes").orNull?.toIntOrNull() ?: 1_048_576)

private val wasmBinary = layout.buildDirectory.file(
    "compileSync/wasmWasi/main/productionExecutable/optimized/non-browser-wasm.wasm",
)

private val wasmEntry = layout.buildDirectory.file(
    "compileSync/wasmWasi/main/productionExecutable/optimized/non-browser-wasm.mjs",
)

private fun resolveOnPath(executable: String): File? =
    System.getenv("PATH")
        ?.split(File.pathSeparatorChar)
        ?.asSequence()
        ?.map { dir -> File(dir, executable) }
        ?.firstOrNull { it.canExecute() }

private fun requireResolvedOnPath(executable: String, installUrl: String): File =
    resolveOnPath(executable) ?: throw GradleException(
        "'$executable' is not on PATH. Install it from $installUrl and try again.",
    )

private data class CapturedExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

private fun Project.execCaptured(
    command: List<String>,
    stdinUtf8: String,
    ignoreExitValue: Boolean = false,
): CapturedExecResult {
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    val result = exec {
        commandLine(command)
        standardInput = ByteArrayInputStream(stdinUtf8.toByteArray(Charsets.UTF_8))
        standardOutput = stdout
        errorOutput = stderr
        isIgnoreExitValue = ignoreExitValue
    }
    return CapturedExecResult(
        exitCode = result.exitValue,
        stdout = stdout.toString(Charsets.UTF_8),
        stderr = stderr.toString(Charsets.UTF_8),
    )
}

private fun wasmtimeRunCommand(wasmtime: File, wasmPath: String): List<String> =
    listOf(
        wasmtime.absolutePath,
        "run", "-W", "function-references,gc",
        wasmPath,
    )

tasks.register("wasmWasiE2eTest") {
    group = "verification"
    description = "Runs end-to-end WASI stdin/stdout checks against the compiled Wasm binary."
    dependsOn("compileProductionExecutableKotlinWasmWasiOptimize")
    inputs.file(wasmBinary)
    doLast {
        val wasmtime = requireResolvedOnPath("wasmtime", "https://wasmtime.dev/")
        val wasmPath = wasmBinary.get().asFile.absolutePath
        val wasmtimeCommand = wasmtimeRunCommand(wasmtime, wasmPath)

        fun assertRun(input: String, expectedStdout: String) {
            val result = project.execCaptured(
                command = wasmtimeCommand,
                stdinUtf8 = input,
            )
            if (result.stdout != expectedStdout) {
                throw GradleException(
                    "Unexpected stdout for input=${input.replace("\n", "\\n")}.\n" +
                        "Expected: $expectedStdout\n" +
                        "Actual:   ${result.stdout}\n" +
                        "Stderr:   ${result.stderr}",
                )
            }
        }

        fun assertRunWithStderr(input: String, expectedStdout: String, expectedStderrFragment: String) {
            val result = project.execCaptured(
                command = wasmtimeCommand,
                stdinUtf8 = input,
            )
            if (result.exitCode != 0) {
                throw GradleException(
                    "Expected successful run.\nExit: ${result.exitCode}\nStdout: ${result.stdout}\nStderr: ${result.stderr}",
                )
            }
            if (result.stdout != expectedStdout) {
                throw GradleException(
                    "Unexpected stdout.\nExpected: $expectedStdout\nActual:   ${result.stdout}",
                )
            }
            if (!result.stderr.contains(expectedStderrFragment)) {
                throw GradleException(
                    "Unexpected stderr.\nExpected fragment: $expectedStderrFragment\nActual stderr: ${result.stderr}",
                )
            }
        }

        assertRun(
            input = "hello\nworld\n",
            expectedStdout = "Wasm received: hello\nWasm received: world\n",
        )
        assertRun(
            input = "tail-without-newline",
            expectedStdout = "Wasm received: tail-without-newline\n",
        )
        val oversizedLine = "x".repeat(maxLineBytes + 1)
        assertRunWithStderr(
            input = oversizedLine,
            expectedStdout = "",
            expectedStderrFragment = "LineTooLong(maxBytes=$maxLineBytes)",
        )
    }
}

tasks.register("wasmWasiNodePipeE2eTest") {
    group = "verification"
    description = "Runs end-to-end WASI stdin/stdout checks via Node in pipe mode."
    dependsOn("compileProductionExecutableKotlinWasmWasiOptimize")
    inputs.file(wasmEntry)
    doLast {
        val node = requireResolvedOnPath("node", "https://nodejs.org/")
        val entryPath = wasmEntry.get().asFile.absolutePath
        val result = project.execCaptured(
            command = listOf(node.absolutePath, entryPath),
            stdinUtf8 = "node-line\n",
        )
        val expected = "Wasm received: node-line\n"
        if (result.stdout != expected) {
            throw GradleException(
                "Unexpected Node stdout.\nExpected: $expected\nActual: ${result.stdout}\nStderr: ${result.stderr}",
            )
        }
    }
}
