package com.mrredhood.devforge.core.editor

/** Lightweight line-oriented diff suitable for previews, review and patch preparation. */
class DiffEngine {
    fun compare(before: String, after: String): List<DiffLine> {
        if (before == after) return emptyList()
        val oldLines = before.lines()
        val newLines = after.lines()
        val common = minOf(oldLines.size, newLines.size)
        val result = mutableListOf<DiffLine>()

        for (index in 0 until common) {
            if (oldLines[index] == newLines[index]) {
                result += DiffLine(DiffKind.CONTEXT, index + 1, index + 1, oldLines[index])
            } else {
                result += DiffLine(DiffKind.REMOVED, index + 1, null, oldLines[index])
                result += DiffLine(DiffKind.ADDED, null, index + 1, newLines[index])
            }
        }

        if (oldLines.size > common) {
            for (index in common until oldLines.size) {
                result += DiffLine(DiffKind.REMOVED, index + 1, null, oldLines[index])
            }
        }
        if (newLines.size > common) {
            for (index in common until newLines.size) {
                result += DiffLine(DiffKind.ADDED, null, index + 1, newLines[index])
            }
        }
        return result
    }
}

data class DiffLine(
    val kind: DiffKind,
    val oldLine: Int?,
    val newLine: Int?,
    val text: String,
)

enum class DiffKind { CONTEXT, ADDED, REMOVED }
