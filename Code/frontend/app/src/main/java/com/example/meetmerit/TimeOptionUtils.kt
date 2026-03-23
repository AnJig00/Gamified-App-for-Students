package com.example.meetmerit

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeOptionUtils {
    fun buildTimeOptions(includeEndOfDay: Boolean = false): List<String> {
        val options = mutableListOf<String>()

        for (hour in 0..23) {
            for (minute in listOf(0, 15, 30, 45)) {
                options.add(String.format(Locale.US, "%02d:%02d", hour, minute))
            }
        }

        if (includeEndOfDay && !options.contains("23:59")) {
            options.add("23:59")
        }

        return options
    }

    fun apiToDisplayTime(raw: String): String {
        val parsed = parseWithPatterns(
            normalizeTimeInput(raw),
            listOf("HH:mm:ss", "HH:mm", "H:mm")
        ) ?: return raw

        return SimpleDateFormat("h:mm a", Locale.US).format(parsed)
    }

    fun userInputToApiTime(raw: String): String? {
        val normalized = normalizeTimeInput(raw)
        if (normalized.isBlank()) {
            return null
        }

        val parsed = parseWithPatterns(
            normalized,
            listOf("h:mm a", "h a", "hh:mm a", "hh a", "H:mm", "HH:mm", "H")
        ) ?: return null

        return SimpleDateFormat("HH:mm:ss", Locale.US).format(parsed)
    }

    fun normalizeUserDisplayTime(raw: String): String? {
        val apiTime = userInputToApiTime(raw) ?: return null
        return apiToDisplayTime(apiTime)
    }

    fun isEndAfterStart(startTime: String, endTime: String): Boolean {
        val startApi = userInputToApiTime(startTime) ?: return false
        val endApi = userInputToApiTime(endTime) ?: return false
        return toMinutes(endApi) > toMinutes(startApi)
    }

    fun apiToCompactDisplayRange(startTime: String, endTime: String): String {
        return "${apiToDisplayTime(startTime)} - ${apiToDisplayTime(endTime)}"
    }

    fun displayToApiTime(raw: String): String {
        return userInputToApiTime(raw) ?: raw
    }

    private fun normalizeTimeInput(raw: String): String {
        return raw
            .trim()
            .replace(".", "")
            .replace(Regex("\\s+"), " ")
            .uppercase(Locale.US)
    }

    private fun parseWithPatterns(raw: String, patterns: List<String>): Date? {
        for (pattern in patterns) {
            try {
                return SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                }.parse(raw)
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun toMinutes(rawTime: String): Int {
        val parts = rawTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return (hour * 60) + minute
    }
}
