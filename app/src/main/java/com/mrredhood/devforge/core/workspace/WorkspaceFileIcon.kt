package com.mrredhood.devforge.core.workspace

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp

private data class LanguageIconSpec(
    val icon: ImageVector,
    val tint: Color,
    val description: String,
)

private object LanguageIconPack {
    private fun code(tint: Color, description: String) = LanguageIconSpec(Icons.Outlined.Code, tint, description)
    private fun data(tint: Color, description: String) = LanguageIconSpec(Icons.Outlined.DataObject, tint, description)
    private fun web(tint: Color, description: String) = LanguageIconSpec(Icons.Outlined.Language, tint, description)
    private fun terminal(tint: Color, description: String) = LanguageIconSpec(Icons.Outlined.Terminal, tint, description)

    fun forName(name: String): LanguageIconSpec {
        val base = name.substringAfterLast('/').trim()
        val lower = base.lowercase()
        val ext = lower.substringAfterLast('.', "").takeIf { lower.contains('.') }
        return when {
            lower == "dockerfile" || lower.startsWith("dockerfile.") -> terminal(Color(0xFF2496ED), "Dockerfile")
            lower == "makefile" || lower.endsWith(".mk") -> terminal(Color(0xFF5C6BC0), "Makefile")
            lower == ".env" || lower.startsWith(".env.") -> data(Color(0xFFE8B339), "Environment configuration")
            lower == ".gitignore" || lower == ".gitattributes" || lower == ".gitmodules" -> code(Color(0xFFF05032), "Git file")
            lower == "readme" || lower.startsWith("readme.") || lower == "license" || lower.startsWith("license.") -> LanguageIconSpec(Icons.Outlined.Description, Color(0xFF9AA3B2), "Documentation")
            ext == null -> code(Color(0xFF9AA3B2), "Source file")
            ext in setOf("py","rb","php","pl","pm","r","jl","hs","lhs","ex","exs","lua","scala","sc","groovy","gradle","fs","fsx","fsi","vb","vbs","d","nim","nims","clj","cljs","cljc","gd","sol","pas","pp","f","for","f77","f90","f95","f03","f08","cob","cbl","pro","prolog","ml","mli","swift") -> code(languageTint(ext), languageName(ext))
            ext in setOf("kt","kts","java","cs","c","h","cpp","cc","cxx","hpp","hh","hxx","m","mm","zig","v","sv","svh") -> code(languageTint(ext), languageName(ext))
            ext in setOf("js","jsx","mjs","cjs","ts","tsx","mts","cts","json","jsonc") -> data(languageTint(ext), languageName(ext))
            ext in setOf("html","htm","xhtml","xml","xsd","xsl","xslt","vue","svelte") -> web(languageTint(ext), languageName(ext))
            ext in setOf("css","scss","sass","less") -> web(languageTint(ext), languageName(ext))
            ext in setOf("sh","bash","zsh","fish","ps1","psm1") -> terminal(languageTint(ext), languageName(ext))
            ext in setOf("sql","graphql","gql") -> LanguageIconSpec(Icons.Outlined.Storage, languageTint(ext), languageName(ext))
            ext in setOf("yaml","yml","toml","hcl","tf","tfvars","ini","cfg","conf","properties") -> data(languageTint(ext), languageName(ext))
            ext in setOf("md","markdown","mdx","txt","log") -> LanguageIconSpec(Icons.Outlined.Description, languageTint(ext), languageName(ext))
            ext in setOf("csv","tsv") -> LanguageIconSpec(Icons.Outlined.TableChart, languageTint(ext), languageName(ext))
            ext in setOf("png","jpg","jpeg","gif","webp","bmp","ico","svg") -> LanguageIconSpec(Icons.Outlined.Image, languageTint(ext), languageName(ext))
            ext in setOf("mp3","wav","ogg","m4a","flac") -> LanguageIconSpec(Icons.Outlined.Audiotrack, languageTint(ext), languageName(ext))
            ext in setOf("mp4","mov","mkv","webm","avi") -> LanguageIconSpec(Icons.Outlined.Movie, languageTint(ext), languageName(ext))
            ext in setOf("zip","7z","rar","tar","gz","bz2","xz") -> LanguageIconSpec(Icons.Outlined.Archive, languageTint(ext), languageName(ext))
            ext == "pdf" -> LanguageIconSpec(Icons.Outlined.PictureAsPdf, Color(0xFFE53935), "PDF document")
            ext == "lock" -> LanguageIconSpec(Icons.Outlined.Settings, Color(0xFF8D6E63), "Lock file")
            else -> code(Color(0xFF9AA3B2), "Source file")
        }
    }

