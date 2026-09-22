package com.mrredhood.devforge.core.git

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.InflaterInputStream

internal class GitPackObjectReader(
    private val resolver: ContentResolver,
    private val objectsDirectory: Uri,
    private val looseFallback: (String, Int) -> GitObjectResult?,
) {
    fun read(sha: String, maxBytes: Int = 4 * 1024 * 1024): GitObjectResult =
        readRecursive(sha.lowercase(), maxBytes, 0)
            ?: GitObjectResult.Unavailable("Git packed object $sha is unavailable.")

    private fun readRecursive(sha: String, maxBytes: Int, depth: Int): GitObjectResult? {
        if (depth > MAX_DELTA_DEPTH) return GitObjectResult.Unavailable("Git delta chain exceeded the safety depth.")
        val loose = looseFallback(sha, maxBytes)
        if (loose is GitObjectResult.Success) return loose
        val packDirectory = findDirectChild(objectsDirectory, "pack") ?: return null
        val indexFiles = listChildren(packDirectory).filter { it.name.endsWith(".idx", true) }.take(MAX_PACKS)
        for (idx in indexFiles) {
            val offset = findOffset(idx.uri, sha) ?: continue
            val packName = idx.name.dropLast(4) + ".pack"
            val packUri = findDirectChild(packDirectory, packName) ?: continue
            val result = readAt(packUri, offset, maxBytes, depth)
            if (result != null) return result
        }
        return null
    }

    private fun readAt(
        packUri: Uri,
        offset: Long,
        maxBytes: Int,
        depth: Int,
    ): GitObjectResult? = runCatching {
        require(offset >= 12) { "Invalid packed object offset." }
        require(offset <= MAX_PACK_OFFSET) { "Packed object offset exceeds the mobile safety limit." }
        resolver.openInputStream(packUri)?.use { input ->
            skipFully(input, offset)
            val header = readPackObjectHeader(input)
            when (header.type) {
                TYPE_COMMIT -> GitObjectResult.Success("commit", inflateObject(input, header.size, maxBytes))
                TYPE_TREE -> GitObjectResult.Success("tree", inflateObject(input, header.size, maxBytes))
                TYPE_BLOB -> GitObjectResult.Success("blob", inflateObject(input, header.size, maxBytes))
                TYPE_TAG -> GitObjectResult.Success("tag", inflateObject(input, header.size, maxBytes))
                TYPE_OFS_DELTA -> {
                    val baseDistance = readOfsDeltaDistance(input)
                    val baseOffset = offset - baseDistance
                    require(baseOffset >= 12) { "Packed delta base offset is invalid." }
                    val delta = inflateObject(input, header.size, maxBytes)
                    val base = readAt(packUri, baseOffset, maxBytes, depth + 1)
                        ?: throw IllegalStateException("Packed delta base object is unavailable.")
                    val baseResult = base as? GitObjectResult.Success
                        ?: throw IllegalStateException((base as GitObjectResult.Unavailable).reason)
                    applyDelta(baseResult, delta, maxBytes)
                }
                TYPE_REF_DELTA -> {
                    val baseSha = readExact(input, 20).joinToString("") { "%02x".format(it) }
                    val delta = inflateObject(input, header.size, maxBytes)
                    val base = readRecursive(baseSha, maxBytes, depth + 1)
                        ?: throw IllegalStateException("Packed ref-delta base object is unavailable.")
                    val baseResult = base as? GitObjectResult.Success
                        ?: throw IllegalStateException((base as GitObjectResult.Unavailable).reason)
                    applyDelta(baseResult, delta, maxBytes)
                }
                else -> throw IllegalStateException("Unsupported packed Git object type.")
            }
        } ?: throw IllegalStateException("Unable to open Git pack file.")
    }.getOrElse { error ->
        GitObjectResult.Unavailable(error.message ?: "Packed Git object could not be read.")
    }

    private fun findOffset(indexUri: Uri, sha: String): Long? = runCatching {
        val bytes = readBounded(indexUri, MAX_INDEX_BYTES) ?: return@runCatching null
        if (bytes.size < 8 || bytes[0] != 0xFF.toByte() || bytes[1] != 't'.code.toByte() ||
            bytes[2] != 'O'.code.toByte() || bytes[3] != 'c'.code.toByte()
        ) return@runCatching null
        val version = readInt(bytes, 4)
        if (version != 2) return@runCatching null
        val fanoutBase = 8
        val first = sha.take(2).toInt(16)
        val low = if (first == 0) 0 else readInt(bytes, fanoutBase + (first - 1) * 4)
        val highExclusive = readInt(bytes, fanoutBase + first * 4)
        if (highExclusive <= low) return@runCatching null
        require(low >= 0 && highExclusive <= MAX_INDEX_OBJECTS) { "Git pack index is too large." }
        val objectNamesBase = fanoutBase + 256 * 4
        val totalObjects = readInt(bytes, fanoutBase + 255 * 4)
        require(totalObjects >= highExclusive) { "Git pack fanout table is inconsistent." }
        require(totalObjects in 1..MAX_INDEX_OBJECTS) { "Git pack index is too large." }
        val crcTableBase = objectNamesBase + totalObjects * 20
        val offsetsBase = crcTableBase + totalObjects * 4
        var left = low
        var right = highExclusive - 1
        var found = -1
        while (left <= right) {
            val mid = (left + right) ushr 1
            val nameOffset = objectNamesBase + mid * 20
            val comparison = compareSha(bytes, nameOffset, sha)
            when {
                comparison == 0 -> {
                    found = mid
                    break
                }
                comparison < 0 -> left = mid + 1
                else -> right = mid - 1
            }
        }
        if (found < 0) return@runCatching null
        val rawOffset = readUInt(bytes, offsetsBase + found * 4)
        if ((rawOffset and 0x80000000L) == 0L) {
            rawOffset
        } else {
            val largeIndex = (rawOffset and 0x7fffffffL).toInt()
            val largeBase = offsetsBase + totalObjects * 4
            readLong(bytes, largeBase + largeIndex * 8)
        }
    }.getOrNull()

    private fun applyDelta(
        base: GitObjectResult.Success,
        delta: ByteArray,
        maxBytes: Int,
    ): GitObjectResult {
        var cursor = 0
        val baseSize = readVarInt(delta, cursor).also { cursor = it.next }
        require(baseSize.value == base.content.size.toLong()) { "Git delta base size does not match." }
        val target = readVarInt(delta, cursor)
        cursor = target.next
        require(target.value <= maxBytes) { "Git delta result exceeds the safety limit." }
        val output = ByteArrayOutputStream(target.value.toInt())
        while (cursor < delta.size && output.size().toLong() < target.value) {
            val instruction = delta[cursor++].toInt() and 0xFF
            if ((instruction and 0x80) == 0) {
                val size = instruction and 0x7F
                require(size > 0 && cursor + size <= delta.size)
                output.write(delta, cursor, size)
                cursor += size
            } else {
                var sourceOffset = 0
                if ((instruction and 0x01) != 0) { sourceOffset = readByte(delta, cursor) shl 0; cursor++ }
                if ((instruction and 0x02) != 0) { sourceOffset = sourceOffset or (readByte(delta, cursor) shl 8); cursor++ }
                if ((instruction and 0x04) != 0) { sourceOffset = sourceOffset or (readByte(delta, cursor) shl 16); cursor++ }
                if ((instruction and 0x08) != 0) { sourceOffset = sourceOffset or (readByte(delta, cursor) shl 24); cursor++ }
                var copySize = 0
                if ((instruction and 0x10) != 0) { copySize = readByte(delta, cursor); cursor++ }
                if ((instruction and 0x20) != 0) { copySize = copySize or (readByte(delta, cursor) shl 8); cursor++ }
                if ((instruction and 0x40) != 0) { copySize = copySize or (readByte(delta, cursor) shl 16); cursor++ }
                if (copySize == 0) copySize = 0x10000
                require(sourceOffset >= 0 && sourceOffset + copySize <= base.content.size) {
                    "Git delta copy exceeds the base object."
                }
                output.write(base.content, sourceOffset, copySize)
            }
            require(output.size() <= target.value) { "Git delta result exceeded the declared target size." }
        }
        require(output.size().toLong() == target.value) { "Git delta result is truncated." }
        return GitObjectResult.Success(base.type, output.toByteArray())
    }

    private data class Header(val type: Int, val size: Long)

    private fun readPackObjectHeader(input: InputStream): Header {
        var first = input.read()
        require(first >= 0) { "Packed object header is truncated." }
        val type = (first ushr 4) and 0x07
        var size = (first and 0x0F).toLong()
        var shift = 4
        while ((first and 0x80) != 0) {
            first = input.read()
            require(first >= 0) { "Packed object size header is truncated." }
            size = size or ((first and 0x7F).toLong() shl shift)
            shift += 7
            require(shift <= 63) { "Packed object size header is invalid." }
        }
        return Header(type, size)
    }

    private fun inflateObject(input: InputStream, declaredSize: Long, maxBytes: Int): ByteArray {
        require(declaredSize <= maxBytes) { "Packed Git object exceeds the safety limit." }
        val inflater = InflaterInputStream(input)
        val out = ByteArrayOutputStream(declaredSize.toInt().coerceAtMost(64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        inflater.use {
            while (true) {
                val read = it.read(buffer)
                if (read <= 0) break
                total += read
                require(total <= maxBytes) { "Packed Git object exceeded the safety limit." }
                out.write(buffer, 0, read)
            }
        }
        require(total.toLong() == declaredSize) { "Packed Git object size does not match the header." }
        return out.toByteArray()
    }

    private fun readOfsDeltaDistance(input: InputStream): Long {
        var first = input.read()
        require(first >= 0)
        var value = (first and 0x7F).toLong()
        while ((first and 0x80) != 0) {
            first = input.read()
            require(first >= 0)
            value = (value + 1L) * 128L + (first and 0x7F)
        }
        return value
    }

    private fun readExact(input: InputStream, count: Int): ByteArray {
        val output = ByteArray(count)
        var readTotal = 0
        while (readTotal < count) {
            val read = input.read(output, readTotal, count - readTotal)
            require(read > 0)
            readTotal += read
        }
        return output
    }

    private fun skipFully(input: InputStream, distance: Long) {
        var remaining = distance
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
                continue
            }
            require(input.read() >= 0) { "Git pack file ended before the requested offset." }
            remaining--
        }
    }

    private fun readBounded(uri: Uri, maxBytes: Int): ByteArray? {
        resolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream(minOf(maxBytes, 1024 * 1024))
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > maxBytes) return null
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
        return null
    }

    private data class Child(val uri: Uri, val name: String)

    private fun findDirectChild(parent: Uri, name: String): Uri? =
        listChildren(parent).firstOrNull { it.name == name }?.uri

    private fun listChildren(parent: Uri): List<Child> = runCatching {
        val id = runCatching { DocumentsContract.getDocumentId(parent) }
            .getOrElse { DocumentsContract.getTreeDocumentId(parent) }
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, id)
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext() && size < MAX_CHILDREN) {
                    val documentId = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    add(Child(DocumentsContract.buildDocumentUriUsingTree(parent, documentId), name))
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int

    private fun readUInt(bytes: ByteArray, offset: Int): Long =
        readInt(bytes, offset).toLong() and 0xffffffffL

    private fun readLong(bytes: ByteArray, offset: Int): Long =
        ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.BIG_ENDIAN).long

    private fun compareSha(bytes: ByteArray, offset: Int, sha: String): Int {
        for (index in 0 until 20) {
            val current = bytes[offset + index].toInt() and 0xFF
            val expected = sha.substring(index * 2, index * 2 + 2).toInt(16)
            if (current != expected) return current - expected
        }
        return 0
    }

    private fun readVarInt(bytes: ByteArray, offset: Int): VarInt {
        var cursor = offset
        var value = 0L
        var shift = 0
        while (true) {
            require(cursor < bytes.size)
            val current = bytes[cursor++].toInt() and 0xFF
            value = value or ((current and 0x7F).toLong() shl shift)
            if ((current and 0x80) == 0) return VarInt(value, cursor)
            shift += 7
            require(shift <= 63)
        }
    }

    private data class VarInt(val value: Long, val next: Int)

    private fun readByte(bytes: ByteArray, offset: Int): Int {
        require(offset in bytes.indices)
        return bytes[offset].toInt() and 0xFF
    }

    companion object {
        private const val TYPE_COMMIT = 1
        private const val TYPE_TREE = 2
        private const val TYPE_BLOB = 3
        private const val TYPE_TAG = 4
        private const val TYPE_OFS_DELTA = 6
        private const val TYPE_REF_DELTA = 7
        private const val MAX_INDEX_BYTES = 64 * 1024 * 1024
        private const val MAX_INDEX_OBJECTS = 1_000_000
        private const val MAX_PACKS = 32
        private const val MAX_PACK_OFFSET = 256L * 1024L * 1024L
        private const val MAX_DELTA_DEPTH = 20
        private const val MAX_CHILDREN = 128
    }
}
