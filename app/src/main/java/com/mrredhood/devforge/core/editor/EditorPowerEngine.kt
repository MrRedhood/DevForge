package com.mrredhood.devforge.core.editor

data class EditorPowerEdit(
    val content: String,
    val cursor: Int,
    val selectionStart: Int = cursor,
    val selectionEnd: Int = cursor,
)

object EditorPowerEngine {
    fun lineRange(text: String, cursor: Int): IntRange {
        val safeCursor = cursor.coerceIn(0, text.length)
        val start = text.lastIndexOf('\n', safeCursor - 1).let { if (it < 0) 0 else it + 1 }
        val newline = text.indexOf('\n', safeCursor)
        val end = if (newline < 0) text.length else newline
        return start..end
    }

    fun selectLine(text: String, cursor: Int): EditorPowerEdit {
        val range = lineRange(text, cursor)
        return EditorPowerEdit(text, range.first, range.first, range.last)
    }

    fun duplicateLine(text: String, cursor: Int): EditorPowerEdit {
        val range = lineRange(text, cursor)
        val line = text.substring(range.first, range.last)
        val hasNewline = range.last < text.length && text[range.last] == '\n'
        val insertion = line + if (hasNewline) "\n" else "\n"
        val result = text.substring(0, range.last + if (hasNewline) 1 else 0) +
            insertion +
            text.substring(range.last + if (hasNewline) 1 else 0)
        val newCursor = cursor + insertion.length
        return EditorPowerEdit(result, newCursor)
    }

    fun deleteLine(text: String, cursor: Int): EditorPowerEdit {
        if (text.isEmpty()) return EditorPowerEdit(text, 0)
        val range = lineRange(text, cursor)
        val start = range.first
        val endExclusive = when {
            range.last < text.length -> range.last + 1
            start > 0 -> start
            else -> text.length
        }
        val actualStart = if (range.last == text.length && start > 0) start - 1 else start
        val result = text.removeRange(actualStart, endExclusive)
        return EditorPowerEdit(result, actualStart.coerceIn(0, result.length))
    }

    fun moveLine(text: String, cursor: Int, direction: Int): EditorPowerEdit {
        if (text.isEmpty() || direction == 0) return EditorPowerEdit(text, cursor.coerceIn(0, text.length))
        val normalized = text.split('\n').toMutableList()
        val safeCursor = cursor.coerceIn(0, text.length)
        val lineIndex = text.take(safeCursor).count { it == '\n' }.coerceIn(0, normalized.lastIndex)
        val target = lineIndex + if (direction < 0) -1 else 1
        if (target !in normalized.indices) return EditorPowerEdit(text, safeCursor)
        val column = safeCursor - (text.lastIndexOf('\n', safeCursor - 1).let { if (it < 0) 0 else it + 1 })
        val currentLine = normalized[lineIndex]
        normalized[lineIndex] = normalized[target]
        normalized[target] = currentLine
        val result = normalized.joinToString("\n")
        var newLineStart = 0
        repeat(target) {
            newLineStart = result.indexOf('\n', newLineStart).let { if (it < 0) result.length else it + 1 }
        }
        return EditorPowerEdit(result, (newLineStart + column).coerceAtMost(result.length))
    }

    fun toggleComment(text: String, cursor: Int, language: EditorLanguage): EditorPowerEdit {
        val range = lineRange(text, cursor)
        val line = text.substring(range.first, range.last)
        val token = commentToken(language) ?: return EditorPowerEdit(text, cursor)
        val leading = line.takeWhile { it == ' ' || it == '\t' }
        val body = line.drop(leading.length)
        val next = if (body.startsWith(token)) {
            leading + body.removePrefix(token).removePrefix(" ")
        } else {
            leading + token + if (body.isBlank()) "" else " " + body
        }
        val result = text.substring(0, range.first) + next + text.substring(range.last)
        return EditorPowerEdit(result, cursor + (next.length - line.length).coerceIn(Int.MIN_VALUE, Int.MAX_VALUE))
    }

    private fun commentToken(language: EditorLanguage): String? = when (language) {
        EditorLanguage.PYTHON, EditorLanguage.RUBY, EditorLanguage.SHELL, EditorLanguage.YAML, EditorLanguage.TOML -> "#"
        EditorLanguage.SQL -> "--"
        else -> "//"
    }
}