package com.mrredhood.devforge.core.ai

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(markdown: String) {
    val lines = markdown.replace("\r\n", "\n").split('\n')
    Column {
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.trimStart().startsWith("```") ) {
                val language = line.trim().removePrefix("```").trim()
                val code = buildString {
                    index++
                    while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                        append(lines[index])
                        if (index < lines.lastIndex) append('\n')
                        index++
                    }
                }.trimEnd('\n')
                CodeBlock(code, language)
                if (index < lines.lastIndex) index++
                continue
            }
            when {
                line.isBlank() -> Text(" ", fontSize = 4.sp)
                line.startsWith("### ") -> Text(
                    inlineMarkdown(line.removePrefix("### ")),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 2.dp),
                )
                line.startsWith("## ") -> Text(
                    inlineMarkdown(line.removePrefix("## ")),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 3.dp),
                )
                line.startsWith("# ") -> Text(
                    inlineMarkdown(line.removePrefix("# ")),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                line.trimStart().startsWith("- ") -> Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text("• ", fontWeight = FontWeight.Bold)
                    Text(inlineMarkdown(line.trimStart().drop(2)))
                }
                else -> Text(inlineMarkdown(line), modifier = Modifier.fillMaxWidth())
            }
            index++
        }
    }
}

@Composable
private fun CodeBlock(code: String, language: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            if (language.isNotBlank()) Text(language, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Text(code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun inlineMarkdown(value: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var index = 0
    while (index < value.length) {
        when {
            value.startsWith("**", index) -> {
                val end = value.indexOf("**", index + 2)
                if (end >= 0) {
                    builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value.substring(index + 2, end)) }
                    index = end + 2
                } else { builder.append(value[index]); index++ }
            }
            value.startsWith("~~", index) -> {
                val end = value.indexOf("~~", index + 2)
                if (end >= 0) {
                    builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(value.substring(index + 2, end)) }
                    index = end + 2
                } else { builder.append(value[index]); index++ }
            }
            value[index] == '`' -> {
                val end = value.indexOf('`', index + 1)
                if (end >= 0) {
                    builder.withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(value.substring(index + 1, end)) }
                    index = end + 1
                } else { builder.append(value[index]); index++ }
            }
            value[index] == '*' || value[index] == '_' -> {
                val marker = value[index]
                val end = value.indexOf(marker, index + 1)
                if (end > index + 1) {
                    builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(value.substring(index + 1, end)) }
                    index = end + 1
                } else { builder.append(marker); index++ }
            }
            else -> { builder.append(value[index]); index++ }
        }
    }
    return builder.toAnnotatedString()
}
