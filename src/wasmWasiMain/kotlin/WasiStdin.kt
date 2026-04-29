import kotlin.wasm.ExperimentalWasmInterop
import kotlin.wasm.WasmImport
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

private const val STDIN_FD: Int = 0
private const val STDERR_FD: Int = 2
internal const val LF: Byte = 0x0A

@OptIn(ExperimentalWasmInterop::class, UnsafeWasmMemoryApi::class)
@WasmImport("wasi_snapshot_preview1", "fd_read")
private external fun wasiRawFdRead(fd: Int, iovs: Int, iovsLen: Int, nread: Int): Int

@OptIn(ExperimentalWasmInterop::class, UnsafeWasmMemoryApi::class)
@WasmImport("wasi_snapshot_preview1", "fd_write")
private external fun wasiRawFdWrite(fd: Int, iovs: Int, iovsLen: Int, nwritten: Int): Int

private fun classifyErrno(errno: Int): WasiReadError =
    when (errno) {
        8 -> WasiReadError.BadFileDescriptor
        27 -> WasiReadError.Interrupted
        else -> WasiReadError.Unexpected(errno)
    }

/**
 * Reads one UTF-8 encoded line from stdin via WASI `fd_read`.
 *
 * @return the decoded line, or `null` on EOF.
 */
@OptIn(ExperimentalWasmInterop::class, UnsafeWasmMemoryApi::class)
fun wasiReadLine(): WasiLineReadResult = withScopedMemoryAllocator { allocator ->
    val byteBuffer = allocator.allocate(1)
    val iovec = allocator.allocate(8).also {
        (it + 0).storeInt(byteBuffer.address.toInt())
        (it + 4).storeInt(1)
    }
    val nreadPtr = allocator.allocate(4)

    val collected = ArrayList<Byte>(64)
    var droppingOversizedLine = false
    while (true) {
        val errno = wasiRawFdRead(
            fd = STDIN_FD,
            iovs = iovec.address.toInt(),
            iovsLen = 1,
            nread = nreadPtr.address.toInt(),
        )
        if (errno != 0) {
            return@withScopedMemoryAllocator if (collected.isEmpty()) {
                WasiLineReadResult.Error(classifyErrno(errno))
            } else {
                collected.toLineResult()
            }
        }

        val bytesRead = nreadPtr.loadInt()
        if (bytesRead == 0) {
            return@withScopedMemoryAllocator if (droppingOversizedLine) {
                WasiLineReadResult.Error(WasiDecodeError.LineTooLong(MAX_LINE_BYTES))
            } else if (collected.isEmpty()) {
                WasiLineReadResult.Eof
            } else {
                collected.toLineResult()
            }
        }

        val byte = byteBuffer.loadByte()
        if (droppingOversizedLine) {
            if (byte == LF) {
                return@withScopedMemoryAllocator WasiLineReadResult.Error(
                    WasiDecodeError.LineTooLong(MAX_LINE_BYTES),
                )
            }
            continue
        }
        if (byte == LF) {
            return@withScopedMemoryAllocator collected.toLineResult()
        }
        if (collected.size >= MAX_LINE_BYTES) {
            droppingOversizedLine = true
            continue
        }
        collected.add(byte)
    }
    error("unreachable")
}

@OptIn(ExperimentalWasmInterop::class, UnsafeWasmMemoryApi::class)
internal fun writeStderrLine(message: String) = withScopedMemoryAllocator { allocator ->
    val bytes = (message + "\n").encodeToByteArray()
    val buffer = allocator.allocate(bytes.size)
    for (index in bytes.indices) {
        (buffer + index).storeByte(bytes[index])
    }
    val iovec = allocator.allocate(8).also {
        (it + 0).storeInt(buffer.address.toInt())
        (it + 4).storeInt(bytes.size)
    }
    val nwritten = allocator.allocate(4)
    wasiRawFdWrite(
        fd = STDERR_FD,
        iovs = iovec.address.toInt(),
        iovsLen = 1,
        nwritten = nwritten.address.toInt(),
    )
}
