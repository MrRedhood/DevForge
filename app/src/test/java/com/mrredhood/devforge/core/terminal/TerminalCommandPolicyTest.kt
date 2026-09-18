package com.mrredhood.devforge.core.terminal

import org.junit.Assert.assertThrows
import org.junit.Test

class TerminalCommandPolicyTest {
    @Test
    fun rejectsPathTraversal() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommand(TerminalExecutable.CAT, listOf("../../outside.txt")).also(TerminalCommandPolicy::validate)
        }
    }

    @Test
    fun rejectsAbsolutePathAndOptionInjection() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommand(TerminalExecutable.CAT, listOf("/data/local/tmp/secret")).also(TerminalCommandPolicy::validate)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommand(TerminalExecutable.GREP, listOf("--file=/data/local/tmp/patterns", "needle")).also(TerminalCommandPolicy::validate)
        }
    }

    @Test
    fun rejectsAbsoluteWorkingDirectory() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommand(TerminalExecutable.PWD, workingDirectory = "/data/local/tmp").also(TerminalCommandPolicy::validate)
        }
    }

    @Test
    fun rejectsUnsafeWorkingDirectorySegmentsAndControlSeparators() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommand(TerminalExecutable.PWD, workingDirectory = ".git").also(TerminalCommandPolicy::validate)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommand(TerminalExecutable.ECHO, listOf("safe\nunsafe")).also(TerminalCommandPolicy::validate)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommand(TerminalExecutable.ECHO, listOf("safe\u0000unsafe")).also(TerminalCommandPolicy::validate)
        }
    }

    @Test
    fun rejectsBounds() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommand(TerminalExecutable.ECHO, List(TerminalCommandPolicy.MAX_ARGS + 1) { "x" }).also(TerminalCommandPolicy::validate)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommand(TerminalExecutable.PWD, timeoutMs = TerminalCommandPolicy.MAX_TIMEOUT_MS + 1).also(TerminalCommandPolicy::validate)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommand(TerminalExecutable.ECHO, listOf("x".repeat(TerminalCommandPolicy.MAX_ARG_LENGTH + 1))).also(TerminalCommandPolicy::validate)
        }
    }

    @Test
    fun acceptsSafeRelativeCommand() {
        TerminalCommand(TerminalExecutable.LS, listOf("-A", "src")).also(TerminalCommandPolicy::validate)
    }
}
