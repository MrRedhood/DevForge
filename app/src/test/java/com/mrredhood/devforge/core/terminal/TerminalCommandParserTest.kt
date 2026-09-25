package com.mrredhood.devforge.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalCommandParserTest {
    @Test
    fun parsesAnyAvailableLinuxCommandForAiTools() {
        assertEquals(
            TerminalExecutable.SHELL,
            TerminalCommandParser.parseToolCommand("env", "", 10_000L, null).executable,
        )
        assertEquals(
            listOf("-c", "which sh && getprop ro.build.version.release | head -n 1"),
            TerminalCommandParser.parseToolCommand(
                "which sh && getprop ro.build.version.release | head -n 1",
                "",
                10_000L,
                null,
            ).args,
        )
    }

    @Test
    fun parsesCommandsBuiltin() {
        val parsed = TerminalCommandParser.parse("commands", "", 10_000L, "session")
        assertEquals(true, parsed is TerminalParsedCommand.Commands)
    }

    @Test
    fun exposesExactly150DiscoverableCommands() {
        assertEquals(150, TerminalCommandCatalog.commands.size)
        assertEquals(150, TerminalCommandCatalog.commands.toSet().size)
    }

    @Test
    fun leavesLinuxShellOperatorsOnInteractiveShellPath() {
        val parsed = TerminalCommandParser.parse("printf 'a' | tr a b", "", 10_000L, "session")
        assertEquals(true, parsed is TerminalParsedCommand.Shell)
    }
}