    private fun languageTint(ext: String): Color = when (ext) {
        "kt", "kts" -> Color(0xFF9B7CFF)
        "java" -> Color(0xFFFFA726)
        "js", "jsx", "mjs", "cjs" -> Color(0xFFF7DF1E)
        "ts", "tsx", "mts", "cts" -> Color(0xFF4E9FE6)
        "py" -> Color(0xFF6FA8DC)
        "go" -> Color(0xFF26C6DA)
        "rs" -> Color(0xFFFFB86C)
        "go" -> Color(0xFF26C6DA)
        "rs" -> Color(0xFFFFB86C)
        "cs" -> Color(0xFFAB6BD6)
        "swift" -> Color(0xFFFF7043)
        "php" -> Color(0xFF8E8CC4)
        "rb" -> Color(0xFFEF5350)
        "c", "h", "cpp", "cc", "cxx", "hpp", "hh", "hxx", "mm" -> Color(0xFF5B9BD5)
        "html", "htm", "xhtml" -> Color(0xFFFF7043)
        "css", "scss", "sass", "less" -> Color(0xFF42A5F5)
        "json", "jsonc", "yaml", "yml", "toml", "hcl", "tf", "tfvars" -> Color(0xFF81C784)
        "sql", "graphql", "gql" -> Color(0xFF64B5F6)
        "sh", "bash", "zsh", "fish", "ps1", "psm1" -> Color(0xFF66BB6A)
        "md", "markdown", "mdx", "txt", "log" -> Color(0xFF90A4AE)
        "svg", "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico" -> Color(0xFF26A69A)
        "mp3", "wav", "ogg", "m4a", "flac" -> Color(0xFFBA68C8)
        "mp4", "mov", "mkv", "webm", "avi" -> Color(0xFFEC407A)
        else -> Color(0xFF9AA3B2)
    }

    private fun languageName(ext: String): String = when (ext) {
        "kt","kts" -> "Kotlin"; "java" -> "Java"; "js","jsx","mjs","cjs" -> "JavaScript"
        "ts","tsx","mts","cts" -> "TypeScript"; "py" -> "Python"; "go" -> "Go"; "rs" -> "Rust"
        "cs" -> "C#"; "swift" -> "Swift"; "php" -> "PHP"; "rb" -> "Ruby"; "lua" -> "Lua"
        "scala","sc" -> "Scala"; "groovy","gradle" -> "Groovy"; "r" -> "R"; "pl","pm" -> "Perl"
        "hs","lhs" -> "Haskell"; "ex","exs" -> "Elixir"; "m" -> "MATLAB"; "mm" -> "Objective-C++"
        "d" -> "D"; "fs","fsx","fsi" -> "F#"; "vb","vbs" -> "Visual Basic"; "jl" -> "Julia"
        "zig" -> "Zig"; "nim","nims" -> "Nim"; "clj","cljs","cljc" -> "Clojure"; "gd" -> "GDScript"
        "sol" -> "Solidity"; "pas","pp" -> "Pascal"; "f","for","f77","f90","f95","f03","f08" -> "Fortran"
        "cob","cbl" -> "COBOL"; "pro","prolog" -> "Prolog"; "ml","mli" -> "OCaml"
        "vhd","vhdl" -> "VHDL"; "v","sv","svh" -> "Verilog"; "c","h","cpp","cc","cxx","hpp","hh","hxx" -> "C/C++"
        "json","jsonc" -> "JSON"; "html","htm","xhtml" -> "HTML"; "xml","xsd","xsl","xslt" -> "XML"
        "css" -> "CSS"; "scss","sass" -> "SCSS"; "less" -> "Less"; "sql" -> "SQL"; "graphql","gql" -> "GraphQL"
        "yaml","yml" -> "YAML"; "toml" -> "TOML"; "hcl","tf","tfvars" -> "HCL/Terraform"; "sh","bash","zsh","fish" -> "Shell"
        "ps1","psm1" -> "PowerShell"; "md","markdown","mdx" -> "Markdown"; "txt","log" -> "Text"; "csv","tsv" -> "Table"
        else -> ext.uppercase()
    }
}

@Composable
fun WorkspaceLanguageIcon(
    name: String,
    modifier: Modifier = Modifier,
) {
    ExtensionThemeIcon(
        name = name,
        isFolder = false,
        expanded = false,
        modifier = modifier.size(26.dp),
    ) {
        val spec = remember(name) { LanguageIconPack.forName(name) }
        Icon(
            imageVector = spec.icon,
            contentDescription = spec.description,
            modifier = modifier.size(26.dp),
            tint = spec.tint.copy(alpha = 0.96f),
        )
    }
}

@Composable
fun WorkspaceFolderIcon(
    name: String,
    expanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    ExtensionThemeIcon(
        name = name,
        isFolder = true,
        expanded = expanded,
        modifier = modifier.size(26.dp),
    ) {
        Icon(
            imageVector = if (expanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
            contentDescription = if (expanded) "Open folder" else "Folder",
            modifier = modifier.size(26.dp),
        )
    }
}
