package com.example.meetmerit

import android.os.Bundle
import androidx.core.os.bundleOf

object NotesNavigation {
    const val ARG_SCREEN_TITLE = "screen_title"
    const val ARG_SCREEN_SUBTITLE = "screen_subtitle"
    const val ARG_LINKED_TASK_ID = "linked_task_id"
    const val ARG_LINKED_TIMETABLE_ENTRY_ID = "linked_timetable_entry_id"
    const val ARG_DEFAULT_NOTE_TYPE = "default_note_type"
    const val ARG_COURSE_NAME = "course_name"
    const val ARG_TEMPLATE_TITLE = "template_title"
    const val ARG_TEMPLATE_CONTENT = "template_content"

    fun notebookArgs(
        title: String = "Notebook",
        subtitle: String = "Quick markdown notes for classes, tasks and reminders."
    ): Bundle {
        return bundleOf(
            ARG_SCREEN_TITLE to title,
            ARG_SCREEN_SUBTITLE to subtitle,
            ARG_DEFAULT_NOTE_TYPE to NoteType.QUICK.apiValue,
        )
    }

    fun taskArgs(task: Task): Bundle {
        return bundleOf(
            ARG_SCREEN_TITLE to task.title,
            ARG_SCREEN_SUBTITLE to "Task-linked notes and checklists.",
            ARG_LINKED_TASK_ID to task.id,
            ARG_DEFAULT_NOTE_TYPE to NoteType.TASK.apiValue,
            ARG_TEMPLATE_TITLE to "${task.title} Notes",
            ARG_TEMPLATE_CONTENT to buildTaskTemplate(task),
        )
    }

    fun classArgs(entry: TimetableEntry): Bundle {
        return bundleOf(
            ARG_SCREEN_TITLE to entry.courseName,
            ARG_SCREEN_SUBTITLE to "${dayLabel(entry.dayOfWeek)} • ${
                TimeOptionUtils.apiToCompactDisplayRange(entry.startTime, entry.endTime)
            } • ${entry.classroom}",
            ARG_LINKED_TIMETABLE_ENTRY_ID to entry.id,
            ARG_DEFAULT_NOTE_TYPE to NoteType.CLASS.apiValue,
            ARG_COURSE_NAME to entry.courseName,
            ARG_TEMPLATE_TITLE to "${entry.courseName} Notes",
            ARG_TEMPLATE_CONTENT to buildClassTemplate(entry),
        )
    }

    private fun buildTaskTemplate(task: Task): String {
        val dueDateLine = task.dueDate?.takeIf { it.isNotBlank() } ?: "Not set"
        return """
            # ${task.title}

            Task:
            - ${task.title}

            Deadline:
            - $dueDateLine

            Checklist:
            - [ ]

            Notes:
            -
        """.trimIndent()
    }

    private fun buildClassTemplate(entry: TimetableEntry): String {
        return """
            # ${entry.courseName}

            Date:
            - ${dayLabel(entry.dayOfWeek)}

            Time:
            - ${TimeOptionUtils.apiToCompactDisplayRange(entry.startTime, entry.endTime)}

            Location:
            - ${entry.classroom}

            Key points:
            -

            Homework:
            - [ ]

            Questions:
            -
        """.trimIndent()
    }

    private fun dayLabel(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            7 -> "Sunday"
            else -> "Class"
        }
    }
}

enum class NoteType(val apiValue: String, val label: String) {
    QUICK("quick", "Quick"),
    TASK("task", "Task"),
    CLASS("class", "Class"),
    COURSE("course", "Course");

    companion object {
        fun fromApiValue(value: String?): NoteType {
            return entries.firstOrNull { it.apiValue == value } ?: QUICK
        }
    }
}
