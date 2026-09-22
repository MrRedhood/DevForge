package com.mrredhood.devforge.core.ai

/**
 * Shared command adapter for the agent engine. Commands are data first: side effects are
 * represented as proposals and must still pass DevForge's capability/approval gateway.
 */
object AgentCommandBridge {
    fun parse(input: String): AICommandInvocation? = AICommandRegistry.parse(input)

    fun catalog(): List<AICommandDefinition> = AgentCommandCatalog.commands

    fun prepare(input: String): AgentCommandResult {
        val invocation = AICommandRegistry.parse(input) ?: return AgentCommandResult.NotACommand
        return AgentCommandResult.Ready(invocation.command, invocation.toAgentInstruction())
    }
}

sealed interface AgentCommandResult {
    data object NotACommand : AgentCommandResult
    data class Ready(
        val command: AICommandDefinition,
        val instruction: String,
    ) : AgentCommandResult
}
