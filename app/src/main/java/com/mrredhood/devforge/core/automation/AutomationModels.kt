package com.mrredhood.devforge.core.automation

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

enum class AutomationStatus { ENABLED, DISABLED, PAUSED }
en
enum class AutomationRunStatus { RUNNING, WAITING_APPROVAL, COMPLETED, FAILED, CANCELLED, SKIPPED }

enum class AutomationTriggerType {
    SCHEDULE,
    MANUAL,
    REPOSITORY_CHANGE,
    BUILD_COMPLETION,
    CONDITION,
}

/** Supported durable schedule grammar for the first automation engine. */
sealed interface AutomationSchedule {
    data class Daily(val time: LocalTime) : AutomationSchedule
    data class Interval(val minutes: Long) : AutomationSchedule
    data class Once(val instant: Instant) : AutomationSchedule
}

object AutomationScheduleParser {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    fun parse(value: String?): AutomationSchedule? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null
        return when {
            raw.startsWith("daily@", ignoreCase = true) -> {
                val time = runCatching { LocalTime.parse(raw.substringAfter('@'), TIME) }
                    .getOrElse { throw IllegalArgumentException("Invalid daily schedule. Use daily@HH:mm.") }
                AutomationSchedule.Daily(time)
            }
            raw.startsWith("interval:", ignoreCase = true) -> {
                val minutes = raw.substringAfter(':').toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid interval schedule.")
                require(minutes in MIN_INTERVAL_MINUTES..MAX_INTERVAL_MINUTES) {
                    "Interval must be between $MIN_INTERVAL_MINUTES and $MAX_INTERVAL_MINUTES minutes."
                }
                AutomationSchedule.Interval(minutes)
            }
            raw.startsWith("once@", ignoreCase = true) -> {
                val instant = try {
                    ZonedDateTime.parse(raw.substringAfter('@')).toInstant()
                } catch (_: DateTimeParseException) {
                    try {
                        Instant.parse(raw.substringAfter('@'))
                    } catch (_: DateTimeParseException) {
                        throw IllegalArgumentException("Invalid once schedule. Use once@ISO-8601.")
                    }
                }
                AutomationSchedule.Once(instant)
            }
            else -> throw IllegalArgumentException("Unsupported schedule. Use daily@HH:mm, interval:N, or once@ISO-8601.")
        }
    }

    fun nextEpochMs(value: String?, nowEpochMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long? {
        return when (val schedule = parse(value)) {
            null -> null
            is AutomationSchedule.Interval -> nowEpochMs + schedule.minutes * 60_000L
            is AutomationSchedule.Once -> schedule.instant.toEpochMilli().takeIf { it > nowEpochMs }
            is AutomationSchedule.Daily -> {
                val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowEpochMs), zone)
                var next = LocalDate.now(zone).atTime(schedule.time).atZone(zone)
                if (!next.isAfter(now)) next = next.plusDays(1)
                next.toInstant().toEpochMilli()
            }
        }
    }

    const val MIN_INTERVAL_MINUTES = 15L
    const val MAX_INTERVAL_MINUTES = 7L * 24L * 60L
}
