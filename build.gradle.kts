@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import java.io.File
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)

    wasmWasi {
        nodejs()
        binaries.executable()
    }

    sourceSets {
        wasmWasiTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xwasm-use-traps-instead-of-exceptions")
}

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
    group = "run"
    description = "Compiles and runs the Kotlin/Wasm WASI binary via Wasmtime (interactive)."
    dependsOn("compileProductionExecutableKotlinWasmWasiOptimize")
    inputs.file(wasmBinary)
    standardInput = System.`in`
    doFirst {
        val wasmtime = requireResolvedOnPath("wasmtime", "https://wasmtime.dev/")
        commandLine(
            wasmtime.absolutePath,
            "run", "-W", "function-references,gc",
            wasmBinary.get().asFile.absolutePath,
        )
    }
    commandLine("true")
}

tasks.register<Exec>("runWasmNode") {
    group = "run"
    description = "Compiles and runs the Kotlin/Wasm WASI binary via Node.js (pipe-only)."
    dependsOn("compileProductionExecutableKotlinWasmWasiOptimize")
    inputs.file(wasmEntry)
    standardInput = System.`in`
    doFirst {
        val node = requireResolvedOnPath("node", "https://nodejs.org/")
        commandLine(
            "sh",
            "-c",
            """
            if [ -t 0 ]; then
              echo "Node.js WASI mode is pipe-only here: it does not reliably block on interactive TTY stdin." >&2
              echo "Use: printf 'hello\n' | ./gradlew runWasm -Pruntime=node" >&2
              echo "For interactive stdin, use the default Wasmtime mode: ./gradlew runWasm" >&2
              exit 2
            fi
            exec "$1" "$2"
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
    dependsOn("compileProductionExecutableKotlinWasmWasiOptimize")
    doFirst {
        requireResolvedOnPath("wasmer", "https://wasmer.io/install/")
        throw GradleException(
            "Wasmer is installed, but current stable Wasmer releases do not support " +
                "the WebAssembly GC features emitted by Kotlin/Wasm. " +
                "Your installed Wasmer reports this as: " +
                "'array indexed types not supported without the gc feature' / " +
                "'No backends support the required features for the Wasm module'. " +
                "Use -Pruntime=wasmtime for interactive execution, or retry Wasmer " +
                "when upstream WasmGC support ships.",
        )
    }
}
