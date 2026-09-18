package com.mrredhood.devforge.core.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.mrredhood.devforge.core.diagnostics.Diagnostic
import com.mrredhood.devforge.core.diagnostics.DiagnosticLocation
import com.mrredhood.devforge.core.diagnostics.DiagnosticReport
import com.mrredhood.devforge.core.diagnostics.DiagnosticSeverity
import com.mrredhood.devforge.core.diagnostics.DiagnosticSource

enum class EditorLanguage {
    KOTLIN, JAVA, JAVASCRIPT, TYPESCRIPT, PYTHON, GO, RUST, C_LIKE, PLAIN;

    companion object {
        fun detect(name: String): EditorLanguage {
            return when {
                name.endsWith(".kt", true) || name.endsWith(".kts", true) -> KOTLIN
                name.endsWith(".java", true) -> JAVA
                name.endsWith(".js", true) || name.endsWith(".jsx", true) -> JAVASCRIPT
                name.endsWith(".ts", true) || name.endsWith(".tsx", true) -> TYPESCRIPT
                name.endsWith(".py", true) -> PYTHON
                name.endsWith(".go", true) -> GO
                name.endsWith(".rs", true) -> RUST
                name.endsWith(".c", true) || name.endsWith(".h", true) || name.endsWith(".cpp", true) || name.endsWith(".hpp", true) -> C_LIKE
                else -> PLAIN
            }
        }
    }
}

