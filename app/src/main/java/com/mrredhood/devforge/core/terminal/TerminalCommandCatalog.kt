package com.mrredhood.devforge.core.terminal

/** Discoverability catalog for the real Android/Linux shell. */
object TerminalCommandCatalog {
    val groups: List<Pair<String, List<String>>> = listOf(
        "Navigation" to listOf("cd", "pwd", "ls", "dir", "find", "tree", "du", "df", "stat", "readlink", "realpath", "basename", "dirname", "file"),
        "Read/text" to listOf("cat", "head", "tail", "less", "more", "sed", "awk", "grep", "egrep", "fgrep", "cut", "tr", "sort", "uniq", "wc"),
        "Text/process" to listOf("xargs", "tee", "paste", "join", "comm", "nl", "fold", "expand", "unexpand", "strings", "od", "printf", "echo", "seq", "fmt"),
        "Files" to listOf("mkdir", "rmdir", "touch", "rm", "cp", "mv", "ln", "chmod", "chown", "rename", "install", "shred", "truncate", "mktemp", "dd"),
        "Archives/checksums" to listOf("tar", "gzip", "gunzip", "bzip2", "bunzip2", "xz", "unxz", "zip", "unzip", "md5sum", "sha1sum", "sha256sum", "sha512sum", "cksum", "cmp"),
        "Environment/system" to listOf("env", "printenv", "export", "unset", "which", "type", "command", "id", "whoami", "uname", "hostname", "date", "uptime", "getprop", "setprop", "sysctl"),
        "Processes/Android" to listOf("ps", "top", "pgrep", "pidof", "kill", "killall", "pkill", "nice", "renice", "nohup", "time", "sleep", "logcat", "toybox", "dumpsys"),
        "Network" to listOf("ping", "ping6", "nslookup", "dig", "host", "ip", "ifconfig", "route", "arp", "netstat", "ss", "telnet", "curl", "wget", "ftp"),
        "Build/development" to listOf("git", "gradle", "java", "javac", "kotlinc", "kotlin", "jar", "javap", "adb", "cmake", "ninja", "make", "python", "python3", "node"),
        "Project/utilities" to listOf("npm", "npx", "yarn", "pnpm", "bun", "ruby", "perl", "lua", "go", "rustc", "cargo", "clang", "gcc", "ld", "sqlite3"),
    )
    val commands: List<String> = groups.flatMap { it.second }
}
