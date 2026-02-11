package com.example.meetmerit

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TasksAdapter(
    private var tasks: List<Task>,
    private val onTaskClick: (Task) -> Unit
) : RecyclerView.Adapter<TasksAdapter.TaskViewHolder>() {

    private val apiFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayFormat = SimpleDateFormat("MMM dd", Locale.getDefault())

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layoutRoot: View = itemView.findViewById(R.id.layoutTaskRoot)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTaskTitle)
        val cbCompleted: CheckBox = itemView.findViewById(R.id.cbTaskCompleted)
        val ivCalendar: ImageView = itemView.findViewById(R.id.iv_calendar)
        val tvDueDate: TextView = itemView.findViewById(R.id.tv_due_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]

        holder.tvTitle.text = task.title
        holder.cbCompleted.isChecked = task.is_completed

        // ── Completion styling ──
        if (task.is_completed) {
            holder.tvTitle.paintFlags = holder.tvTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.layoutRoot.alpha = 0.6f
        } else {
            holder.tvTitle.paintFlags = holder.tvTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.layoutRoot.alpha = 1.0f
        }

        // ── Due date display ──
        if (!task.due_date.isNullOrBlank()) {
            try {
                val date = apiFormat.parse(task.due_date)!!

                val todayCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val tomorrowCal = (todayCal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, 1)
                }

                val dueCal = Calendar.getInstance().apply { time = date }
                dueCal.set(Calendar.HOUR_OF_DAY, 0)
                dueCal.set(Calendar.MINUTE, 0)
                dueCal.set(Calendar.SECOND, 0)
                dueCal.set(Calendar.MILLISECOND, 0)

                val isOverdue = dueCal.before(todayCal) && !task.is_completed
                val isToday = dueCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)
                        && dueCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
                val isTomorrow = dueCal.get(Calendar.YEAR) == tomorrowCal.get(Calendar.YEAR)
                        && dueCal.get(Calendar.DAY_OF_YEAR) == tomorrowCal.get(Calendar.DAY_OF_YEAR)

                // Format the display text
                val dateText = when {
                    isToday -> "Today"
                    isTomorrow -> "Tomorrow"
                    isOverdue -> "${displayFormat.format(date)} (Overdue)"
                    else -> displayFormat.format(date)
                }
                holder.tvDueDate.text = dateText

                // Color: red for overdue, grey otherwise
                val overdueColor = Color.parseColor("#D32F2F")
                val normalColor = Color.parseColor("#757575")

                if (isOverdue) {
                    holder.tvDueDate.setTextColor(overdueColor)
                    holder.ivCalendar.setColorFilter(overdueColor)
                } else {
                    holder.tvDueDate.setTextColor(normalColor)
                    holder.ivCalendar.setColorFilter(normalColor)
                }
            } catch (e: Exception) {
                holder.tvDueDate.text = task.due_date
                holder.tvDueDate.setTextColor(Color.parseColor("#757575"))
                holder.ivCalendar.setColorFilter(Color.parseColor("#757575"))
            }
            holder.ivCalendar.visibility = View.VISIBLE
            holder.tvDueDate.visibility = View.VISIBLE
        } else {
            holder.ivCalendar.visibility = View.GONE
            holder.tvDueDate.visibility = View.GONE
        }

        holder.cbCompleted.setOnClickListener {
            onTaskClick(task)
        }
    }

    override fun getItemCount() = tasks.size

    fun updateData(newTasks: List<Task>) {
        tasks = newTasks
        notifyDataSetChanged()
    }
}
