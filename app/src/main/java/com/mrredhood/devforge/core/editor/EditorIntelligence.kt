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
    KOTLIN, JAVA, JAVASCRIPT, TYPESCRIPT, PYTHON, GO, RUST, C_LIKE,
    CSHARP, SWIFT, PHP, RUBY, LUA, SCALA, GROOVY, R, PERL, HASKELL, ELIXIR, MATLAB,
    OBJECTIVE_C, D, FSHARP, VISUAL_BASIC, JULIA, ZIG, NIM, CLOJURE, GDSCRIPT, SOLIDITY,
    PASCAL, FORTRAN, COBOL, PROLOG, OCAML, VHDL, VERILOG,
    JSON, XML, HTML, CSS, SCSS, LESS, SQL, GRAPHQL, YAML, TOML, HCL, SHELL, POWERSHELL,
    DOCKERFILE, MAKEFILE, MARKDOWN, DART, PLAIN;

    companion object {
        fun detect(name: String): EditorLanguage {
            val base = name.substringAfterLast('/').trim()
            return when {
                base.endsWith(".kt", true) || base.endsWith(".kts", true) -> KOTLIN
                base.endsWith(".java", true) -> JAVA
                base.endsWith(".js", true) || base.endsWith(".jsx", true) || base.endsWith(".mjs", true) || base.endsWith(".cjs", true) -> JAVASCRIPT
                base.endsWith(".ts", true) || base.endsWith(".tsx", true) || base.endsWith(".mts", true) || base.endsWith(".cts", true) -> TYPESCRIPT
                base.endsWith(".py", true) -> PYTHON
                base.endsWith(".go", true) -> GO
                base.endsWith(".rs", true) -> RUST
                base.endsWith(".cs", true) -> CSHARP
                base.endsWith(".swift", true) -> SWIFT
                base.endsWith(".php", true) -> PHP
                base.endsWith(".rb", true) || base.endsWith(".rake", true) || base.equals("Gemfile", true) -> RUBY
                base.endsWith(".lua", true) -> LUA
                base.endsWith(".scala", true) || base.endsWith(".sc", true) -> SCALA
                base.endsWith(".groovy", true) || base.endsWith(".gradle", true) -> GROOVY
                base.equals("R", true) || base.endsWith(".r", true) -> R
                base.endsWith(".pl", true) || base.endsWith(".pm", true) || base.endsWith(".pod", true) -> PERL
                base.endsWith(".hs", true) || base.endsWith(".lhs", true) -> HASKELL
                base.endsWith(".ex", true) || base.endsWith(".exs", true) -> ELIXIR
                base.endsWith(".m", true) || base.endsWith(".matlab", true) -> MATLAB
                base.endsWith(".mm", true) -> OBJECTIVE_C
                base.endsWith(".d", true) -> D
                base.endsWith(".fs", true) || base.endsWith(".fsx", true) || base.endsWith(".fsi", true) -> FSHARP
                base.endsWith(".vb", true) || base.endsWith(".vbs", true) -> VISUAL_BASIC
                base.endsWith(".jl", true) -> JULIA
                base.endsWith(".zig", true) -> ZIG
                base.endsWith(".nim", true) || base.endsWith(".nims", true) -> NIM
                base.endsWith(".clj", true) || base.endsWith(".cljs", true) || base.endsWith(".cljc", true) -> CLOJURE
                base.endsWith(".gd", true) -> GDSCRIPT
                base.endsWith(".sol", true) -> SOLIDITY
                base.endsWith(".pas", true) || base.endsWith(".pp", true) -> PASCAL
                base.endsWith(".f", true) || base.endsWith(".for", true) || base.endsWith(".f77", true) || base.endsWith(".f90", true) || base.endsWith(".f95", true) || base.endsWith(".f03", true) || base.endsWith(".f08", true) -> FORTRAN
                base.endsWith(".cob", true) || base.endsWith(".cbl", true) -> COBOL
                base.endsWith(".prolog", true) || base.endsWith(".pro", true) -> PROLOG
                base.endsWith(".ml", true) || base.endsWith(".mli", true) -> OCAML
                base.endsWith(".vhd", true) || base.endsWith(".vhdl", true) -> VHDL
                base.endsWith(".v", true) || base.endsWith(".sv", true) || base.endsWith(".svh", true) -> VERILOG
                base.endsWith(".c", true) || base.endsWith(".h", true) || base.endsWith(".cpp", true) ||
                    base.endsWith(".cc", true) || base.endsWith(".cxx", true) || base.endsWith(".hpp", true) ||
                    base.endsWith(".hh", true) || base.endsWith(".hxx", true) -> C_LIKE
                base.endsWith(".json", true) || base.endsWith(".jsonc", true) -> JSON
                base.endsWith(".xml", true) || base.endsWith(".xsd", true) || base.endsWith(".xsl", true) || base.endsWith(".xslt", true) -> XML
                base.endsWith(".html", true) || base.endsWith(".htm", true) || base.endsWith(".xhtml", true) -> HTML
                base.endsWith(".css", true) -> CSS
                base.endsWith(".scss", true) || base.endsWith(".sass", true) -> SCSS
                base.endsWith(".less", true) -> LESS
                base.endsWith(".sql", true) -> SQL
                base.endsWith(".graphql", true) || base.endsWith(".gql", true) -> GRAPHQL
                base.endsWith(".yaml", true) || base.endsWith(".yml", true) -> YAML
                base.endsWith(".toml", true) -> TOML
                base.endsWith(".hcl", true) || base.endsWith(".tf", true) || base.endsWith(".tfvars", true) -> HCL
                base.endsWith(".sh", true) || base.endsWith(".bash", true) || base.endsWith(".zsh", true) || base.endsWith(".fish", true) -> SHELL
                base.endsWith(".ps1", true) || base.endsWith(".psm1", true) -> POWERSHELL
                base.equals("Dockerfile", true) || base.startsWith("Dockerfile.", true) -> DOCKERFILE
                base.equals("Makefile", true) || base.equals("makefile", true) || base.endsWith(".mk", true) -> MAKEFILE
                base.endsWith(".md", true) || base.endsWith(".markdown", true) || base.endsWith(".mdx", true) -> MARKDOWN
                base.endsWith(".dart", true) -> DART
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
            val options = if (language in CASE_INSENSITIVE_KEYWORD_LANGUAGES) setOf(RegexOption.IGNORE_CASE) else emptySet()
            Regex("\\b" + Regex.escape(keyword) + "\\b", options).findAll(text.text).forEach { match ->
                builder.addStyle(SpanStyle(color = keywordColor), match.range.first, match.range.last + 1)
            }
        }
        NUMBER_REGEX.findAll(text.text).forEach { match ->
            builder.addStyle(SpanStyle(color = numberColor), match.range.first, match.range.last + 1)
        }
        STRING_REGEX.findAll(text.text).forEach { match ->
            builder.addStyle(SpanStyle(color = stringColor), match.range.first, match.range.last + 1)
        }
        (COMMENT_REGEX_BY_LANGUAGE[language] ?: COMMENT_REGEX).findAll(text.text).forEach { match ->
            builder.addStyle(SpanStyle(color = commentColor), match.range.first, match.range.last + 1)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    private companion object {
        val STRING_REGEX = Regex("""("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')""")
        val COMMENT_REGEX = Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/|#[^\\n]*")
        val NUMBER_REGEX = Regex("\\b(?:0x[0-9A-Fa-f]+|\\d+(?:\\.\\d+)?)\\b")
        val COMMENT_REGEX_BY_LANGUAGE = mapOf(
            EditorLanguage.PYTHON to Regex("#[^\\n]*"),
            EditorLanguage.SHELL to Regex("#[^\\n]*"),
            EditorLanguage.POWERSHELL to Regex("#[^\\n]*"),
            EditorLanguage.YAML to Regex("#[^\\n]*"),
            EditorLanguage.TOML to Regex("#[^\\n]*"),
            EditorLanguage.JULIA to Regex("#[^\\n]*"),
            EditorLanguage.NIM to Regex("#[^\\n]*"),
            EditorLanguage.GRAPHQL to Regex("#[^\\n]*"),
            EditorLanguage.DOCKERFILE to Regex("#[^\\n]*"),
            EditorLanguage.MAKEFILE to Regex("#[^\\n]*"),
            EditorLanguage.R to Regex("#[^\\n]*"),
            EditorLanguage.RUBY to Regex("#[^\\n]*"),
            EditorLanguage.PERL to Regex("#[^\\n]*"),
            EditorLanguage.PROLOG to Regex("%[^\\n]*"),
            EditorLanguage.LUA to Regex("--[^\\n]*|--\\[\\[[\\s\\S]*?\\]\\]"),
            EditorLanguage.HASKELL to Regex("--[^\\n]*|\\{-[\\s\\S]*?-\\}"),
            EditorLanguage.MATLAB to Regex("%[^\\n]*"),
            EditorLanguage.VISUAL_BASIC to Regex("'[^\\n]*|REM(?:\\s+.*)?$", RegexOption.MULTILINE),
            EditorLanguage.OCAML to Regex("\\(\\*[\\s\\S]*?\\*\\)"),
            EditorLanguage.FSHARP to Regex("//[^\\n]*|\\(\\*[\\s\\S]*?\\*\\)"),
            EditorLanguage.PASCAL to Regex("//[^\\n]*|\\{[^\\}]*\\}|\\(\\*[\\s\\S]*?\\*\\)"),
            EditorLanguage.FORTRAN to Regex("![^\\n]*"),
            EditorLanguage.COBOL to Regex("\\*>[^\\n]*"),
            EditorLanguage.VHDL to Regex("--[^\\n]*"),
            EditorLanguage.SQL to Regex("--[^\\n]*|/\\*[\\s\\S]*?\\*/"),
            EditorLanguage.HCL to Regex("#[^\\n]*|//[^\\n]*|/\\*[\\s\\S]*?\\*/"),
            EditorLanguage.CSS to Regex("/\\*[\\s\\S]*?\\*/"),
            EditorLanguage.SCSS to Regex("/\\*[\\s\\S]*?\\*/|//[^\\n]*"),
            EditorLanguage.LESS to Regex("/\\*[\\s\\S]*?\\*/|//[^\\n]*"),
            EditorLanguage.HTML to Regex("<!--[\\s\\S]*?-->"),
            EditorLanguage.XML to Regex("<!--[\\s\\S]*?-->"),
            EditorLanguage.JSON to Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/"),
            EditorLanguage.MARKDOWN to Regex("<!--[\\s\\S]*?-->|^\\s*>.*$", RegexOption.MULTILINE),
        )
        val CASE_INSENSITIVE_KEYWORD_LANGUAGES = setOf(
            EditorLanguage.SQL,
            EditorLanguage.VISUAL_BASIC,
            EditorLanguage.FORTRAN,
            EditorLanguage.COBOL,
            EditorLanguage.POWERSHELL,
            EditorLanguage.DOCKERFILE,
        )
        val KEYWORDS = mapOf(
            EditorLanguage.KOTLIN to setOf("class","interface","object","fun","val","var","const","return","if","else","when","for","while","in","is","as","import","package","public","private","protected","internal","override","suspend","data","sealed","open","true","false","null"),
            EditorLanguage.JAVA to setOf("class","interface","enum","return","if","else","for","while","switch","case","break","continue","new","this","extends","implements","import","package","public","private","protected","static","final","abstract","try","catch","finally","throw","throws","true","false","null"),
            EditorLanguage.JAVASCRIPT to setOf("function","return","if","else","for","while","const","let","var","new","this","class","extends","import","from","export","default","async","await","try","catch","throw","true","false","null","undefined"),
            EditorLanguage.TYPESCRIPT to setOf("function","return","if","else","for","while","const","let","var","new","this","class","interface","type","extends","implements","import","from","export","default","async","await","public","private","readonly","true","false","null","undefined"),
            EditorLanguage.PYTHON to setOf("def","return","if","elif","else","for","while","in","is","import","from","class","try","except","finally","raise","async","await","with","as","lambda","yield","True","False","None"),
            EditorLanguage.GO to setOf("package","import","func","return","if","else","for","range","switch","case","type","struct","interface","go","defer","map","chan","var","const","true","false","nil"),
            EditorLanguage.RUST to setOf("fn","let","mut","const","struct","enum","trait","impl","match","if","else","for","while","loop","use","mod","pub","crate","self","Self","return","async","await","true","false"),
            EditorLanguage.C_LIKE to setOf("int","char","float","double","void","bool","class","struct","enum","namespace","using","return","if","else","for","while","switch","case","break","continue","const","static","public","private","protected","true","false","nullptr"),
            EditorLanguage.CSHARP to setOf("using","namespace","class","struct","record","interface","enum","public","private","protected","internal","static","readonly","async","await","new","return","if","else","for","foreach","while","switch","case","try","catch","finally","throw","true","false","null"),
            EditorLanguage.SWIFT to setOf("import","class","struct","enum","protocol","extension","func","let","var","if","else","for","while","switch","case","guard","return","public","private","internal","static","async","await","true","false","nil"),
            EditorLanguage.PHP to setOf("<?php","class","interface","trait","function","public","private","protected","static","const","return","if","else","foreach","while","namespace","use","new","echo","true","false","null"),
            EditorLanguage.RUBY to setOf("class","module","def","end","if","elsif","else","unless","case","when","while","until","do","yield","require","include","attr_reader","attr_writer","return","true","false","nil"),
            EditorLanguage.LUA to setOf("local","function","end","if","then","elseif","else","for","while","repeat","until","in","do","return","require","true","false","nil"),
            EditorLanguage.SCALA to setOf("package","import","class","object","trait","case","match","def","val","var","type","extends","with","if","else","for","while","yield","implicit","given","using","true","false","null"),
            EditorLanguage.GROOVY to setOf("class","interface","trait","def","return","if","else","for","while","switch","case","new","import","package","public","private","protected","static","final","true","false","null"),
            EditorLanguage.R to setOf("function","if","else","for","while","repeat","in","next","break","library","require","return","TRUE","FALSE","NULL","NA"),
            EditorLanguage.PERL to setOf("my","our","sub","use","package","if","elsif","else","unless","for","foreach","while","until","return","use","strict","warnings","undef"),
            EditorLanguage.HASKELL to setOf("module","where","import","data","type","newtype","class","instance","deriving","let","in","case","of","if","then","else","do","where"),
            EditorLanguage.ELIXIR to setOf("def","defp","defmodule","defmacro","fn","case","cond","if","unless","do","end","when","alias","import","require","use","receive","true","false","nil"),
            EditorLanguage.MATLAB to setOf("function","if","elseif","else","for","while","switch","case","otherwise","end","global","persistent","return","classdef","properties","methods","true","false"),
            EditorLanguage.OBJECTIVE_C to setOf("import","@interface","@implementation","@protocol","@property","@synthesize","@end","class","self","nil","return","if","else","for","while","switch","case","typedef","struct","enum"),
            EditorLanguage.D to setOf("module","import","class","struct","interface","enum","alias","auto","const","immutable","shared","void","int","bool","string","return","if","else","for","foreach","while","switch","case","try","catch","throw","true","false","null"),
            EditorLanguage.FSHARP to setOf("module","namespace","open","type","let","mutable","member","interface","class","inherit","match","with","function","fun","if","then","else","for","while","do","yield","async","task","true","false"),
            EditorLanguage.VISUAL_BASIC to setOf("Module","Class","Structure","Interface","Enum","Sub","Function","Property","Dim","As","If","Then","Else","For","Each","Next","While","Do","Loop","Select","Case","Return","Imports","Namespace","Public","Private","Protected","True","False","Nothing"),
            EditorLanguage.JULIA to setOf("function","end","if","elseif","else","for","while","begin","struct","mutable","module","using","import","export","return","let","local","global","try","catch","finally","true","false","nothing","missing"),
            EditorLanguage.ZIG to setOf("const","var","fn","pub","export","extern","struct","enum","union","error","if","else","for","while","switch","break","continue","return","defer","errdefer","try","catch","orelse","true","false","null","undefined"),
            EditorLanguage.NIM to setOf("proc","func","method","iterator","template","macro","type","object","ref","enum","let","var","const","if","elif","else","for","while","case","of","try","except","finally","return","import","from","include","true","false","nil"),
            EditorLanguage.CLOJURE to setOf("def","defn","fn","let","if","do","loop","recur","when","cond","case","ns","require","use","import","new","true","false","nil"),
            EditorLanguage.GDSCRIPT to setOf("extends","class_name","class","func","static","var","const","signal","enum","if","elif","else","for","while","match","break","continue","return","await","preload","load","true","false","null"),
            EditorLanguage.SOLIDITY to setOf("pragma","solidity","contract","interface","library","struct","enum","event","function","modifier","constructor","fallback","receive","public","private","internal","external","memory","storage","calldata","mapping","address","payable","returns","return","if","else","for","while","require","revert","assert","true","false"),
            EditorLanguage.PASCAL to setOf("program","unit","interface","implementation","type","record","class","object","function","procedure","begin","end","if","then","else","for","to","downto","while","repeat","until","case","of","uses","var","const","true","false","nil"),
            EditorLanguage.FORTRAN to setOf("program","module","subroutine","function","end","implicit","none","integer","real","double","complex","logical","character","type","allocate","deallocate","do","if","then","else","elseif","select","case","where","use","call","return"),
            EditorLanguage.COBOL to setOf("identification","environment","data","procedure","division","section","paragraph","perform","move","display","accept","compute","if","else","end-if","evaluate","when","read","write","open","close","stop","run"),
            EditorLanguage.PROLOG to setOf("module","use_module","dynamic","public","consult","assertz","retract","findall","bagof","setof","is","not","true","fail"),
            EditorLanguage.OCAML to setOf("let","rec","and","in","fun","function","match","with","type","module","open","include","class","object","method","inherit","if","then","else","for","to","downto","while","do","done","true","false"),
            EditorLanguage.VHDL to setOf("library","use","entity","architecture","begin","end","signal","variable","constant","process","if","then","else","elsif","case","when","loop","for","generate","component","port","map"),
            EditorLanguage.VERILOG to setOf("module","endmodule","input","output","inout","wire","reg","logic","assign","always","initial","begin","end","if","else","case","for","while","posedge","negedge","parameter","localparam"),
            EditorLanguage.SCSS to setOf("mixin","include","extend","function","if","else","for","each","while","return","color","background","display","position","margin","padding","width","height","font","grid","flex","border"),
            EditorLanguage.LESS to setOf("import","media","keyframes","color","background","display","position","margin","padding","width","height","font","grid","flex","border"),
            EditorLanguage.GRAPHQL to setOf("query","mutation","subscription","fragment","schema","type","interface","union","enum","input","scalar","directive","extend","implements","on","true","false","null"),
            EditorLanguage.YAML to setOf("true","false","null"),
            EditorLanguage.TOML to setOf("true","false"),
            EditorLanguage.HCL to setOf("resource","data","provider","module","variable","output","terraform","locals","for_each","count","depends_on","true","false","null"),
            EditorLanguage.POWERSHELL to setOf("function","param","begin","process","end","if","elseif","else","foreach","for","while","do","switch","try","catch","finally","throw","return","class","using","true","false","null"),
            EditorLanguage.DOCKERFILE to setOf("FROM","RUN","CMD","LABEL","EXPOSE","ENV","ADD","COPY","ENTRYPOINT","VOLUME","USER","WORKDIR","ARG","ONBUILD","STOPSIGNAL","HEALTHCHECK","SHELL"),
            EditorLanguage.MAKEFILE to setOf("include","define","endef","if","ifdef","ifndef","ifeq","ifneq","else","endif","override","export","unexport","private","vpath"),
            EditorLanguage.JSON to emptySet(),
            EditorLanguage.XML to setOf("xml","version","encoding"),
            EditorLanguage.HTML to setOf("html","head","body","div","span","script","style","class","id","href","src","meta","title"),
            EditorLanguage.CSS to setOf("color","background","display","position","margin","padding","width","height","font","grid","flex","border"),
            EditorLanguage.SQL to setOf("select","from","where","join","left","right","inner","outer","group","by","order","having","insert","into","values","update","set","delete","create","table","alter","drop","and","or","not","null"),
            EditorLanguage.YAML to emptySet(),
            EditorLanguage.SHELL to setOf("if","then","else","fi","for","in","do","done","case","esac","function","export","local","return"),
            EditorLanguage.MARKDOWN to emptySet(),
            EditorLanguage.DART to setOf("class","mixin","extension","enum","typedef","final","const","var","late","void","int","double","String","bool","return","if","else","for","while","switch","case","import","export","async","await","true","false","null"),
            EditorLanguage.PLAIN to emptySet(),
        )
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
            originalToTransformed[index + 1] = transformedOffset
        }
        transformedToOriginal += text.text.length
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
