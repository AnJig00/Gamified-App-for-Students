package com.example.meetmerit

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesFragment : Fragment() {

    private lateinit var adapter: NoteAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView

    private var currentUserId: Int = -1
    private var linkedTaskId: Int? = null
    private var linkedTimetableEntryId: Int? = null
    private var courseName: String = ""
    private var screenTitle: String = "Notebook"
    private var screenSubtitle: String = "Quick markdown notes for classes, tasks and reminders."
    private var defaultNoteType: NoteType = NoteType.QUICK
    private var templateTitle: String = "Quick Note"
    private var templateContent: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_notes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        readArguments()

        val prefs = activity?.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        currentUserId = prefs?.getInt("USER_ID", -1) ?: -1

        progressBar = view.findViewById(R.id.progressBarNotes)
        tvEmptyState = view.findViewById(R.id.tvEmptyNotes)
        tvTitle = view.findViewById(R.id.tvNotesTitle)
        tvSubtitle = view.findViewById(R.id.tvNotesSubtitle)

        val btnBack = view.findViewById<ImageButton>(R.id.btnBackNotes)
        val btnAdd = view.findViewById<MaterialButton>(R.id.btnAddNote)
        val rvNotes = view.findViewById<RecyclerView>(R.id.rvNotes)

        tvTitle.text = screenTitle
        tvSubtitle.text = screenSubtitle

        adapter = NoteAdapter(
            notes = emptyList(),
            onNoteClick = { note -> showNoteDetails(note) },
            onDeleteClick = { note -> confirmDelete(note) }
        )
        rvNotes.layoutManager = LinearLayoutManager(context)
        rvNotes.adapter = adapter

        btnBack.setOnClickListener { findNavController().navigateUp() }
        btnAdd.setOnClickListener { showNoteEditor(null) }

        if (currentUserId == -1) {
            Toast.makeText(context, "Error: User ID missing", Toast.LENGTH_SHORT).show()
            return
        }

        fetchNotes()
    }

    override fun onResume() {
        super.onResume()
        (activity as? HomeActivity)?.setBottomNavVisibility(false)
    }

    override fun onDestroyView() {
        (activity as? HomeActivity)?.setBottomNavVisibility(true)
        super.onDestroyView()
    }

    private fun readArguments() {
        val args = arguments
        linkedTaskId = args?.takeIf { it.containsKey(NotesNavigation.ARG_LINKED_TASK_ID) }
            ?.getInt(NotesNavigation.ARG_LINKED_TASK_ID)
        linkedTimetableEntryId = args?.takeIf { it.containsKey(NotesNavigation.ARG_LINKED_TIMETABLE_ENTRY_ID) }
            ?.getInt(NotesNavigation.ARG_LINKED_TIMETABLE_ENTRY_ID)
        courseName = args?.getString(NotesNavigation.ARG_COURSE_NAME).orEmpty()
        screenTitle = args?.getString(NotesNavigation.ARG_SCREEN_TITLE) ?: screenTitle
        screenSubtitle = args?.getString(NotesNavigation.ARG_SCREEN_SUBTITLE) ?: screenSubtitle
        templateTitle = args?.getString(NotesNavigation.ARG_TEMPLATE_TITLE) ?: templateTitle
        templateContent = args?.getString(NotesNavigation.ARG_TEMPLATE_CONTENT).orEmpty()
        defaultNoteType = NoteType.fromApiValue(
            args?.getString(NotesNavigation.ARG_DEFAULT_NOTE_TYPE)
        )
    }

    private fun fetchNotes() {
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notes = RetrofitClient.instance.getNotes(
                    userId = currentUserId,
                    linkedTaskId = linkedTaskId,
                    linkedTimetableEntryId = linkedTimetableEntryId,
                    courseName = if (linkedTimetableEntryId == null && courseName.isNotBlank()) {
                        courseName
                    } else {
                        null
                    }
                )

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    adapter.updateData(notes)
                    tvEmptyState.isVisible = notes.isEmpty()
                    tvEmptyState.text = emptyStateMessage()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        context,
                        "Failed to load notes: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showNoteEditor(existingNote: Note?) {
        val dialog = BottomSheetDialog(requireContext(), R.style.ThemeOverlay_App_BottomSheet)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_note, null)
        dialog.setContentView(dialogView)
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvNoteDialogTitle)
        val tvContext = dialogView.findViewById<TextView>(R.id.tvNoteContext)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.chipGroupNoteType)
        val chipQuick = dialogView.findViewById<Chip>(R.id.chipNoteQuick)
        val chipCourse = dialogView.findViewById<Chip>(R.id.chipNoteCourse)
        val tilTitle = dialogView.findViewById<TextInputLayout>(R.id.tilNoteTitle)
        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.etNoteTitle)
        val tilCourseName = dialogView.findViewById<TextInputLayout>(R.id.tilNoteCourseName)
        val etCourseName = dialogView.findViewById<TextInputEditText>(R.id.etNoteCourseName)
        val toggleNoteMode = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggleNoteMode)
        val scrollNoteTools = dialogView.findViewById<View>(R.id.scrollNoteTools)
        val btnInsertHeading = dialogView.findViewById<MaterialButton>(R.id.btnInsertHeading)
        val btnInsertBullet = dialogView.findViewById<MaterialButton>(R.id.btnInsertBullet)
        val btnInsertChecklist = dialogView.findViewById<MaterialButton>(R.id.btnInsertChecklist)
        val btnInsertBold = dialogView.findViewById<MaterialButton>(R.id.btnInsertBold)
        val tilContent = dialogView.findViewById<TextInputLayout>(R.id.tilNoteContent)
        val etContent = dialogView.findViewById<TextInputEditText>(R.id.etNoteContent)
        val tvRenderedPreview = dialogView.findViewById<TextView>(R.id.tvRenderedPreview)
        val btnDelete = dialogView.findViewById<MaterialButton>(R.id.btnDeleteNoteDialog)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnSaveNoteDialog)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseNoteDialog)

        val lockedType = lockedContextNoteType() ?: existingLockedNoteType(existingNote)
        var selectedType = existingNote?.let { NoteType.fromApiValue(it.noteType) } ?: lockedType ?: defaultNoteType

        tvDialogTitle.text = if (existingNote == null) "New Note" else "Edit Note"
        tvContext.text = contextLabelForEditor(existingNote)
        tvContext.isVisible = tvContext.text.isNotBlank()

        etTitle.setText(existingNote?.title ?: templateTitle)
        etCourseName.setText(existingNote?.courseName?.ifBlank { courseName } ?: courseName)
        etContent.setText(existingNote?.contentMarkdown ?: templateContent)
        btnDelete.visibility = if (existingNote == null) View.GONE else View.VISIBLE

        val setPreviewMode: (Boolean) -> Unit = { previewMode ->
            tilContent.visibility = if (previewMode) View.GONE else View.VISIBLE
            scrollNoteTools.visibility = if (previewMode) View.GONE else View.VISIBLE
            tvRenderedPreview.visibility = if (previewMode) View.VISIBLE else View.GONE
            if (previewMode) {
                tvRenderedPreview.text = renderMarkdownPreview(
                    etContent.text?.toString().orEmpty()
                )
            }
        }

        toggleNoteMode.check(if (existingNote == null) R.id.btnNoteEditMode else R.id.btnNotePreviewMode)
        setPreviewMode(existingNote != null)
        toggleNoteMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            setPreviewMode(checkedId == R.id.btnNotePreviewMode)
        }

        etContent.doAfterTextChanged { editable ->
            tvRenderedPreview.text = renderMarkdownPreview(editable?.toString().orEmpty())
        }
        btnInsertHeading.setOnClickListener { insertLinePrefix(etContent, "# ") }
        btnInsertBullet.setOnClickListener { insertLinePrefix(etContent, "- ") }
        btnInsertChecklist.setOnClickListener { insertLinePrefix(etContent, "- [ ] ") }
        btnInsertBold.setOnClickListener { wrapSelection(etContent, "**", "**", "bold text") }

        if (lockedType != null) {
            chipGroup.visibility = View.GONE
            tilCourseName.visibility = if (lockedType == NoteType.CLASS || courseName.isNotBlank()) {
                View.VISIBLE
            } else {
                View.GONE
            }
            if (lockedType == NoteType.CLASS && courseName.isNotBlank()) {
                etCourseName.setText(courseName)
                etCourseName.isEnabled = false
            }
        } else {
            chipGroup.visibility = View.VISIBLE
            chipQuick.isChecked = selectedType == NoteType.QUICK
            chipCourse.isChecked = selectedType == NoteType.COURSE
            updateCourseFieldVisibility(tilCourseName, selectedType)
            chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
                selectedType = if (checkedIds.contains(R.id.chipNoteCourse)) {
                    NoteType.COURSE
                } else {
                    NoteType.QUICK
                }
                updateCourseFieldVisibility(tilCourseName, selectedType)
                if (selectedType == NoteType.COURSE && etTitle.text.isNullOrBlank() && courseName.isNotBlank()) {
                    etTitle.setText("$courseName Notes")
                }
            }
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        btnDelete.setOnClickListener {
            existingNote?.let { note ->
                dialog.dismiss()
                confirmDelete(note)
            }
        }
        btnSave.setOnClickListener {
            tilTitle.error = null
            tilCourseName.error = null
            tilContent.error = null

            val title = etTitle.text?.toString()?.trim().orEmpty()
            val content = etContent.text?.toString()?.trim().orEmpty()
            val resolvedType = lockedType ?: selectedType
            val resolvedCourseName = when (resolvedType) {
                NoteType.CLASS -> courseName.ifBlank { etCourseName.text?.toString()?.trim().orEmpty() }
                NoteType.COURSE -> etCourseName.text?.toString()?.trim().orEmpty()
                else -> ""
            }

            var hasError = false
            if (title.isEmpty()) {
                tilTitle.error = "Title is required"
                hasError = true
            }
            if (resolvedType == NoteType.COURSE && resolvedCourseName.isBlank()) {
                tilCourseName.error = "Course name is required"
                hasError = true
            }
            if (content.isEmpty()) {
                tilContent.error = "Write at least a short markdown note"
                hasError = true
            }
            if (hasError) {
                return@setOnClickListener
            }

            val payload = Note(
                id = existingNote?.id ?: 0,
                title = title,
                contentMarkdown = content,
                noteType = resolvedType.apiValue,
                courseName = resolvedCourseName,
                linkedTaskId = linkedTaskId ?: existingNote?.linkedTaskId,
                linkedTimetableEntryId = linkedTimetableEntryId ?: existingNote?.linkedTimetableEntryId,
                createdAt = existingNote?.createdAt,
                updatedAt = existingNote?.updatedAt
            )

            saveNote(existingNote?.id, payload, dialog)
        }

        dialog.show()
    }

    private fun showNoteDetails(note: Note) {
        val dialog = BottomSheetDialog(requireContext(), R.style.ThemeOverlay_App_BottomSheet)
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_note_detail, null)
        dialog.setContentView(dialogView)
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvNoteDetailTitle)
        val tvContext = dialogView.findViewById<TextView>(R.id.tvNoteDetailContext)
        val tvTypeChip = dialogView.findViewById<TextView>(R.id.tvNoteDetailTypeChip)
        val tvUpdatedAt = dialogView.findViewById<TextView>(R.id.tvNoteDetailUpdatedAt)
        val tvBody = dialogView.findViewById<TextView>(R.id.tvNoteDetailBody)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseNoteDetail)
        val btnDelete = dialogView.findViewById<MaterialButton>(R.id.btnDeleteNoteDetail)
        val btnExtractChecklist = dialogView.findViewById<MaterialButton>(R.id.btnExtractChecklist)
        val btnEdit = dialogView.findViewById<MaterialButton>(R.id.btnEditNoteDetail)
        val btnDone = dialogView.findViewById<MaterialButton>(R.id.btnDoneNoteDetail)

        val noteType = NoteType.fromApiValue(note.noteType)
        val contextText = buildNoteContextText(note, noteType)
        val checklistItems = parseUncheckedChecklistItems(note.contentMarkdown)

        tvTitle.text = note.title
        tvContext.text = contextText
        tvContext.isVisible = contextText.isNotBlank()
        tvTypeChip.text = noteType.label
        tvTypeChip.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = resources.displayMetrics.density * 12
            setColor(ContextCompat.getColor(requireContext(), chipBackgroundColor(noteType)))
        }
        tvTypeChip.setTextColor(ContextCompat.getColor(requireContext(), chipTextColor(noteType)))
        tvUpdatedAt.text = formatNoteUpdatedAt(note.updatedAt)
        tvBody.text = renderMarkdownPreview(note.contentMarkdown)
        btnExtractChecklist.visibility = if (checklistItems.isEmpty()) View.GONE else View.VISIBLE

        btnClose.setOnClickListener { dialog.dismiss() }
        btnDone.setOnClickListener { dialog.dismiss() }
        btnExtractChecklist.setOnClickListener {
            showChecklistImportDialog(note, checklistItems) {
                dialog.dismiss()
            }
        }
        btnEdit.setOnClickListener {
            dialog.dismiss()
            showNoteEditor(note)
        }
        btnDelete.setOnClickListener {
            dialog.dismiss()
            confirmDelete(note)
        }

        dialog.show()
    }

    private fun saveNote(existingId: Int?, note: Note, dialog: BottomSheetDialog) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (existingId == null) {
                    RetrofitClient.instance.createNote(currentUserId, note)
                } else {
                    RetrofitClient.instance.updateNote(existingId, currentUserId, note)
                }

                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(
                        context,
                        if (existingId == null) "Note saved" else "Note updated",
                        Toast.LENGTH_SHORT
                    ).show()
                    fetchNotes()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Failed to save note: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun confirmDelete(note: Note) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete note?")
            .setMessage("This note will be removed from your notebook.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ -> deleteNote(note) }
            .show()
    }

    private fun deleteNote(note: Note) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.instance.deleteNote(note.id, currentUserId)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Note deleted", Toast.LENGTH_SHORT).show()
                        fetchNotes()
                    } else {
                        Toast.makeText(context, "Failed to delete note", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Failed to delete note: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun emptyStateMessage(): String {
        return when {
            linkedTaskId != null -> "No notes for this task yet. Capture requirements, subtasks or deadline details."
            linkedTimetableEntryId != null -> "No class notes yet. Save key points, homework and questions here."
            else -> "Your notebook is empty. Start with a quick markdown note or a course note."
        }
    }

    private fun contextLabelForEditor(existingNote: Note?): String {
        return when {
            linkedTaskId != null -> "Linked to this task"
            linkedTimetableEntryId != null && courseName.isNotBlank() -> "Linked to $courseName"
            linkedTimetableEntryId != null -> "Linked to this class"
            existingNote?.linkedTaskId != null -> "Linked to a task"
            existingNote?.noteType == NoteType.CLASS.apiValue && existingNote.courseName.isNotBlank() -> {
                "Linked to ${existingNote.courseName}"
            }
            existingNote?.linkedTimetableEntryId != null -> "Linked to a class"
            else -> ""
        }
    }

    private fun lockedContextNoteType(): NoteType? {
        return when {
            linkedTaskId != null -> NoteType.TASK
            linkedTimetableEntryId != null -> NoteType.CLASS
            else -> null
        }
    }

    private fun existingLockedNoteType(existingNote: Note?): NoteType? {
        return when (existingNote?.noteType) {
            NoteType.TASK.apiValue -> NoteType.TASK
            NoteType.CLASS.apiValue -> NoteType.CLASS
            else -> null
        }
    }

    private fun updateCourseFieldVisibility(tilCourseName: TextInputLayout, noteType: NoteType) {
        tilCourseName.visibility = if (noteType == NoteType.COURSE) View.VISIBLE else View.GONE
    }

    private fun buildNoteContextText(note: Note, noteType: NoteType): String {
        return when {
            note.courseName.isNotBlank() -> note.courseName
            noteType == NoteType.TASK -> "Linked to a task"
            noteType == NoteType.CLASS -> "Linked to a class"
            noteType == NoteType.COURSE -> "Course note"
            else -> "Quick note"
        }
    }

    private fun formatNoteUpdatedAt(value: String?): String {
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
                val parsed = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                    .parse(normalized) ?: return@forEach
                val output = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.US)
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

    private fun parseUncheckedChecklistItems(markdown: String): List<String> {
        return markdown
            .lines()
            .mapNotNull { rawLine ->
                val line = rawLine.trim()
                val match = Regex("""^[-*]\s+\[\s\]\s+(.+)$""").find(line)
                match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            }
            .distinct()
    }

    private fun showChecklistImportDialog(
        note: Note,
        checklistItems: List<String>,
        onImportStarted: () -> Unit
    ) {
        if (checklistItems.isEmpty()) {
            Toast.makeText(context, "No unchecked checklist items found.", Toast.LENGTH_SHORT).show()
            return
        }

        val selected = BooleanArray(checklistItems.size) { true }

        AlertDialog.Builder(requireContext())
            .setTitle("Add checklist to tasks")
            .setMultiChoiceItems(checklistItems.toTypedArray(), selected) { _, which, isChecked ->
                selected[which] = isChecked
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create Tasks") { _, _ ->
                val chosenItems = checklistItems.filterIndexed { index, _ -> selected[index] }
                if (chosenItems.isEmpty()) {
                    Toast.makeText(context, "Choose at least one checklist item.", Toast.LENGTH_SHORT).show()
                } else {
                    onImportStarted()
                    importChecklistItemsAsTasks(note, chosenItems)
                }
            }
            .show()
    }

    private fun importChecklistItemsAsTasks(note: Note, items: List<String>) {
        if (currentUserId <= 0) {
            Toast.makeText(context, "Please log in again before creating tasks.", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val createdItems = mutableListOf<String>()

            items.forEach { item ->
                val taskTitle = item.take(TASK_TITLE_MAX_LENGTH)
                val newTask = Task(
                    id = 0,
                    title = taskTitle,
                    is_completed = false,
                    dueDate = null
                )

                runCatching {
                    RetrofitClient.instance.createTask(currentUserId, newTask)
                }.onSuccess {
                    createdItems += item
                }
            }

            var noteUpdated = false
            if (createdItems.isNotEmpty()) {
                val updatedMarkdown = markChecklistItemsCompleted(note.contentMarkdown, createdItems)
                if (updatedMarkdown != note.contentMarkdown) {
                    val updatedNote = note.copy(contentMarkdown = updatedMarkdown)
                    noteUpdated = runCatching {
                        RetrofitClient.instance.updateNote(note.id, currentUserId, updatedNote)
                    }.isSuccess
                }
            }

            withContext(Dispatchers.Main) {
                when {
                    createdItems.size == items.size && noteUpdated -> {
                        Toast.makeText(
                            context,
                            "Added ${createdItems.size} task${if (createdItems.size == 1) "" else "s"} and marked them done in the note.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    createdItems.size == items.size -> {
                        Toast.makeText(
                            context,
                            "Added ${createdItems.size} task${if (createdItems.size == 1) "" else "s"}, but the note was not updated.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    createdItems.isNotEmpty() && noteUpdated -> {
                        Toast.makeText(
                            context,
                            "Added ${createdItems.size} of ${items.size} checklist items and updated the note.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    createdItems.isNotEmpty() -> {
                        Toast.makeText(
                            context,
                            "Added ${createdItems.size} of ${items.size} checklist items to tasks.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    else -> {
                        Toast.makeText(
                            context,
                            "Could not create tasks from this checklist.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                if (createdItems.isNotEmpty()) {
                    fetchNotes()
                }
            }
        }
    }

    private fun markChecklistItemsCompleted(markdown: String, completedItems: List<String>): String {
        val remainingCounts = completedItems
            .groupingBy { it.trim() }
            .eachCount()
            .toMutableMap()

        return markdown.lines().joinToString("\n") { rawLine ->
            val trimmed = rawLine.trim()
            val match = Regex("""^([-*])\s+\[\s\]\s+(.+)$""").find(trimmed)
            if (match == null) {
                rawLine
            } else {
                val bullet = match.groupValues[1]
                val itemText = match.groupValues[2].trim()
                val remaining = remainingCounts[itemText] ?: 0
                if (remaining > 0) {
                    remainingCounts[itemText] = remaining - 1
                    rawLine.replaceFirst(
                        Regex("""^(\s*)[-*]\s+\[\s\]\s+"""),
                        "${'$'}1$bullet [x] "
                    )
                } else {
                    rawLine
                }
            }
        }
    }

    private fun insertLinePrefix(editText: TextInputEditText, prefix: String) {
        val editable = editText.text ?: return
        val cursor = editText.selectionStart.coerceAtLeast(0)
        val lineStart = editable.toString().lastIndexOf('\n', (cursor - 1).coerceAtLeast(0))
            .let { if (it == -1) 0 else it + 1 }

        editable.insert(lineStart, prefix)
        val targetCursor = (cursor + prefix.length).coerceAtMost(editable.length)
        editText.setSelection(targetCursor)
    }

    private fun wrapSelection(
        editText: TextInputEditText,
        before: String,
        after: String,
        placeholder: String
    ) {
        val editable = editText.text ?: return
        val start = editText.selectionStart.coerceAtLeast(0)
        val end = editText.selectionEnd.coerceAtLeast(start)
        val hasSelection = end > start
        val selectedText = editable.subSequence(start, end).toString()
        val content = if (hasSelection) selectedText else placeholder

        editable.replace(start, end, before + content + after)

        if (hasSelection) {
            editText.setSelection(start + before.length + content.length + after.length)
        } else {
            editText.setSelection(start + before.length, start + before.length + content.length)
        }
    }

    private fun renderMarkdownPreview(markdown: String): CharSequence {
        if (markdown.isBlank()) {
            return "Nothing to preview yet. Write your note or use the quick actions above."
        }

        val builder = SpannableStringBuilder()
        val lines = markdown.lines()

        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trimEnd()
            appendMarkdownLineClean(builder, line)
            if (index != lines.lastIndex) {
                builder.append("\n")
            }
        }

        return builder
    }

    private fun appendMarkdownLineClean(builder: SpannableStringBuilder, line: String) {
        val trimmed = line.trimStart()
        when {
            trimmed.isBlank() -> builder.append("\n")
            trimmed.startsWith("### ") -> appendStyledText(builder, trimmed.removePrefix("### "), 1.12f, true)
            trimmed.startsWith("## ") -> appendStyledText(builder, trimmed.removePrefix("## "), 1.18f, true)
            trimmed.startsWith("# ") -> appendStyledText(builder, trimmed.removePrefix("# "), 1.24f, true)
            trimmed.startsWith("- [x] ", ignoreCase = true) -> appendStyledText(builder, "[x] ${trimmed.drop(6)}")
            trimmed.startsWith("- [ ] ") -> appendStyledText(builder, "[ ] ${trimmed.drop(6)}")
            trimmed.startsWith("- ") -> appendStyledText(builder, "* ${trimmed.drop(2)}")
            trimmed.startsWith("> ") -> appendStyledText(builder, "| ${trimmed.drop(2)}", italic = true)
            else -> appendStyledText(builder, trimmed)
        }
    }

    private fun appendMarkdownLine(builder: SpannableStringBuilder, line: String) {
        val trimmed = line.trimStart()
        when {
            trimmed.isBlank() -> builder.append("\n")
            trimmed.startsWith("### ") -> appendStyledText(builder, trimmed.removePrefix("### "), 1.12f, true)
            trimmed.startsWith("## ") -> appendStyledText(builder, trimmed.removePrefix("## "), 1.18f, true)
            trimmed.startsWith("# ") -> appendStyledText(builder, trimmed.removePrefix("# "), 1.24f, true)
            trimmed.startsWith("- [x] ", ignoreCase = true) -> appendStyledText(builder, "☑ ${trimmed.drop(6)}")
            trimmed.startsWith("- [ ] ") -> appendStyledText(builder, "☐ ${trimmed.drop(6)}")
            trimmed.startsWith("- ") -> appendStyledText(builder, "• ${trimmed.drop(2)}")
            trimmed.startsWith("> ") -> appendStyledText(builder, "│ ${trimmed.drop(2)}", italic = true)
            else -> appendStyledText(builder, trimmed)
        }
    }

    private fun appendStyledText(
        builder: SpannableStringBuilder,
        text: String,
        sizeMultiplier: Float = 1f,
        bold: Boolean = false,
        italic: Boolean = false
    ) {
        val parsed = parseInlineMarkdown(text)
        val start = builder.length
        builder.append(parsed)
        val end = builder.length

        if (bold) {
            builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (italic) {
            builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (sizeMultiplier != 1f) {
            builder.setSpan(RelativeSizeSpan(sizeMultiplier), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun parseInlineMarkdown(text: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        var index = 0

        while (index < text.length) {
            val boldStart = text.indexOf("**", index)
            if (boldStart == -1) {
                builder.append(text.substring(index))
                break
            }

            builder.append(text.substring(index, boldStart))
            val boldEnd = text.indexOf("**", boldStart + 2)
            if (boldEnd == -1) {
                builder.append(text.substring(boldStart))
                break
            }

            val spanStart = builder.length
            builder.append(text.substring(boldStart + 2, boldEnd))
            val spanEnd = builder.length
            builder.setSpan(StyleSpan(Typeface.BOLD), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            index = boldEnd + 2
        }

        return builder
    }

    companion object {
        private const val TASK_TITLE_MAX_LENGTH = 120
    }
}
