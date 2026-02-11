package com.example.meetmerit

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

        currentUserId = activity?.intent?.getIntExtra("USER_ID", -1) ?: -1

        if (currentUserId == -1) {
            Toast.makeText(context, "Error: User ID missing", Toast.LENGTH_SHORT).show()
        }

        // Setup incomplete list
        rvIncomplete.layoutManager = LinearLayoutManager(context)
        incompleteAdapter = TasksAdapter(emptyList()) { task -> completeTask(task) }
        rvIncomplete.adapter = incompleteAdapter

        // Setup completed list
        rvTasksCompleted.layoutManager = LinearLayoutManager(context)
        completedAdapter = TasksAdapter(emptyList()) { task -> completeTask(task) }
        rvTasksCompleted.adapter = completedAdapter

        if (currentUserId != -1) {
            fetchTasks()
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

    private fun showAddTaskDialog() {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_add_task, null)

        val etTitle = dialogView.findViewById<TextInputEditText>(R.id.etDialogTaskTitle)
        val etDueDate = dialogView.findViewById<TextInputEditText>(R.id.etDialogDueDate)
        val tilDueDate = dialogView.findViewById<TextInputLayout>(R.id.tilDueDate)

        var selectedDateApi: String? = null

        val displayFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val apiFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val openDatePicker = {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedDateApi = apiFormat.format(calendar.time)
                    etDueDate.setText(displayFormat.format(calendar.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.minDate = System.currentTimeMillis() - 1000
                show()
            }
        }

        etDueDate.setOnClickListener { openDatePicker() }
        tilDueDate.setEndIconOnClickListener { openDatePicker() }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("New Task")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val title = etTitle.text.toString().trim()
                if (title.isNotEmpty()) {
                    createNewTask(title, selectedDateApi)
                } else {
                    Toast.makeText(context, "Title cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createNewTask(title: String, dueDate: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val newTask = Task(0, title, false, dueDate)
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
