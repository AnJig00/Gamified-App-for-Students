package com.example.meetmerit

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

data class TimetableDaySection(
    val label: String,
    val entries: List<TimetableEntry>
)

class TimetableAdapter(
    private var sections: List<TimetableDaySection>,
    private val onEntryClick: (TimetableEntry) -> Unit,
    private val onEntryDelete: (TimetableEntry) -> Unit,
    private val onEntryNotesClick: (TimetableEntry) -> Unit,
) : RecyclerView.Adapter<TimetableAdapter.DaySectionViewHolder>() {

    inner class DaySectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDayLabel: TextView = itemView.findViewById(R.id.tvDayLabel)
        val tvEmptyDay: TextView = itemView.findViewById(R.id.tvEmptyDay)
        val layoutEntries: LinearLayout = itemView.findViewById(R.id.layoutEntries)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DaySectionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timetable_day_section, parent, false)
        return DaySectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: DaySectionViewHolder, position: Int) {
        val section = sections[position]
        holder.tvDayLabel.text = section.label
        holder.layoutEntries.removeAllViews()

        if (section.entries.isEmpty()) {
            holder.tvEmptyDay.visibility = View.VISIBLE
        } else {
            holder.tvEmptyDay.visibility = View.GONE
            section.entries.forEach { entry ->
                holder.layoutEntries.addView(createEntryView(holder.layoutEntries, entry))
            }
        }
    }

    override fun getItemCount(): Int = sections.size

    fun updateData(newSections: List<TimetableDaySection>) {
        sections = newSections
        notifyDataSetChanged()
    }

    private fun createEntryView(parent: ViewGroup, entry: TimetableEntry): View {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timetable_class_card, parent, false)

        val root = view.findViewById<View>(R.id.layoutClassCard)
        val tvCourseName = view.findViewById<TextView>(R.id.tvCourseName)
        val tvClassMeta = view.findViewById<TextView>(R.id.tvClassMeta)
        val btnNotes = view.findViewById<ImageButton>(R.id.btnClassNotes)
        val btnDelete = view.findViewById<ImageButton>(R.id.btnDeleteClass)

        tvCourseName.text = entry.courseName
        tvClassMeta.text =
            "${TimeOptionUtils.apiToCompactDisplayRange(entry.startTime, entry.endTime)} • ${entry.classroom}"

        applyPastelBackground(root, entry)

        root.setOnClickListener {
            onEntryClick(entry)
        }
        btnNotes.setOnClickListener {
            onEntryNotesClick(entry)
        }
        btnDelete.setOnClickListener {
            onEntryDelete(entry)
        }

        return view
    }

    private fun applyPastelBackground(view: View, entry: TimetableEntry) {
        val colors = listOf(
            R.color.timetable_blue_100,
            R.color.timetable_mint_100,
            R.color.timetable_green_100,
            R.color.timetable_yellow_100,
            R.color.timetable_purple_100,
            R.color.timetable_pink_100,
            R.color.timetable_orange_100
        )
        val colorRes = colors[absoluteValue(entry.courseName.hashCode()) % colors.size]
        val color = ContextCompat.getColor(view.context, colorRes)
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = view.resources.displayMetrics.density * 16
            setColor(color)
        }
        view.background = background
    }

    private fun absoluteValue(value: Int): Int {
        return if (value == Int.MIN_VALUE) 0 else kotlin.math.abs(value)
    }
}
