package com.example.meetmerit

import android.graphics.Paint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TasksAdapter(
    private var tasks: List<Task>,
    private val onTaskClick: (Task) -> Unit,
    private val onNoteClick: (Task) -> Unit,
) : RecyclerView.Adapter<TasksAdapter.TaskViewHolder>() {

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
        val cbCompleted: CheckBox = itemView.findViewById(R.id.cbTaskCompleted)
        val layoutDateRow: View = itemView.findViewById(R.id.layoutDateRow)
        val tvDueDate: TextView = itemView.findViewById(R.id.tv_due_date)
        val ivCalendar: ImageView = itemView.findViewById(R.id.iv_calendar)
        val tvXpBadge: TextView = itemView.findViewById(R.id.tvXpBadge)
        val btnTaskNote: ImageView = itemView.findViewById(R.id.btnTaskNote)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]
        val ctx = holder.itemView.context

        Log.e("DEBUG_DATE", "Title: ${task.title}, Raw Date: ${task.dueDate}")

        holder.tvTitle.text = task.title
        holder.cbCompleted.isChecked = task.is_completed

        // --- Due date ---
        val dueDateRaw = task.dueDate
        val parsedDate = if (!dueDateRaw.isNullOrBlank()) parseDueDate(dueDateRaw) else null

        if (parsedDate != null) {
            holder.layoutDateRow.visibility = View.VISIBLE

            val dueCal = Calendar.getInstance().apply { time = parsedDate }
            val now = Calendar.getInstance()
            val today = Calendar.getInstance().apply { normalizeToDayStart() }
            val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val timeString = SimpleDateFormat("h:mm a", Locale.US).format(parsedDate)

            val dateText = when {
                isSameDay(dueCal, today) -> "Today, $timeString"
                isSameDay(dueCal, tomorrow) -> "Tomorrow, $timeString"
                else -> SimpleDateFormat("MMM d, h:mm a", Locale.US).format(parsedDate)
            }

            val isOverdue = !task.is_completed && dueCal.before(now)

            if (isOverdue) {
                holder.tvDueDate.text = "$dateText (Overdue)"
                val errorColor = ContextCompat.getColor(ctx, R.color.md_error)
                holder.tvDueDate.setTextColor(errorColor)
                holder.ivCalendar.setColorFilter(errorColor)
            } else {
                holder.tvDueDate.text = dateText
                val mutedColor = ContextCompat.getColor(ctx, R.color.md_on_surface_variant)
                holder.tvDueDate.setTextColor(mutedColor)
                holder.ivCalendar.setColorFilter(mutedColor)
            }
        } else {
            holder.layoutDateRow.visibility = View.GONE
        }

        // --- Completed vs Active state ---
        if (task.is_completed) {
            holder.tvTitle.paintFlags = holder.tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.tvTitle.setTextColor(ContextCompat.getColor(ctx, R.color.md_on_surface_variant))
            holder.layoutDateRow.alpha = 0.5f
            holder.tvXpBadge.alpha = 0.5f
            holder.btnTaskNote.alpha = 0.5f
        } else {
            holder.tvTitle.paintFlags = holder.tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.tvTitle.setTextColor(ContextCompat.getColor(ctx, R.color.md_on_surface))
            holder.layoutDateRow.alpha = 1f
            holder.tvXpBadge.alpha = 1f
            holder.btnTaskNote.alpha = 1f
        }

        holder.cbCompleted.setOnClickListener {
            onTaskClick(task)
        }
        holder.btnTaskNote.setOnClickListener {
            onNoteClick(task)
        }
    }

    override fun getItemCount() = tasks.size

    fun updateData(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    fun getTaskAt(position: Int): Task? {
        return tasks.getOrNull(position)
    }

    private fun parseDueDate(raw: String): Date? {
        // Strip trailing 'Z' and any timezone offset so naive patterns match
        var cleanRaw = raw.trim()
        if (cleanRaw.endsWith("Z")) {
            cleanRaw = cleanRaw.dropLast(1)
        }
        // Strip +00:00 / +05:30 style offset
        cleanRaw = cleanRaw.replace(Regex("[+-]\\d{2}:\\d{2}$"), "")
        // Truncate microseconds (.123456) to millis (.123)
        cleanRaw = cleanRaw.replace(Regex("(\\.\\d{3})\\d+"), "$1")

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS",  // millis (truncated from Django microseconds)
            "yyyy-MM-dd'T'HH:mm:ss",       // standard ISO
            "yyyy-MM-dd'T'HH:mm",          // no seconds
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd"
        )

        for (pattern in patterns) {
            try {
                return SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(cleanRaw)
            } catch (_: Exception) {
            }
        }
        Log.e("DEBUG_DATE", "Failed to parse date: '$raw' (cleaned: '$cleanRaw')")
        return null
    }

    private fun Calendar.normalizeToDayStart() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun isSameDay(first: Calendar, second: Calendar): Boolean {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }
}
