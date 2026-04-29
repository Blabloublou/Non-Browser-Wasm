# non-browser-wasm

Kotlin/Wasm WASI project that reads from `stdin` and prints each line:

- input: `hello`
- output: `Wasm received: hello`

## Prerequisites

- JDK 17+
- executable `./gradlew`
- one or more runtimes:
  - `wasmtime` (recommended, interactive)
  - `node` (good for piped input, limited interactive behavior)
  - `wasmer` (can be installed, but does not currently support the Wasm GC features used by this binary)

## Quick start

```bash
# 1) Build project artifacts
./gradlew assemble

# 2) Run with default runtime (Wasmtime, interactive stdin)
./gradlew runWasm

# 3) Run with Node runtime (pipe mode)
printf "hello\n" | ./gradlew runWasm -Pruntime=node
```

Use `-Pdebug=true` to enable debug logs and `-PmaxLineBytes=262144` to override the default 1 MiB line limit.

## Runtimes

- **Wasmtime**: default and recommended runtime (`./gradlew runWasm`), works for interactive stdin.
- **Node**: pipe-oriented mode (`printf "hello\n" | ./gradlew runWasm -Pruntime=node`), interactive TTY input is not supported in this setup.
- **Wasmer**: currently expected to fail fast (`./gradlew runWasm -Pruntime=wasmer`) because stable Wasmer does not yet support the required Wasm GC features.

## Tests

```bash
./gradlew wasmWasiNodeTest --rerun-tasks
./gradlew wasmWasiE2eTest
./gradlew wasmWasiNodePipeE2eTest
```

## CI

GitHub Actions workflow: `.github/workflows/ci.yml`

It runs on push/PR with separate jobs for build, Node tests, Wasmtime e2e checks, and release packaging.

## Release artifacts

Create distributable artifacts and checksums:

```bash
./gradlew packageWasmRelease
```

Generated files in `build/distributions`:

- `non-browser-wasm.wasm`
- `non-browser-wasm.mjs`
- `SHA256SUMS.txt`
