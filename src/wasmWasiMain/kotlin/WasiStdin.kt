import kotlin.wasm.ExperimentalWasmInterop
import kotlin.wasm.WasmImport
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

/**
 * The reader is UTF-8 safe: bytes are accumulated in a [ByteArray] and
 * only decoded into a [String] once a full line has been collected.
 */
private const val STDIN_FD: Int = 0
internal const val LF: Byte = 0x0A
internal const val CR: Byte = 0x0D

@OptIn(ExperimentalWasmInterop::class, UnsafeWasmMemoryApi::class)
@WasmImport("wasi_snapshot_preview1", "fd_read")
private external fun wasiRawFdRead(fd: Int, iovs: Int, iovsLen: Int, nread: Int): Int

/**
 * Reads one UTF-8 encoded line from stdin via WASI `fd_read`.
 *
 * @return the decoded line, or `null` on EOF.
 */
@OptIn(ExperimentalWasmInterop::class, UnsafeWasmMemoryApi::class)
fun wasiReadLine(): String? = withScopedMemoryAllocator { allocator ->
    val byteBuffer = allocator.allocate(1)
    val iovec = allocator.allocate(8).also {
        (it + 0).storeInt(byteBuffer.address.toInt())
        (it + 4).storeInt(1)
    }
    val nreadPtr = allocator.allocate(4)

    val collected = ArrayList<Byte>(64)
    while (true) {
        val errno = wasiRawFdRead(
            fd = STDIN_FD,
            iovs = iovec.address.toInt(),
            iovsLen = 1,
            nread = nreadPtr.address.toInt(),
        )
        if (errno != 0) return@withScopedMemoryAllocator collected.toLineOrNull()
        if (nreadPtr.loadInt() == 0) return@withScopedMemoryAllocator collected.toLineOrNull()

        val byte = byteBuffer.loadByte()
        if (byte == LF) return@withScopedMemoryAllocator collected.toLine()
        collected.add(byte)
    }
    @Suppress("UNREACHABLE_CODE")
    null
}

internal fun List<Byte>.toLineOrNull(): String? = if (isEmpty()) null else toLine()

internal fun List<Byte>.toLine(): String {
    val end = if (isNotEmpty() && last() == CR) size - 1 else size
    val bytes = ByteArray(end)
    for (i in 0 until end) bytes[i] = this[i]
    return bytes.decodeToString()
}
