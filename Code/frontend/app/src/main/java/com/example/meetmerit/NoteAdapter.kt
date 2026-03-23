package com.example.meetmerit

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class NoteAdapter(
    private var notes: List<Note>,
    private val onNoteClick: (Note) -> Unit,
    private val onDeleteClick: (Note) -> Unit,
) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTypeChip: TextView = itemView.findViewById(R.id.tvNoteTypeChip)
        val tvUpdatedAt: TextView = itemView.findViewById(R.id.tvNoteUpdatedAt)
        val tvTitle: TextView = itemView.findViewById(R.id.tvNoteTitle)
        val tvContext: TextView = itemView.findViewById(R.id.tvNoteContext)
        val tvPreview: TextView = itemView.findViewById(R.id.tvNotePreview)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteNote)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val note = notes[position]
        val context = holder.itemView.context
        val noteType = NoteType.fromApiValue(note.noteType)

        holder.tvTypeChip.text = noteType.label
        holder.tvTypeChip.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.resources.displayMetrics.density * 12
            setColor(ContextCompat.getColor(context, chipBackgroundColor(noteType)))
        }
        holder.tvTypeChip.setTextColor(ContextCompat.getColor(context, chipTextColor(noteType)))

        holder.tvUpdatedAt.text = formatUpdatedAt(note.updatedAt)
        holder.tvTitle.text = note.title
        holder.tvContext.text = buildContextText(note, noteType)
        holder.tvPreview.text = buildPreview(note.contentMarkdown)

        holder.itemView.setOnClickListener { onNoteClick(note) }
        holder.btnDelete.setOnClickListener { onDeleteClick(note) }
    }

    override fun getItemCount(): Int = notes.size

    fun updateData(newNotes: List<Note>) {
        notes = newNotes
        notifyDataSetChanged()
    }

    private fun buildContextText(note: Note, noteType: NoteType): String {
        return when {
            note.courseName.isNotBlank() -> note.courseName
            noteType == NoteType.TASK -> "Linked to a task"
            noteType == NoteType.CLASS -> "Linked to a class"
            noteType == NoteType.COURSE -> "Course note"
            else -> "Quick note"
        }
    }

    private fun buildPreview(markdown: String): String {
        val cleaned = markdown
            .replace(Regex("(?m)^#{1,6}\\s*"), "")
            .replace("- [ ]", "☐ ")
            .replace("- [x]", "☑ ")
            .replace("- [X]", "☑ ")
            .replace(Regex("(?m)^-\\s+"), "• ")
            .replace(Regex("(?m)^>\\s+"), "│ ")
            .replace("**", "")
            .replace(Regex("[_`#]"), "")
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return if (cleaned.isBlank()) {
            "Tap to add your markdown notes."
        } else {
            cleaned.take(120)
        }
    }

    private fun formatUpdatedAt(value: String?): String {
        if (value.isNullOrBlank()) {
            return "Updated recently"
        }

        val normalized = value.removeSuffix("Z")
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss"
        )

        patterns.forEach { pattern ->
            try {
                val parsed = SimpleDateFormat(pattern, Locale.US).parse(normalized) ?: return@forEach
                val output = SimpleDateFormat("MMM d, h:mm a", Locale.US)
                return "Updated ${output.format(parsed)}"
            } catch (_: Exception) {
            }
        }

        return "Updated recently"
    }

    private fun chipBackgroundColor(noteType: NoteType): Int {
        return when (noteType) {
            NoteType.QUICK -> R.color.md_primary_container
            NoteType.TASK -> R.color.app_warning_container
            NoteType.CLASS -> R.color.md_secondary_container
            NoteType.COURSE -> R.color.app_violet_container
        }
    }

    private fun chipTextColor(noteType: NoteType): Int {
        return when (noteType) {
            NoteType.QUICK -> R.color.md_on_primary_container
            NoteType.TASK -> R.color.app_warning_text
            NoteType.CLASS -> R.color.md_on_secondary_container
            NoteType.COURSE -> R.color.app_violet_text
        }
    }
}
