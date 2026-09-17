package com.mrredhood.devforge.core.git

import java.nio.charset.StandardCharsets

internal data class GitIndexEntry(
    val path: String,
    val objectId: String,
    val mode: Long,
)

internal sealed interface GitIndexParseResult {
    data class Success(val version: Int, val entries: List<GitIndexEntry>, val truncated: Boolean) : GitIndexParseResult
    data class Unsupported(val reason: String) : GitIndexParseResult
}

internal object GitIndexParser {
    private const val HEADER_BYTES = 12
    private const val ENTRY_FIXED_BYTES = 62
    private const val MAX_ENTRIES = 20_000

    fun parse(bytes: ByteArray): GitIndexParseResult {
        if (bytes.size < HEADER_BYTES) return GitIndexParseResult.Unsupported("The Git index is too small to contain a valid header.")
        if (String(bytes, 0, 4, StandardCharsets.US_ASCII) != "DIRC") {
            return GitIndexParseResult.Unsupported("The Git index signature is invalid.")
        }

        val version = readUInt32(bytes, 4).toInt()
        if (version != 2 && version != 3) {
            return GitIndexParseResult.Unsupported("Git index version $version is not supported yet; versions 2 and 3 are supported.")
        }

        val entryCountLong = readUInt32(bytes, 8)
        if (entryCountLong > MAX_ENTRIES) {
            return GitIndexParseResult.Success(version, emptyList(), truncated = true)
        }

        val entries = ArrayList<GitIndexEntry>(entryCountLong.toInt())
        var offset = HEADER_BYTES
        repeat(entryCountLong.toInt()) {
            if (offset + ENTRY_FIXED_BYTES > bytes.size) {
                return GitIndexParseResult.Unsupported("The Git index ended before all entries could be read.")
            }

            val mode = readUInt32(bytes, offset + 24)
            val objectIdOffset = offset + 40
            val flags = readUInt16(bytes, offset + 60)
            var pathOffset = offset + ENTRY_FIXED_BYTES

            if ((flags and 0x4000) != 0) {
                if (pathOffset + 2 > bytes.size) {
                    return GitIndexParseResult.Unsupported("The Git index extended-entry flags are truncated.")
                }
                pathOffset += 2
            }

            val pathEnd = findNull(bytes, pathOffset)
                ?: return GitIndexParseResult.Unsupported("The Git index contains an unterminated path entry.")
            val path = String(bytes, pathOffset, pathEnd - pathOffset, StandardCharsets.UTF_8)
            if (path.isBlank()) {
                return GitIndexParseResult.Unsupported("The Git index contains an empty path entry.")
            }

            val objectId = bytes.copyOfRange(objectIdOffset, objectIdOffset + 20)
                .joinToString("") { "%02x".format(it) }
            entries += GitIndexEntry(path = path, objectId = objectId, mode = mode)

            val entryBytes = pathEnd + 1 - offset
            val padding = (8 - (entryBytes % 8)) % 8
            offset = pathEnd + 1 + padding
            if (offset > bytes.size) {
                return GitIndexParseResult.Unsupported("The Git index entry padding extends beyond the available data.")
            }
        }

        return GitIndexParseResult.Success(version, entries, truncated = false)
    }

    private fun readUInt16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun readUInt32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)

    private fun findNull(bytes: ByteArray, start: Int): Int {
        for (index in start until bytes.size) {
            if (bytes[index].toInt() == 0) return index
        }
        return -1
    }
}
