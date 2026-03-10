package com.example.meetmerit

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class WeeklyTimetableFragment : Fragment() {

    private lateinit var adapter: TimetableAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var tvWeekSummary: TextView
    private var currentUserId: Int = -1

    private val dayOptions = listOf(
        "Monday" to 1,
        "Tuesday" to 2,
        "Wednesday" to 3,
        "Thursday" to 4,
        "Friday" to 5,
        "Saturday" to 6,
        "Sunday" to 7
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_weekly_timetable, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar = view.findViewById(R.id.progressBarTimetable)
        tvEmptyState = view.findViewById(R.id.tvEmptyTimetable)
        tvWeekSummary = view.findViewById(R.id.tvWeekSummary)

        val btnBack = view.findViewById<ImageButton>(R.id.btnBackTimetable)
        val fabAddClass = view.findViewById<FloatingActionButton>(R.id.fabAddClass)
        val rvTimetable = view.findViewById<RecyclerView>(R.id.rvTimetable)

        val prefs = activity?.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        currentUserId = prefs?.getInt("USER_ID", -1) ?: -1

        adapter = TimetableAdapter(emptyList()) { entry ->
            showTimetableEntryDialog(entry)
        }
        rvTimetable.layoutManager = LinearLayoutManager(context)
        rvTimetable.adapter = adapter

        btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        fabAddClass.setOnClickListener {
            showTimetableEntryDialog(null)
        }

        if (currentUserId == -1) {
            Toast.makeText(context, "Error: User ID missing", Toast.LENGTH_SHORT).show()
            return
        }

        fetchTimetable()
    }

    override fun onResume() {
        super.onResume()
        (activity as? HomeActivity)?.setBottomNavVisibility(false)
    }

    override fun onDestroyView() {
        (activity as? HomeActivity)?.setBottomNavVisibility(true)
        super.onDestroyView()
    }

    private fun fetchTimetable() {
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entries = RetrofitClient.instance.getTimetable(currentUserId)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    renderTimetable(entries)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        context,
                        "Failed to load timetable: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun renderTimetable(entries: List<TimetableEntry>) {
        tvEmptyState.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        tvWeekSummary.text = if (entries.isEmpty()) {
            "No classes scheduled yet"
        } else {
            "${entries.size} classes scheduled this week"
        }

        val listItems = mutableListOf<TimetableListItem>()
        dayOptions.forEach { (label, dayValue) ->
            val dayEntries = entries.filter { it.dayOfWeek == dayValue }
            if (dayEntries.isNotEmpty()) {
                listItems.add(TimetableListItem.DayHeader(label))
                dayEntries.forEach { entry ->
                    listItems.add(TimetableListItem.EntryRow(entry))
                }
            }
        }

        adapter.updateData(listItems)
    }

    private fun showTimetableEntryDialog(existingEntry: TimetableEntry?) {
        val dialog = BottomSheetDialog(requireContext(), R.style.ThemeOverlay_App_BottomSheet)
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_timetable_entry, null)
        dialog.setContentView(dialogView)

        val bottomSheet = dialog.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        bottomSheet?.setBackgroundResource(android.R.color.transparent)

        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tilCourseName = dialogView.findViewById<TextInputLayout>(R.id.tilCourseName)
        val etCourseName = dialogView.findViewById<TextInputEditText>(R.id.etCourseName)
        val tilDayOfWeek = dialogView.findViewById<TextInputLayout>(R.id.tilDayOfWeek)
        val actDayOfWeek = dialogView.findViewById<MaterialAutoCompleteTextView>(R.id.actDayOfWeek)
        val tilStartTime = dialogView.findViewById<TextInputLayout>(R.id.tilStartTime)
        val etStartTime = dialogView.findViewById<TextInputEditText>(R.id.etStartTime)
        val tilEndTime = dialogView.findViewById<TextInputLayout>(R.id.tilEndTime)
        val etEndTime = dialogView.findViewById<TextInputEditText>(R.id.etEndTime)
        val tilClassroom = dialogView.findViewById<TextInputLayout>(R.id.tilClassroom)
        val etClassroom = dialogView.findViewById<TextInputEditText>(R.id.etClassroom)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnDialogCancel)
        val btnDelete = dialogView.findViewById<MaterialButton>(R.id.btnDialogDelete)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnDialogSave)

        val dayLabels = dayOptions.map { it.first }
        actDayOfWeek.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, dayLabels)
        )

        var selectedDayValue = existingEntry?.dayOfWeek
        var selectedStartTime = existingEntry?.startTime?.toDisplayTime()
        var selectedEndTime = existingEntry?.endTime?.toDisplayTime()

        if (existingEntry == null) {
            tvDialogTitle.text = "Add Class"
            btnDelete.visibility = View.GONE
        } else {
            tvDialogTitle.text = "Edit Class"
            btnSave.text = "Update"
            etCourseName.setText(existingEntry.courseName)
            val dayLabel = dayOptions.firstOrNull { it.second == existingEntry.dayOfWeek }?.first.orEmpty()
            actDayOfWeek.setText(
                dayLabel,
                false
            )
            etStartTime.setText(selectedStartTime)
            etEndTime.setText(selectedEndTime)
            etClassroom.setText(existingEntry.classroom)
            btnDelete.visibility = View.VISIBLE
        }

        actDayOfWeek.setOnItemClickListener { _, _, position, _ ->
            tilDayOfWeek.error = null
            selectedDayValue = dayOptions[position].second
        }

        val openStartTimePicker = {
            showTimePicker(selectedStartTime) { selected ->
                selectedStartTime = selected
                tilStartTime.error = null
                etStartTime.setText(selected)
            }
        }

        val openEndTimePicker = {
            showTimePicker(selectedEndTime) { selected ->
                selectedEndTime = selected
                tilEndTime.error = null
                etEndTime.setText(selected)
            }
        }

        etStartTime.setOnClickListener { openStartTimePicker() }
        tilStartTime.setEndIconOnClickListener { openStartTimePicker() }
        etEndTime.setOnClickListener { openEndTimePicker() }
        tilEndTime.setEndIconOnClickListener { openEndTimePicker() }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnDelete.setOnClickListener {
            if (existingEntry != null) {
                deleteTimetableEntry(existingEntry)
                dialog.dismiss()
            }
        }

        btnSave.setOnClickListener {
            tilCourseName.error = null
            tilDayOfWeek.error = null
            tilStartTime.error = null
            tilEndTime.error = null
            tilClassroom.error = null

            val courseName = etCourseName.text?.toString()?.trim().orEmpty()
            val classroom = etClassroom.text?.toString()?.trim().orEmpty()

            var hasError = false

            if (courseName.isEmpty()) {
                tilCourseName.error = "Course name is required"
                hasError = true
            }

            if (selectedDayValue == null) {
                tilDayOfWeek.error = "Please choose a day"
                hasError = true
            }

            if (selectedStartTime.isNullOrBlank()) {
                tilStartTime.error = "Start time is required"
                hasError = true
            }

            if (selectedEndTime.isNullOrBlank()) {
                tilEndTime.error = "End time is required"
                hasError = true
            }

            if (classroom.isEmpty()) {
                tilClassroom.error = "Classroom is required"
                hasError = true
            }

            if (!selectedStartTime.isNullOrBlank() && !selectedEndTime.isNullOrBlank()) {
                if (!isEndTimeLater(selectedStartTime!!, selectedEndTime!!)) {
                    tilEndTime.error = "End time must be later than start time"
                    hasError = true
                }
            }

            if (hasError) {
                return@setOnClickListener
            }

            val timetableEntry = TimetableEntry(
                id = existingEntry?.id ?: 0,
                courseName = courseName,
                dayOfWeek = selectedDayValue!!,
                startTime = selectedStartTime!!.toApiTime(),
                endTime = selectedEndTime!!.toApiTime(),
                classroom = classroom
            )

            if (existingEntry == null) {
                createTimetableEntry(timetableEntry)
            } else {
                updateTimetableEntry(timetableEntry)
            }

            dialog.dismiss()
        }

        dialog.show()
    }

    private fun createTimetableEntry(entry: TimetableEntry) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitClient.instance.createTimetableEntry(currentUserId, entry)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Class added", Toast.LENGTH_SHORT).show()
                    fetchTimetable()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Failed to add class: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun updateTimetableEntry(entry: TimetableEntry) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitClient.instance.updateTimetableEntry(entry.id, currentUserId, entry)

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Class updated", Toast.LENGTH_SHORT).show()
                    fetchTimetable()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Failed to update class: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun deleteTimetableEntry(entry: TimetableEntry) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.instance.deleteTimetableEntry(entry.id, currentUserId)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Class deleted", Toast.LENGTH_SHORT).show()
                        fetchTimetable()
                    } else {
                        Toast.makeText(context, "Failed to delete class", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Failed to delete class: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showTimePicker(initialValue: String?, onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        if (!initialValue.isNullOrBlank()) {
            val parts = initialValue.split(":")
            if (parts.size >= 2) {
                calendar.set(
                    Calendar.HOUR_OF_DAY,
                    parts[0].toIntOrNull() ?: calendar.get(Calendar.HOUR_OF_DAY)
                )
                calendar.set(
                    Calendar.MINUTE,
                    parts[1].toIntOrNull() ?: calendar.get(Calendar.MINUTE)
                )
            }
        }

        val timePicker = android.app.TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                onTimeSelected(String.format(Locale.US, "%02d:%02d", hour, minute))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        )
        timePicker.show()
    }

    private fun isEndTimeLater(startTime: String, endTime: String): Boolean {
        return toMinutes(endTime) > toMinutes(startTime)
    }

    private fun toMinutes(rawTime: String): Int {
        val parts = rawTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return (hour * 60) + minute
    }

    private fun String.toApiTime(): String {
        val parts = split(":")
        if (parts.size < 2) {
            return this
        }
        return String.format(Locale.US, "%02d:%02d:00", parts[0].toInt(), parts[1].toInt())
    }

    private fun String.toDisplayTime(): String {
        val parts = split(":")
        if (parts.size < 2) {
            return this
        }
        return String.format(Locale.US, "%02d:%02d", parts[0].toInt(), parts[1].toInt())
    }
}
