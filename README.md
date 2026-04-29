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

## Test locally

### 1) Quick build

```bash
./gradlew assemble
```

### 2) Default run (Wasmtime, interactive)

```bash
./gradlew runWasm
```

Then type lines in your terminal.

### 3) Test with piped stdin (UTF-8)

```bash
printf "hello\naççents\nこんにちは\n" | ./gradlew runWasm
```

### 4) Node runtime

```bash
printf "via-node\nline2\n" | ./gradlew runWasm -Pruntime=node
```

Note: this mode is mainly intended for piped input, not reliable interactive TTY input.

### 5) Wasmer runtime (expected behavior: explicit failure)

```bash
./gradlew runWasm -Pruntime=wasmer
```

The build fails on purpose with a clear message about the current Wasm GC limitation.

### 6) Invalid runtime (validation)

```bash
./gradlew runWasm -Pruntime=foo
```

Expected error: `Unknown runtime 'foo'. Use one of: node, wasmtime, wasmer.`

## Tests

Run WASI Node tests:

```bash
./gradlew wasmWasiNodeTest
```

If nothing appears, the task is often `UP-TO-DATE`.

Force real execution:

```bash
./gradlew wasmWasiNodeTest --rerun-tasks
```

Show more logs:

```bash
./gradlew wasmWasiNodeTest --rerun-tasks --info
```

## Check the generated binary

```bash
ls -lh build/compileSync/wasmWasi/main/productionExecutable/optimized/non-browser-wasm.wasm
file build/compileSync/wasmWasi/main/productionExecutable/optimized/non-browser-wasm.wasm
```

## Test with Docker

### 1) Build image

```bash
docker build -t non-browser-wasm .
```

### 2) Interactive run (TTY)

```bash
docker run --rm -it non-browser-wasm
```

### 3) Non-interactive run (pipe)

```bash
printf "hello\ndocker\n" | docker run --rm -i non-browser-wasm
```

## Useful command recap

```bash
./gradlew runWasm
printf "hello\n" | ./gradlew runWasm
printf "hello\n" | ./gradlew runWasm -Pruntime=node
./gradlew runWasm -Pruntime=wasmer
./gradlew wasmWasiNodeTest --rerun-tasks --info
docker build -t non-browser-wasm .
docker run --rm -it non-browser-wasm
```
