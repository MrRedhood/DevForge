package com.mrredhood.devforge.core.agent

import java.math.BigDecimal
import java.math.MathContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AgentUtilityToolProvider {
    fun registerAll(registry: AgentToolRegistry): AgentToolRegistry = registry
        .register(CalculateTool())
        .register(CurrentTimeTool())

    private inner class CalculateTool : AgentTool {
        override val definition = AgentToolDefinition(
            AgentToolId.CALCULATE,
            "Evaluate bounded arithmetic with +, -, *, /, %, unary signs and parentheses.",
            com.mrredhood.devforge.core.policy.Capability.READ_WORKSPACE,
            com.mrredhood.devforge.core.policy.RiskLevel.R0,
            false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult = try {
            val expression = JSONObject(request.argumentsJson).optString("expression").trim()
            require(expression.isNotBlank()) { "Expression cannot be empty." }
            require(expression.length <= 240) { "Expression is too long." }
            val result = ArithmeticParser(expression).parse()
            AgentToolResult.Success(
                summary = "Calculated " + expression + ".",
                output = JSONObject().put("expression", expression).put("result", result).toString(),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            AgentToolResult.Failure(error.message ?: "Calculation failed.")
        }
    }

    private inner class CurrentTimeTool : AgentTool {
        override val definition = AgentToolDefinition(
            AgentToolId.CURRENT_TIME,
            "Return current time with epoch milliseconds and an optional timezone.",
            com.mrredhood.devforge.core.policy.Capability.READ_WORKSPACE,
            com.mrredhood.devforge.core.policy.RiskLevel.R0,
            false,
        )

        override suspend fun execute(context: AgentToolContext, request: AgentToolRequest): AgentToolResult =
            withContext(Dispatchers.Default) {
                try {
                    val args = JSONObject(request.argumentsJson)
                    val zoneName = args.optString("timezone", TimeZone.getDefault().id).trim()
                        .ifBlank { TimeZone.getDefault().id }
                    val timezone = TimeZone.getTimeZone(zoneName)
                    val now = Date()
                    val formatted = SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                        Locale.US,
                    ).apply { timeZone = timezone }.format(now)
                    AgentToolResult.Success(
                        summary = "Current time in " + timezone.id + ".",
                        output = JSONObject()
                            .put("timezone", timezone.id)
                            .put("epochMillis", now.time)
                            .put("formatted", formatted)
                            .toString(),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    AgentToolResult.Failure(error.message ?: "Unable to determine current time.")
                }
            }
    }

    private class ArithmeticParser(private val source: String) {
        private val math = MathContext(16)
        private var index = 0

        fun parse(): String {
            val value = expression()
            skipSpaces()
            require(index == source.length) {
                "Unexpected token near '" + source.substring(index).take(20) + "'."
            }
            return value.round(math).stripTrailingZeros().toPlainString()
        }

        private fun expression(): BigDecimal {
            var value = term()
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '+' -> {
                        index++
                        value.add(term(), math)
                    }
                    '-' -> {
                        index++
                        value.subtract(term(), math)
                    }
                    else -> return value
                }
            }
        }

        private fun term(): BigDecimal {
            var value = factor()
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '*' -> {
                        index++
                        value.multiply(factor(), math)
                    }
                    '/' -> {
                        index++
                        val divisor = factor()
                        require(divisor.compareTo(BigDecimal.ZERO) != 0) { "Division by zero." }
                        value.divide(divisor, math)
                    }
                    '%' -> {
                        index++
                        val divisor = factor()
                        require(divisor.compareTo(BigDecimal.ZERO) != 0) { "Modulo by zero." }
                        value.remainder(divisor, math)
                    }
                    else -> return value
                }
            }
        }

        private fun factor(): BigDecimal {
            skipSpaces()
            return when (peek()) {
                '+' -> {
                    index++
                    factor()
                }
                '-' -> {
                    index++
                    factor().negate(math)
                }
                '(' -> {
                    index++
                    val value = expression()
                    skipSpaces()
                    require(peek() == ')') { "Missing closing parenthesis." }
                    index++
                    value
                }
                else -> number()
            }
        }

        private fun number(): BigDecimal {
            skipSpaces()
            val start = index
            var dots = 0
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) {
                if (source[index] == '.') dots++
                index++
            }
            require(index > start && dots <= 1) {
                "Expected a number near '" + source.substring(start).take(20) + "'."
            }
            return BigDecimal(source.substring(start, index), math)
        }

        private fun peek(): Char = if (index < source.length) source[index] else '\u0000'

        private fun skipSpaces() {
            while (index < source.length && source[index].isWhitespace()) index++
        }
    }
}
