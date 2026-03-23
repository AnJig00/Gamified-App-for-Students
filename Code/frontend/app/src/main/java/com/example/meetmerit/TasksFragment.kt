package com.example.meetmerit

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import retrofit2.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TasksFragment : Fragment() {

    private lateinit var incompleteAdapter: TasksAdapter
    private lateinit var completedAdapter: TasksAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHeaderCompleted: TextView
    private lateinit var rvTasksCompleted: RecyclerView
    private lateinit var tvEmptyActive: TextView
    private lateinit var tvSectionTitle: TextView
    private lateinit var tvActiveCount: TextView
    private lateinit var tvDueSoonCount: TextView
    private lateinit var tvCompletedCount: TextView
    private lateinit var tvTaskSummaryCaption: TextView
    private var currentUserId: Int = -1

    // Header views
    private lateinit var tvGreeting: TextView
    private lateinit var tvLevelXp: TextView
    private lateinit var xpProgressBar: ProgressBar

    private var currentXp: Int = 0
    private var currentLevel: Int = 1
    private var username: String = "Student"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tasks, container, false)

        val rvIncomplete = view.findViewById<RecyclerView>(R.id.rvTasksIncomplete)
        rvTasksCompleted = view.findViewById(R.id.rvTasksCompleted)
        progressBar = view.findViewById(R.id.progressBar)
        tvHeaderCompleted = view.findViewById(R.id.tvHeaderCompleted)
        tvEmptyActive = view.findViewById(R.id.tvEmptyActive)
        tvSectionTitle = view.findViewById(R.id.tvSectionTitle)
        tvActiveCount = view.findViewById(R.id.tvActiveCount)
        tvDueSoonCount = view.findViewById(R.id.tvDueSoonCount)
        tvCompletedCount = view.findViewById(R.id.tvCompletedCount)
        tvTaskSummaryCaption = view.findViewById(R.id.tvTaskSummaryCaption)

        // Header
        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvLevelXp = view.findViewById(R.id.tvLevelXp)
        xpProgressBar = view.findViewById(R.id.xpProgressBar)
        val btnCalendar = view.findViewById<ImageButton>(R.id.btnCalendar)
        val btnNotes = view.findViewById<ImageButton>(R.id.btnNotes)

        // Read user data from SharedPreferences
        val prefs = activity?.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        currentUserId = prefs?.getInt("USER_ID", -1) ?: -1
        username = prefs?.getString("USERNAME", "Student") ?: "Student"
        currentXp = prefs?.getInt("CURRENT_XP", 0) ?: 0
        currentLevel = prefs?.getInt("LEVEL", 1) ?: 1

        if (currentUserId == -1) {
            Toast.makeText(context, "Error: User ID missing", Toast.LENGTH_SHORT).show()
        }

        updateHeaderUI()

        rvIncomplete.layoutManager = LinearLayoutManager(context)
        incompleteAdapter = TasksAdapter(
            tasks = emptyList(),
            onTaskClick = { task -> completeTask(task) },
            onNoteClick = { task -> openTaskNotes(task) }
        )
        rvIncomplete.adapter = incompleteAdapter
        attachSwipeToRemove(rvIncomplete, incompleteAdapter)

        rvTasksCompleted.layoutManager = LinearLayoutManager(context)
        completedAdapter = TasksAdapter(
            tasks = emptyList(),
            onTaskClick = { task -> completeTask(task) },
            onNoteClick = { task -> openTaskNotes(task) }
        )
        rvTasksCompleted.adapter = completedAdapter
        attachSwipeToRemove(rvTasksCompleted, completedAdapter)

        if (currentUserId != -1) {
            fetchTasks()
        }

        btnCalendar.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_tasks_to_weeklyTimetableFragment)
        }
        btnNotes.setOnClickListener {
            findNavController().navigate(R.id.notesFragment, NotesNavigation.notebookArgs())
        }

        val fabAddTask =
            view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(
                R.id.fabAddTask
            )
        fabAddTask.setOnClickListener {
            showAddTaskDialog()
        }

        return view
    }

    private fun updateHeaderUI() {
        // Greeting
        tvGreeting.text = "Hi, $username \uD83D\uDC4B"

        // Level & XP with colored "Level X" portion
        val maxXp = currentLevel * 100
        val levelText = "Level $currentLevel"
        val fullText = "$levelText \u2022 $currentXp/$maxXp XP"
        val spannable = SpannableString(fullText)
        spannable.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.md_primary)),
            0, levelText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            StyleSpan(Typeface.BOLD),
            0, levelText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tvLevelXp.text = spannable

        // Progress bar
        xpProgressBar.max = maxXp
        xpProgressBar.progress = currentXp.coerceAtMost(maxXp)
    }

    private fun saveXpToPrefs() {
        val prefs = activity?.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE) ?: return
        prefs.edit().putInt("CURRENT_XP", currentXp).apply()
    }

    private fun showAddTaskDialog() {
        val dialog = BottomSheetDialog(requireContext(), R.style.ThemeOverlay_App_BottomSheet)
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_task, null)
        dialog.setContentView(dialogView)

        // Make the BottomSheet background transparent so our drawable's rounded corners show
        val bottomSheet = dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        bottomSheet?.setBackgroundResource(android.R.color.transparent)

        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.etDialogTaskTitle)
        val tilDueDate = dialogView.findViewById<TextInputLayout>(R.id.tilDueDate)
        val etDueDate = dialogView.findViewById<TextInputEditText>(R.id.etDialogDueDate)
        val tilDueTime = dialogView.findViewById<TextInputLayout>(R.id.tilDueTime)
        val etDueTime = dialogView.findViewById<TextInputEditText>(R.id.etDialogDueTime)
        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnDialogClose)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDialogSave)

        var selectedDateIso: String? = null

        val openDatePicker = {
            val now = Calendar.getInstance()
            val datePicker = DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    selectedDateIso = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                    etDueDate.setText(selectedDateIso)
                    tilDueDate.error = null
                },
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH)
            )
            // Block past dates
            datePicker.datePicker.minDate = System.currentTimeMillis() - 1000
            datePicker.show()
        }

        etDueDate.setOnClickListener { openDatePicker() }
        tilDueDate.setEndIconOnClickListener { openDatePicker() }
        etDueTime.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                tilDueTime.error = null
            }
        }
        etDueTime.setOnClickListener {
            tilDueTime.error = null
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val selectedTimeRaw = etDueTime.text?.toString()?.trim().orEmpty()
            val tilTaskTitle = dialogView.findViewById<TextInputLayout>(R.id.tilTaskTitle)

            tilTaskTitle?.error = null
            if (title.isNotEmpty()) {
                if (selectedTimeRaw.isNotEmpty() && selectedDateIso == null) {
                    tilDueDate.error = "Choose a date before selecting time"
                    return@setOnClickListener
                }

                val normalizedTime = if (selectedTimeRaw.isBlank()) {
                    null
                } else {
                    TimeOptionUtils.normalizeUserDisplayTime(selectedTimeRaw)
                }

                if (selectedTimeRaw.isNotBlank() && normalizedTime == null) {
                    tilDueTime.error = "Use a valid time like 9:00 AM or 21:00"
                    return@setOnClickListener
                }

                if (normalizedTime != null) {
                    etDueTime.setText(normalizedTime)
                }

                val dueDateIso = buildDueDateTimeIso(
                    selectedDateIso = selectedDateIso,
                    selectedTime = normalizedTime
                )
                createNewTask(title, dueDateIso)
                dialog.dismiss()
            } else {
                tilTaskTitle?.error = "Title required"
            }
        }

        dialog.show()
    }

    private fun buildDueDateTimeIso(selectedDateIso: String?, selectedTime: String?): String? {
        if (selectedDateIso == null) {
            return null
        }

        val timePart = selectedTime ?: "23:59"
        return "${selectedDateIso}T${TimeOptionUtils.displayToApiTime(timePart)}"
    }

    private fun createNewTask(title: String, dueDate: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val newTask = Task(0, title, false, dueDate)
                android.util.Log.d("CreateTask", "Sending Task: title='$title', due='$dueDate'")

                RetrofitClient.instance.createTask(currentUserId, newTask)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Task Added!", Toast.LENGTH_SHORT).show()
                    fetchTasks()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun attachSwipeToRemove(recyclerView: RecyclerView, adapter: TasksAdapter) {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.45f

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position == RecyclerView.NO_POSITION) {
                    fetchTasks()
                    return
                }
                val task = adapter.getTaskAt(position)
                if (task == null) {
                    adapter.notifyItemChanged(position)
                    return
                }

                showRemoveTaskConfirmation(task, adapter, position)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0f) {
                    val itemView = viewHolder.itemView
                    val background = android.graphics.drawable.ColorDrawable(
                        ContextCompat.getColor(requireContext(), R.color.md_error)
                    )
                    background.setBounds(
                        itemView.right + dX.toInt(),
                        itemView.top,
                        itemView.right,
                        itemView.bottom
                    )
                    background.draw(c)

                    val label = "Remove"
                    val paint = android.graphics.Paint().apply {
                        color = ContextCompat.getColor(requireContext(), R.color.md_on_error)
                        textSize = 40f
                        isAntiAlias = true
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                    val textY = itemView.top + (itemView.height / 2f) - ((paint.descent() + paint.ascent()) / 2f)
                    c.drawText(label, itemView.right - 32f, textY, paint)
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }).attachToRecyclerView(recyclerView)
    }

    private fun showRemoveTaskConfirmation(task: Task, adapter: TasksAdapter, position: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove task?")
            .setMessage("This task will be removed from your list.")
            .setNegativeButton("Cancel") { _, _ ->
                adapter.notifyItemChanged(position)
            }
            .setOnCancelListener {
                adapter.notifyItemChanged(position)
            }
            .setPositiveButton("Remove") { _, _ ->
                removeTask(task)
            }
            .show()
    }

    private fun removeTask(task: Task) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.instance.deleteTask(task.id, currentUserId)
                withContext(Dispatchers.Main) {
                    handleRemoveTaskResponse(response)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Failed to remove task: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    fetchTasks()
                }
            }
        }
    }

    private fun handleRemoveTaskResponse(response: Response<Unit>) {
        if (response.isSuccessful) {
            Toast.makeText(context, "Task removed", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Could not remove task", Toast.LENGTH_SHORT).show()
        }
        fetchTasks()
    }

    private fun fetchTasks() {
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tasks = RetrofitClient.instance.getTasks(currentUserId)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    splitAndDisplay(tasks)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        context,
                        "Could not load tasks right now.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun splitAndDisplay(tasks: List<Task>) {
        val incompleteTasks = tasks
            .filter { !it.is_completed }
            .sortedWith(compareBy<Task> { dueDateTimestamp(it.dueDate) ?: Long.MAX_VALUE }.thenBy { it.title.lowercase(Locale.US) })
        val completedTasks = tasks.filter { it.is_completed }
        val dueSoonCount = incompleteTasks.count { isDueSoon(it.dueDate) }

        // Update incomplete adapter
        incompleteAdapter.updateData(incompleteTasks)
        completedAdapter.updateData(completedTasks)

        tvActiveCount.text = incompleteTasks.size.toString()
        tvDueSoonCount.text = dueSoonCount.toString()
        tvCompletedCount.text = completedTasks.size.toString()
        tvSectionTitle.text = if (incompleteTasks.isEmpty()) {
            "All Clear"
        } else {
            "Active Tasks"
        }
        tvTaskSummaryCaption.text = when {
            incompleteTasks.isEmpty() && completedTasks.isEmpty() ->
                "Start with one small task or capture ideas in Notebook."
            incompleteTasks.isEmpty() ->
                "Everything is complete. Review notes or plan the next deadline."
            dueSoonCount > 0 ->
                "$dueSoonCount task${if (dueSoonCount == 1) "" else "s"} due in the next 24 hours."
            else ->
                "No urgent deadlines right now. Keep the momentum steady."
        }

        // Show/hide empty state for active tasks
        tvEmptyActive.visibility = if (incompleteTasks.isEmpty()) View.VISIBLE else View.GONE

        // Show/hide completed section
        if (completedTasks.isNotEmpty()) {
            tvHeaderCompleted.visibility = View.VISIBLE
            rvTasksCompleted.visibility = View.VISIBLE
        } else {
            tvHeaderCompleted.visibility = View.GONE
            rvTasksCompleted.visibility = View.GONE
        }
    }

    private fun completeTask(task: Task) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updatedTask = task.copy(is_completed = !task.is_completed)

                val response =
                    RetrofitClient.instance.completeTask(task.id, currentUserId, updatedTask)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, response.message, Toast.LENGTH_SHORT).show()

                    // Update XP display if task was just completed
                    if (!task.is_completed) {
                        currentXp = response.new_xp
                        saveXpToPrefs()
                        updateHeaderUI()
                    }

                    fetchTasks()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Failed to update: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun openTaskNotes(task: Task) {
        findNavController().navigate(
            R.id.notesFragment,
            NotesNavigation.taskArgs(task)
        )
    }

    private fun isDueSoon(raw: String?): Boolean {
        val dueDate = parseDueDate(raw) ?: return false
        val now = Date()
        val nextDay = Date(now.time + 24 * 60 * 60 * 1000)
        return dueDate.after(now) && dueDate.before(nextDay)
    }

    private fun dueDateTimestamp(raw: String?): Long? {
        return parseDueDate(raw)?.time
    }

    private fun parseDueDate(raw: String?): Date? {
        if (raw.isNullOrBlank()) {
            return null
        }

        var cleanRaw = raw.trim()
        if (cleanRaw.endsWith("Z")) {
            cleanRaw = cleanRaw.dropLast(1)
        }
        cleanRaw = cleanRaw.replace(Regex("[+-]\\d{2}:\\d{2}$"), "")
        cleanRaw = cleanRaw.replace(Regex("(\\.\\d{3})\\d+"), "$1")

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd"
        )

        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(cleanRaw)
            }.getOrNull()
        }
    }
}