class CodeSyntaxVisualTransformation(
    private val language: EditorLanguage,
    private val keywordColor: Color,
    private val stringColor: Color,
    private val commentColor: Color,
    private val numberColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text.length > 256 * 1024 || language == EditorLanguage.PLAIN) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val builder = AnnotatedString.Builder(text.text)
        val keywords = KEYWORDS[language].orEmpty()
        keywords.forEach { keyword ->
            Regex("\\b" + Regex.escape(keyword) + "\\b").findAll(text.text).forEach { match ->
                builder.addStyle(SpanStyle(color = keywordColor), match.range.first, match.range.last + 1)
            }
        }
        STRING_REGEX.findAll(text.text).forEach { match ->
            builder.addStyle(SpanStyle(color = stringColor), match.range.first, match.range.last + 1)
        }
        COMMENT_REGEX.findAll(text.text).forEach { match ->
            builder.addStyle(SpanStyle(color = commentColor), match.range.first, match.range.last + 1)
        }
        NUMBER_REGEX.findAll(text.text).forEach { match ->
            builder.addStyle(SpanStyle(color = numberColor), match.range.first, match.range.last + 1)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    private companion object {
        val STRING_REGEX = Regex(""(?:\\\\.|[^"\\\\])*"|'(?:\\\\.|[^'\\\\])*'")
        val COMMENT_REGEX = Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/|#[^\\n]*")
        val NUMBER_REGEX = Regex("\\b(?:0x[0-9A-Fa-f]+|\\d+(?:\\.\\d+)?)\\b")
        val COMMON = setOf(
            "class","interface","object","fun","function","def","return","if","else","when","for","while",
            "in","is","as","val","var","const","let","new","this","public","private","protected","static",
            "extends","implements","import","from","package","try","catch","finally","throw","async","await",
            "true","false","null","fun","struct","enum","type","fn","impl","match","use","mod",
        )
        val KEYWORDS = EditorLanguage.entries.associateWith { COMMON }
    }
}

data class EditorFoldRange(
    val startOffset: Int,
    val endOffset: Int,
    val startLine: Int,
    val endLine: Int,
)

object EditorFolding {
    fun ranges(content: String, maxRanges: Int = 80): List<EditorFoldRange> {
        require(maxRanges in 1..80)
        val stack = ArrayDeque<Pair<Int, Int>>()
        val ranges = mutableListOf<EditorFoldRange>()
        content.forEachIndexed { index, char ->
            when (char) {
                '{' -> stack.addLast(index to lineAt(content, index))
                '}' -> {
                    val open = stack.removeLastOrNull() ?: return@forEachIndexed
                    if (index > open.first + 1 && open.second < lineAt(content, index)) {
                        ranges += EditorFoldRange(open.first, index, open.second, lineAt(content, index))
                    }
                    if (ranges.size >= maxRanges) return@forEachIndexed
                }
            }
        }
        return ranges
    }

    private fun lineAt(content: String, offset: Int): Int = content.take(offset).count { it == '\n' } + 1
}

object EditorDiagnostics {
    fun analyze(path: String, content: String): DiagnosticReport {
        if (content.toByteArray(Charsets.UTF_8).size > 256 * 1024) return DiagnosticReport(emptyList())
        val diagnostics = mutableListOf<Diagnostic>()
        val stack = ArrayDeque<Pair<Char, Int>>()
        var inString = false
        var quote = '\u0000'
        var escaped = false
        content.forEachIndexed { index, char ->
            if (escaped) {
                escaped = false
                return@forEachIndexed
            }
            if (inString && char == '\\') {
                escaped = true
                return@forEachIndexed
            }
            if (char == '"' || char == '\'') {
                if (!inString) {
                    inString = true
                    quote = char
                } else if (quote == char) {
                    inString = false
                }
                return@forEachIndexed
            }
            if (inString) return@forEachIndexed
            when (char) {
                '(', '[', '{' -> stack.addLast(char to index)
                ')' , ']' , '}' -> {
                    val expected = when (char) { ')' -> '('; ']' -> '['; else -> '{' }
                    val open = stack.removeLastOrNull()
                    if (open == null || open.first != expected) {
                        diagnostics += diagnostic(path, content, index, "Mismatched closing '$char'.", "EDITOR_BRACKET")
                    }
                }
            }
            if (diagnostics.size >= 100) return@forEachIndexed
        }
        stack.take(20).forEach { (char, index) ->
            diagnostics += diagnostic(path, content, index, "Unclosed '$char'.", "EDITOR_BRACKET")
        }
        if (inString) diagnostics += diagnostic(path, content, content.lastIndex.coerceAtLeast(0), "Unterminated string literal.", "EDITOR_STRING")
        return DiagnosticReport(diagnostics.take(100))
    }

    private fun diagnostic(path: String, content: String, offset: Int, message: String, code: String): Diagnostic {
        val prefix = content.take(offset.coerceIn(0, content.length))
        val line = prefix.count { it == '\n' } + 1
        val column = prefix.substringAfterLast('\n').length + 1
        return Diagnostic(
            severity = DiagnosticSeverity.WARNING,
            source = DiagnosticSource.COMPILER,
            message = message,
            code = code,
            location = DiagnosticLocation(path, line, column),
            origin = "DevForge editor",
        )
    }
}


class FoldingVisualTransformation(
    private val source: String,
    ranges: List<EditorFoldRange>,
) : VisualTransformation {
    private val ranges = ranges
        .sortedBy { it.startOffset }
        .fold(mutableListOf<EditorFoldRange>()) { result, range ->
            if (range.startOffset >= 0 && range.endOffset > range.startOffset &&
                result.none { range.startOffset < it.endOffset && range.endOffset > it.startOffset }
            ) result += range
            result
        }

    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text != source || ranges.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val out = StringBuilder()
        var sourceCursor = 0
        ranges.forEach { range ->
            out.append(source, sourceCursor, range.startOffset + 1)
            out.append('…')
            sourceCursor = range.endOffset
        }
        out.append(source, sourceCursor, source.length)

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var transformed = offset
                for (range in ranges) {
                    val hiddenStart = range.startOffset + 1
                    val hiddenEnd = range.endOffset
                    if (offset <= range.startOffset) break
                    if (offset < hiddenEnd) return transformed - (hiddenStart - transformed + 1).coerceAtMost(0)
                    transformed -= (hiddenEnd - hiddenStart - 1)
                }
                return transformed.coerceIn(0, out.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                var original = offset
                for (range in ranges) {
                    val transformedStart = range.startOffset + 1 - (range.endOffset - range.startOffset - 2)
                    if (offset < transformedStart) break
                    if (offset <= transformedStart + 1) return range.startOffset + 1
                    original += range.endOffset - range.startOffset - 2
                }
                return original.coerceIn(0, source.length)
            }
        }
        return TransformedText(AnnotatedString(out.toString()), mapping)
    }
}
