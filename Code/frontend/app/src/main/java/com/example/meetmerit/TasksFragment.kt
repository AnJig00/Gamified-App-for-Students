package com.example.meetmerit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
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
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TasksFragment : Fragment() {

    private lateinit var incompleteAdapter: TasksAdapter
    private lateinit var completedAdapter: TasksAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvHeaderCompleted: TextView
    private lateinit var rvTasksCompleted: RecyclerView
    private lateinit var tvEmptyActive: TextView
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

        // Header
        tvGreeting = view.findViewById(R.id.tvGreeting)
        tvLevelXp = view.findViewById(R.id.tvLevelXp)
        xpProgressBar = view.findViewById(R.id.xpProgressBar)
        val btnCalendar = view.findViewById<ImageButton>(R.id.btnCalendar)

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
        incompleteAdapter = TasksAdapter(emptyList()) { task -> completeTask(task) }
        rvIncomplete.adapter = incompleteAdapter

        rvTasksCompleted.layoutManager = LinearLayoutManager(context)
        completedAdapter = TasksAdapter(emptyList()) { task -> completeTask(task) }
        rvTasksCompleted.adapter = completedAdapter

        if (currentUserId != -1) {
            fetchTasks()
        }

        btnCalendar.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_tasks_to_weeklyTimetableFragment)
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
            ForegroundColorSpan(Color.parseColor("#4FC3F7")),
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
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnDialogCancel)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnDialogSave)

        var selectedDateTimeISO: String? = null
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val displayFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        // Calendar object that accumulates the user's picks
        val pickedCal = Calendar.getInstance()

        val openDatePicker = {
            val now = Calendar.getInstance()
            val datePicker = DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    // Set picked date, default time to 23:59
                    pickedCal.set(Calendar.YEAR, y)
                    pickedCal.set(Calendar.MONTH, m)          // 0-indexed, same as DatePicker
                    pickedCal.set(Calendar.DAY_OF_MONTH, d)
                    pickedCal.set(Calendar.HOUR_OF_DAY, 23)
                    pickedCal.set(Calendar.MINUTE, 59)
                    pickedCal.set(Calendar.SECOND, 0)
                    pickedCal.set(Calendar.MILLISECOND, 0)

                    // Pre-set with default 23:59 immediately
                    selectedDateTimeISO = isoFormat.format(pickedCal.time)
                    etDueDate.setText(displayFormat.format(pickedCal.time))

                    // Open TimePicker so user can optionally change the time
                    val timePicker = TimePickerDialog(
                        requireContext(),
                        { _, h, min ->
                            pickedCal.set(Calendar.HOUR_OF_DAY, h)
                            pickedCal.set(Calendar.MINUTE, min)
                            selectedDateTimeISO = isoFormat.format(pickedCal.time)
                            etDueDate.setText(displayFormat.format(pickedCal.time))
                        },
                        23, 59, true
                    )
                    // If user cancels TimePicker, the default 23:59 is already set
                    timePicker.show()
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

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val title = etTitle.text.toString().trim()
            if (title.isNotEmpty()) {
                createNewTask(title, selectedDateTimeISO)
                dialog.dismiss()
            } else {
                dialogView.findViewById<TextInputLayout>(R.id.tilTaskTitle)?.error = "Title required"
            }
        }

        dialog.show()
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
                }
            }
        }
    }

    private fun splitAndDisplay(tasks: List<Task>) {
        val incompleteTasks = tasks.filter { !it.is_completed }
        val completedTasks = tasks.filter { it.is_completed }

        // Update incomplete adapter
        incompleteAdapter.updateData(incompleteTasks)

        // Show/hide empty state for active tasks
        tvEmptyActive.visibility = if (incompleteTasks.isEmpty()) View.VISIBLE else View.GONE

        // Show/hide completed section
        if (completedTasks.isNotEmpty()) {
            tvHeaderCompleted.visibility = View.VISIBLE
            rvTasksCompleted.visibility = View.VISIBLE
            completedAdapter.updateData(completedTasks)
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
}
