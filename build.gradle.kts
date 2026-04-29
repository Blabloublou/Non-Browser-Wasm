@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

val debugEnabled = (providers.gradleProperty("debug").orNull == "true")
val maxLineBytes = (providers.gradleProperty("maxLineBytes").orNull?.toIntOrNull() ?: 1_048_576)
val generatedFlagsDir = layout.buildDirectory.dir("generated/buildFlags/wasmWasiMain/kotlin")

val generateBuildFlags by tasks.registering {
    inputs.property("debugEnabled", debugEnabled)
    inputs.property("maxLineBytes", maxLineBytes)
    outputs.dir(generatedFlagsDir)
    doLast {
        require(maxLineBytes > 0) { "maxLineBytes must be > 0, got: $maxLineBytes" }
        val outputDir = generatedFlagsDir.get().asFile
        outputDir.mkdirs()
        outputDir.resolve("BuildFlags.kt").writeText(
            """
            internal const val DEBUG_ENABLED: Boolean = $debugEnabled
            internal const val MAX_LINE_BYTES: Int = $maxLineBytes
            """.trimIndent() + "\n",
        )
    }
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
        wasmWasiMain {
            kotlin.srcDir(generatedFlagsDir)
        }
        wasmWasiTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateBuildFlags)
    compilerOptions.freeCompilerArgs.add("-Xwasm-use-traps-instead-of-exceptions")
}

apply(from = "gradle/tasks/wasm-run.gradle.kts")
apply(from = "gradle/tasks/wasm-verification.gradle.kts")
apply(from = "gradle/tasks/wasm-release.gradle.kts")
