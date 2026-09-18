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
        val STRING_REGEX = Regex("""("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')""")
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
        val ranges = ArrayList<EditorFoldRange>(maxRanges)
        var line = 1

        for (index in content.indices) {
            when (content[index]) {
                '{' -> stack.addLast(index to line)
                '}' -> {
                    val open = stack.removeLastOrNull()
                    if (open != null && index > open.first + 1 && open.second < line) {
                        ranges += EditorFoldRange(open.first, index, open.second, line)
                        if (ranges.size >= maxRanges) break
                    }
                }
            }
            if (content[index] == '\n') line++
        }
        return ranges
    }
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
        if (text.text != source || ranges.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val output = StringBuilder()
        val originalToTransformed = IntArray(source.length + 1)
        val transformedToOriginal = mutableListOf<Int>()
        var sourceCursor = 0
        var outputCursor = 0
        originalToTransformed[0] = 0
        ranges.forEach { range ->
            while (sourceCursor <= range.startOffset && sourceCursor < source.length) {
                output.append(source[sourceCursor])
                transformedToOriginal += sourceCursor
                outputCursor++
                sourceCursor++
                originalToTransformed[sourceCursor] = outputCursor
            }
            val placeholderPosition = outputCursor
            for (offset in sourceCursor until range.endOffset) {
                originalToTransformed[offset] = placeholderPosition
            }
            output.append('…')
            transformedToOriginal += range.startOffset + 1
            outputCursor++
            sourceCursor = range.endOffset
            originalToTransformed[sourceCursor] = outputCursor
        }
        while (sourceCursor < source.length) {
            output.append(source[sourceCursor])
            transformedToOriginal += sourceCursor
            outputCursor++
            sourceCursor++
            originalToTransformed[sourceCursor] = outputCursor
        }
        val reverse = IntArray(output.length + 1)
        transformedToOriginal.forEachIndexed { index, original -> reverse[index] = original }
        reverse[output.length] = source.length
        return TransformedText(
            AnnotatedString(output.toString()),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = originalToTransformed[offset.coerceIn(0, source.length)].coerceIn(0, output.length)
                override fun transformedToOriginal(offset: Int): Int = reverse[offset.coerceIn(0, output.length)].coerceIn(0, source.length)
            },
        )
    }
}

class ChainedVisualTransformation(
    private val first: VisualTransformation,
    private val second: VisualTransformation,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val a = first.filter(text)
        val b = second.filter(a.text)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                b.offsetMapping.originalToTransformed(a.offsetMapping.originalToTransformed(offset))
            override fun transformedToOriginal(offset: Int): Int =
                a.offsetMapping.transformedToOriginal(b.offsetMapping.transformedToOriginal(offset))
        }
        return TransformedText(b.text, mapping)
    }
}


class VisibleWhitespaceVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val output = StringBuilder()
        val originalToTransformed = IntArray(text.text.length + 1)
        val transformedToOriginal = mutableListOf<Int>()
        var transformedOffset = 0
        originalToTransformed[0] = 0
        text.text.forEachIndexed { index, char ->
            val rendered = when (char) {
                ' ' -> "·"
                '\t' -> "→   "
                '\n' -> "↵\n"
                '\r' -> "␍"
                else -> char.toString()
            }
            rendered.forEach { visible ->
                transformedToOriginal += index
                output.append(visible)
                transformedOffset++
            }
            transformedToOriginal += index + 1
            originalToTransformed[index + 1] = transformedOffset
        }
        return TransformedText(
            AnnotatedString(output.toString()),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int =
                    originalToTransformed[offset.coerceIn(0, text.text.length)]
                override fun transformedToOriginal(offset: Int): Int =
                    transformedToOriginal[offset.coerceIn(0, transformedToOriginal.lastIndex)]
            },
        )
    }
}
