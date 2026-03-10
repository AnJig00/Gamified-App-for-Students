package com.example.meetmerit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

sealed class TimetableListItem {
    data class DayHeader(val label: String) : TimetableListItem()
    data class EntryRow(val entry: TimetableEntry) : TimetableListItem()
}

class TimetableAdapter(
    private var items: List<TimetableListItem>,
    private val onEntryClick: (TimetableEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_DAY_HEADER = 0
        private const val VIEW_TYPE_ENTRY = 1
    }

    inner class DayHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDayHeader: TextView = itemView.findViewById(R.id.tvDayHeader)
    }

    inner class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCourseName: TextView = itemView.findViewById(R.id.tvCourseName)
        val tvClassTime: TextView = itemView.findViewById(R.id.tvClassTime)
        val tvClassroom: TextView = itemView.findViewById(R.id.tvClassroom)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is TimetableListItem.DayHeader -> VIEW_TYPE_DAY_HEADER
            is TimetableListItem.EntryRow -> VIEW_TYPE_ENTRY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_DAY_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_timetable_day_header, parent, false)
                DayHeaderViewHolder(view)
            }

            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_timetable_entry, parent, false)
                EntryViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is TimetableListItem.DayHeader -> {
                (holder as DayHeaderViewHolder).tvDayHeader.text = item.label
            }

            is TimetableListItem.EntryRow -> {
                holder as EntryViewHolder
                holder.tvCourseName.text = item.entry.courseName
                holder.tvClassTime.text =
                    "${toDisplayTime(item.entry.startTime)} - ${toDisplayTime(item.entry.endTime)}"
                holder.tvClassroom.text = item.entry.classroom
                holder.itemView.setOnClickListener {
                    onEntryClick(item.entry)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<TimetableListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun toDisplayTime(rawTime: String): String {
        val parts = rawTime.split(":")
        if (parts.size < 2) {
            return rawTime
        }
        return "${parts[0]}:${parts[1]}"
    }
}
