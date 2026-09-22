package com.mrredhood.devforge.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalCommandParserTest {
    @Test
    fun parsesExpandedReadOnlyCommandsForAiTools() {
        assertEquals(
            TerminalExecutable.ENV,
            TerminalCommandParser.parseToolCommand("env", "", 10_000L, null).executable,
        )
        assertEquals(
            TerminalExecutable.WHICH,
            TerminalCommandParser.parseToolCommand("which sh", "", 10_000L, null).executable,
        )
        assertEquals(
            TerminalExecutable.GETPROP,
            TerminalCommandParser.parseToolCommand("getprop ro.build.version.release", "", 10_000L, null).executable,
        )
    }

    @Test
    fun leavesLinuxShellOperatorsOnInteractiveShellPath() {
        val parsed = TerminalCommandParser.parse("printf 'a' | tr a b", "", 10_000L, "session")
        assertEquals(true, parsed is TerminalParsedCommand.Shell)
    }
}
