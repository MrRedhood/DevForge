package com.mrredhood.devforge.core.editor

import kotlin.math.max
import kotlin.math.min

class Editor2Document(
    initialText: String,
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE,
) {
    private val chunks = ArrayList<String>()
    private val lineStarts = ArrayList<Int>()
    private var version = 0L

    init {
        require(chunkSize in 4 * 1024..256 * 1024)
        replaceAll(initialText)
    }

    fun snapshot(): String = buildString { chunks.forEach(::append) }

    fun reset(text: String) {
        replaceAll(text)
    }

    fun version(): Long = version

    fun replace(start: Int, endExclusive: Int, replacement: String) {
        val current = snapshot()
        val from = start.coerceIn(0, current.length)
        val to = endExclusive.coerceIn(from, current.length)
        replaceAll(current.substring(0, from) + replacement + current.substring(to))
    }

    fun lineCount(): Int = max(1, lineStarts.size)

    fun lineStart(line: Int): Int {
        val index = (line - 1).coerceIn(0, lineStarts.lastIndex)
        return lineStarts[index]
    }

    fun lineEnd(line: Int): Int {
        val start = lineStart(line)
        val next = if (line < lineCount()) lineStart(line + 1) else snapshot().length
        return max(start, min(next, snapshot().length))
    }

    fun visibleLines(firstLine: Int, lastLine: Int): List<String> {
        val text = snapshot()
        val fromLine = firstLine.coerceIn(1, lineCount())
        val toLine = lastLine.coerceIn(fromLine, lineCount())
        return (fromLine..toLine).map { line ->
            text.substring(lineStart(line), lineEnd(line)).removeSuffix("\n")
        }
    }

    private fun replaceAll(text: String) {
        chunks.clear()
        var cursor = 0
        while (cursor < text.length) {
            val end = min(cursor + chunkSize, text.length)
            chunks += text.substring(cursor, end)
            cursor = end
        }
        if (text.isEmpty()) chunks += ""
        lineStarts.clear()
        lineStarts += 0
        text.forEachIndexed { index, char ->
            if (char == '\n' && index + 1 <= text.length) lineStarts += index + 1
        }
        version++
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE = 32 * 1024
    }
}

class IncrementalTokenCache {
    private data class Entry(
        val version: Long,
        val startLine: Int,
        val endLine: Int,
        val language: EditorLanguage,
        val tokens: List<String>,
    )

    private var entry: Entry? = null

    fun invalidate(startLine: Int, endLine: Int) {
        val current = entry ?: return
        if (startLine <= current.endLine && endLine >= current.startLine) entry = null
    }

    fun get(version: Long, language: EditorLanguage, startLine: Int, endLine: Int): List<String>? =
        entry?.takeIf {
            it.version == version &&
                it.language == language &&
                it.startLine <= startLine &&
                it.endLine >= endLine
        }?.tokens

    fun put(version: Long, language: EditorLanguage, startLine: Int, endLine: Int, tokens: List<String>) {
        entry = Entry(version, startLine, endLine, language, tokens)
    }
}

class EditorViewportRenderer(
    private val maxVisibleLines: Int = 400,
) {
    fun window(firstLine: Int, lineCount: Int): IntRange {
        val start = max(1, firstLine)
        val end = min(lineCount, start + maxVisibleLines - 1)
        return start..max(start, end)
    }
}
