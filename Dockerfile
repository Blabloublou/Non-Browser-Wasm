FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon --quiet assemble

FROM debian:bookworm-slim AS runtime
WORKDIR /app

ARG WASMTIME_VERSION=v44.0.0

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates curl xz-utils; \
    curl -fsSL "https://wasmtime.dev/install.sh" | bash -s -- --version "${WASMTIME_VERSION}"; \
    install -m 0755 /root/.wasmtime/bin/wasmtime /usr/local/bin/wasmtime; \
    rm -rf /root/.wasmtime; \
    apt-get purge -y --auto-remove curl xz-utils; \
    rm -rf /var/lib/apt/lists/*

COPY --from=build \
    /workspace/build/compileSync/wasmWasi/main/productionExecutable/optimized/non-browser-wasm.wasm \
    ./

ENTRYPOINT ["wasmtime", "run", "-W", "function-references,gc", "non-browser-wasm.wasm"]
