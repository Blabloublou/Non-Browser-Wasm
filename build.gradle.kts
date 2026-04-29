@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

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
        val wasmWasiMain by getting
        val wasmWasiTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}