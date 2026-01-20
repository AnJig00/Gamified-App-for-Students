package com.example.meetmerit

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TasksFragment : Fragment() {

    private lateinit var adapter: TasksAdapter
    private lateinit var progressBar: ProgressBar
    private var currentUserId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tasks, container, false)

        val rvTasks = view.findViewById<RecyclerView>(R.id.rvTasks)
        progressBar = view.findViewById(R.id.progressBar)

        currentUserId = activity?.intent?.getIntExtra("USER_ID", -1) ?: -1

        if (currentUserId == -1) {
            Toast.makeText(context, "Error: User ID missing", Toast.LENGTH_SHORT).show()
        }

        rvTasks.layoutManager = LinearLayoutManager(context)

        adapter = TasksAdapter(emptyList()) { task ->
            completeTask(task)
        }
        rvTasks.adapter = adapter

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
        val input = android.widget.EditText(context)
        input.hint = "Enter task title"
        input.setPadding(50, 40, 50, 40)

        android.app.AlertDialog.Builder(context)
            .setTitle("New Task")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isNotEmpty()) {
                    createNewTask(title)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createNewTask(title: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 构造新任务对象 (id=0 因为后端会生成, completed=false)
                val newTask = Task(0, title, false)
                RetrofitClient.instance.createTask(currentUserId, newTask)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Task Added!", Toast.LENGTH_SHORT).show()
                    fetchTasks() // 刷新列表
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    } // <--- 关键修改：在这里加了一个括号，结束了 createNewTask 函数

    private fun fetchTasks() {
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tasks = RetrofitClient.instance.getTasks(currentUserId)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    adapter.updateData(tasks)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    // Toast.makeText(context, "Error fetching tasks: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
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