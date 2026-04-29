import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.tasks.Exec

private val COMPILE_TASK = "compileProductionExecutableKotlinWasmWasiOptimize"
private val RUN_FEATURE_FLAGS = "function-references,gc"

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

private fun Exec.configureCommonWasmExec(inputFile: Any) {
    group = "run"
    dependsOn(COMPILE_TASK)
    inputs.file(inputFile)
    standardInput = System.`in`
}

tasks.register("runWasm") {
    group = "run"
    description = "Compiles and runs the Kotlin/Wasm WASI binary (default runtime: Wasmtime)."
    val runtime = (project.findProperty("runtime") as? String)?.lowercase() ?: "wasmtime"
    val target = when (runtime) {
        "node" -> "runWasmNode"
        "wasmtime" -> "runWasmWasmtime"
        "wasmer" -> "runWasmWasmer"
        else -> throw GradleException("Unknown runtime '$runtime'. Use one of: node, wasmtime, wasmer.")
    }
    dependsOn(target)
}

tasks.register<Exec>("runWasmWasmtime") {
    description = "Compiles and runs the Kotlin/Wasm WASI binary via Wasmtime (interactive)."
    configureCommonWasmExec(wasmBinary)
    doFirst {
        val wasmtime = requireResolvedOnPath("wasmtime", "https://wasmtime.dev/")
        commandLine(
            wasmtime.absolutePath,
            "run", "-W", RUN_FEATURE_FLAGS,
            wasmBinary.get().asFile.absolutePath,
        )
    }
    commandLine("true")
}

tasks.register<Exec>("runWasmNode") {
    description = "Compiles and runs the Kotlin/Wasm WASI binary via Node.js (pipe-only)."
    configureCommonWasmExec(wasmEntry)
    doFirst {
        val node = requireResolvedOnPath("node", "https://nodejs.org/")
        commandLine(
            "sh",
            "-c",
            """
            if [ -t 0 ]; then
              echo "Node WASI here is pipe-only (non-interactive stdin). Use: printf 'hello\n' | ./gradlew runWasm -Pruntime=node; for interactive stdin, run ./gradlew runWasm (Wasmtime)." >&2
              exit 2
            fi
            node_bin="${'$'}1"
            entry="${'$'}2"
            shift 2
            exec "${'$'}node_bin" "${'$'}entry" "${'$'}@"
            """.trimIndent(),
            "node-runner",
            node.absolutePath,
            wasmEntry.get().asFile.absolutePath,
        )
    }
    commandLine("true")
}

tasks.register("runWasmWasmer") {
    group = "run"
    description = "Explains why Wasmer cannot currently run Kotlin/Wasm GC binaries."
    dependsOn(COMPILE_TASK)
    doFirst {
        requireResolvedOnPath("wasmer", "https://wasmer.io/install/")
        throw GradleException(
            "Wasmer is installed, but current stable Wasmer releases do not support " +
                "the WebAssembly GC features",
        )
    }
}
