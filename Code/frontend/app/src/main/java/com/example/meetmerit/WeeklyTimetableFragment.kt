package com.example.meetmerit

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class WeeklyTimetableFragment : Fragment() {

    private lateinit var adapter: TimetableAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var tvWeeklyClassCount: TextView
    private lateinit var tvActiveDayCount: TextView
    private lateinit var tvNextClassSummary: TextView
    private var currentUserId: Int = -1

    private val dayOptions = listOf(
        "Mon" to 1,
        "Tue" to 2,
        "Wed" to 3,
        "Thu" to 4,
        "Fri" to 5,
        "Sat" to 6,
        "Sun" to 7
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
        tvWeeklyClassCount = view.findViewById(R.id.tvWeeklyClassCount)
        tvActiveDayCount = view.findViewById(R.id.tvActiveDayCount)
        tvNextClassSummary = view.findViewById(R.id.tvNextClassSummary)

        val btnAddClass = view.findViewById<MaterialButton>(R.id.btnAddClass)
        val btnNotes = view.findViewById<MaterialButton>(R.id.btnTimetableNotes)
        val btnBackTimetable = view.findViewById<ImageButton>(R.id.btnBackTimetable)
        val rvTimetable = view.findViewById<RecyclerView>(R.id.rvTimetable)

        val prefs = activity?.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        currentUserId = prefs?.getInt("USER_ID", -1) ?: -1

        adapter = TimetableAdapter(
            sections = emptyList(),
            onEntryClick = { entry -> showTimetableEntryDialog(entry) },
            onEntryDelete = { entry -> deleteTimetableEntry(entry) },
            onEntryNotesClick = { entry -> openClassNotes(entry) }
        )
        rvTimetable.layoutManager = LinearLayoutManager(context)
        rvTimetable.adapter = adapter

        btnAddClass.setOnClickListener {
            showTimetableEntryDialog(null)
        }
        btnNotes.setOnClickListener {
            findNavController().navigate(R.id.notesFragment, NotesNavigation.notebookArgs())
        }
        btnBackTimetable.setOnClickListener {
            findNavController().navigateUp()
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
        tvWeeklyClassCount.text = entries.size.toString()
        tvActiveDayCount.text = entries.map { it.dayOfWeek }.distinct().size.toString()
        tvNextClassSummary.text = buildNextClassSummary(entries)

        val sections = dayOptions.map { (label, dayValue) ->
            TimetableDaySection(
                label = label,
                entries = entries.filter { it.dayOfWeek == dayValue }
            )
        }

        adapter.updateData(sections)
    }

    private fun showTimetableEntryDialog(existingEntry: TimetableEntry?) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_timetable_entry, null)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val btnDialogClose = dialogView.findViewById<View>(R.id.btnDialogClose)
        val tilCourseName = dialogView.findViewById<TextInputLayout>(R.id.tilCourseName)
        val etCourseName = dialogView.findViewById<TextInputEditText>(R.id.etCourseName)
        val chipGroupDays = dialogView.findViewById<ChipGroup>(R.id.chipGroupDays)
        val tvDaySelectionError = dialogView.findViewById<TextView>(R.id.tvDaySelectionError)
        val tilStartTime = dialogView.findViewById<TextInputLayout>(R.id.tilStartTime)
        val etStartTime = dialogView.findViewById<TextInputEditText>(R.id.etStartTime)
        val tilEndTime = dialogView.findViewById<TextInputLayout>(R.id.tilEndTime)
        val etEndTime = dialogView.findViewById<TextInputEditText>(R.id.etEndTime)
        val tilClassroom = dialogView.findViewById<TextInputLayout>(R.id.tilClassroom)
        val etClassroom = dialogView.findViewById<TextInputEditText>(R.id.etClassroom)
        val btnDelete = dialogView.findViewById<MaterialButton>(R.id.btnDialogDelete)
        val btnSave = dialogView.findViewById<MaterialButton>(R.id.btnDialogSave)

        val dayChipIds = listOf(
            R.id.chipMonday to 1,
            R.id.chipTuesday to 2,
            R.id.chipWednesday to 3,
            R.id.chipThursday to 4,
            R.id.chipFriday to 5,
            R.id.chipSaturday to 6,
            R.id.chipSunday to 7
        )

        var selectedStartTime = existingEntry?.startTime?.let(TimeOptionUtils::apiToDisplayTime)
        var selectedEndTime = existingEntry?.endTime?.let(TimeOptionUtils::apiToDisplayTime)

        if (existingEntry == null) {
            tvDialogTitle.text = "Add Class"
            btnSave.text = "Add Class"
            btnDelete.visibility = View.GONE
        } else {
            tvDialogTitle.text = "Edit Class"
            btnSave.text = "Save Changes"
            etCourseName.setText(existingEntry.courseName)
            setSelectedDays(chipGroupDays, dayChipIds, listOf(existingEntry.dayOfWeek))
            etStartTime.setText(selectedStartTime)
            etEndTime.setText(selectedEndTime)
            etClassroom.setText(existingEntry.classroom)
            btnDelete.visibility = View.VISIBLE
        }

        chipGroupDays.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                tvDaySelectionError.visibility = View.GONE
            }
        }

        etStartTime.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                tilStartTime.error = null
            }
        }
        etEndTime.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                tilEndTime.error = null
            }
        }
        etStartTime.setOnClickListener { tilStartTime.error = null }
        etEndTime.setOnClickListener { tilEndTime.error = null }

        btnDialogClose.setOnClickListener {
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
            tvDaySelectionError.visibility = View.GONE
            tilStartTime.error = null
            tilEndTime.error = null
            tilClassroom.error = null

            val courseName = etCourseName.text?.toString()?.trim().orEmpty()
            val classroom = etClassroom.text?.toString()?.trim().orEmpty()
            val startTimeRaw = etStartTime.text?.toString()?.trim().orEmpty()
            val endTimeRaw = etEndTime.text?.toString()?.trim().orEmpty()
            selectedStartTime = if (startTimeRaw.isBlank()) {
                null
            } else {
                TimeOptionUtils.normalizeUserDisplayTime(startTimeRaw)
            }
            selectedEndTime = if (endTimeRaw.isBlank()) {
                null
            } else {
                TimeOptionUtils.normalizeUserDisplayTime(endTimeRaw)
            }
            val selectedDays = collectSelectedDays(chipGroupDays, dayChipIds)

            var hasError = false

            if (courseName.isEmpty()) {
                tilCourseName.error = "Course name is required"
                hasError = true
            }

            if (selectedDays.isEmpty()) {
                tvDaySelectionError.visibility = View.VISIBLE
                hasError = true
            }

            if (selectedStartTime.isNullOrBlank()) {
                tilStartTime.error = if (startTimeRaw.isBlank()) {
                    "Start time is required"
                } else {
                    "Use a valid time like 9:00 AM or 14:30"
                }
                hasError = true
            }

            if (selectedEndTime.isNullOrBlank()) {
                tilEndTime.error = if (endTimeRaw.isBlank()) {
                    "End time is required"
                } else {
                    "Use a valid time like 10:30 AM or 16:30"
                }
                hasError = true
            }

            if (classroom.isEmpty()) {
                tilClassroom.error = "Classroom is required"
                hasError = true
            }

            if (!selectedStartTime.isNullOrBlank() && !selectedEndTime.isNullOrBlank()) {
                if (!TimeOptionUtils.isEndAfterStart(selectedStartTime!!, selectedEndTime!!)) {
                    tilEndTime.error = "End time must be later than start time"
                    hasError = true
                }
            }

            if (hasError) {
                return@setOnClickListener
            }

            etStartTime.setText(selectedStartTime)
            etEndTime.setText(selectedEndTime)

            saveTimetableEntries(
                existingEntry = existingEntry,
                selectedDays = selectedDays,
                courseName = courseName,
                startTime = selectedStartTime!!,
                endTime = selectedEndTime!!,
                classroom = classroom
            )
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun saveTimetableEntries(
        existingEntry: TimetableEntry?,
        selectedDays: List<Int>,
        courseName: String,
        startTime: String,
        endTime: String,
        classroom: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (existingEntry == null) {
                    selectedDays.forEach { day ->
                        RetrofitClient.instance.createTimetableEntry(
                            currentUserId,
                            buildTimetableEntry(
                                id = 0,
                                courseName = courseName,
                                dayOfWeek = day,
                                startTime = startTime,
                                endTime = endTime,
                                classroom = classroom
                            )
                        )
                    }
                } else {
                    val primaryDay = selectedDays.first()
                    RetrofitClient.instance.updateTimetableEntry(
                        existingEntry.id,
                        currentUserId,
                        buildTimetableEntry(
                            id = existingEntry.id,
                            courseName = courseName,
                            dayOfWeek = primaryDay,
                            startTime = startTime,
                            endTime = endTime,
                            classroom = classroom
                        )
                    )

                    selectedDays.drop(1).forEach { day ->
                        RetrofitClient.instance.createTimetableEntry(
                            currentUserId,
                            buildTimetableEntry(
                                id = 0,
                                courseName = courseName,
                                dayOfWeek = day,
                                startTime = startTime,
                                endTime = endTime,
                                classroom = classroom
                            )
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    val message = when {
                        existingEntry == null && selectedDays.size == 1 -> "Class added"
                        existingEntry == null -> "Class added to ${selectedDays.size} days"
                        selectedDays.size == 1 -> "Class updated"
                        else -> "Class updated and added to ${selectedDays.size - 1} more days"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    fetchTimetable()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Failed to save class: ${e.message}",
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

    private fun collectSelectedDays(
        chipGroup: ChipGroup,
        dayChipIds: List<Pair<Int, Int>>
    ): List<Int> {
        return dayChipIds
            .filter { (chipId, _) -> chipGroup.findViewById<Chip>(chipId).isChecked }
            .map { it.second }
    }

    private fun setSelectedDays(
        chipGroup: ChipGroup,
        dayChipIds: List<Pair<Int, Int>>,
        selectedDays: List<Int>
    ) {
        dayChipIds.forEach { (chipId, dayValue) ->
            chipGroup.findViewById<Chip>(chipId).isChecked = selectedDays.contains(dayValue)
        }
    }

    private fun buildTimetableEntry(
        id: Int,
        courseName: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        classroom: String
    ): TimetableEntry {
        return TimetableEntry(
            id = id,
            courseName = courseName,
            dayOfWeek = dayOfWeek,
            startTime = TimeOptionUtils.displayToApiTime(startTime),
            endTime = TimeOptionUtils.displayToApiTime(endTime),
            classroom = classroom
        )
    }

    private fun openClassNotes(entry: TimetableEntry) {
        findNavController().navigate(R.id.notesFragment, NotesNavigation.classArgs(entry))
    }

    private fun buildNextClassSummary(entries: List<TimetableEntry>): String {
        if (entries.isEmpty()) {
            return "No classes scheduled yet. Add one to map out the week."
        }

        val now = Calendar.getInstance()
        val currentDay = convertCalendarDayToApp(now.get(Calendar.DAY_OF_WEEK))
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val nextEntry = entries
            .sortedWith(compareBy<TimetableEntry> { distanceFromToday(currentDay, it.dayOfWeek) }.thenBy { timeToMinutes(it.startTime) })
            .firstOrNull { entry ->
                val dayDistance = distanceFromToday(currentDay, entry.dayOfWeek)
                dayDistance > 0 || timeToMinutes(entry.startTime) >= currentMinutes
            }
            ?: entries.minWithOrNull(compareBy<TimetableEntry> { it.dayOfWeek }.thenBy { timeToMinutes(it.startTime) })

        return if (nextEntry == null) {
            "No classes scheduled yet. Add one to map out the week."
        } else {
            "Next class: ${nextEntry.courseName} on ${labelForDay(nextEntry.dayOfWeek)} at ${
                TimeOptionUtils.apiToDisplayTime(nextEntry.startTime)
            } in ${nextEntry.classroom}."
        }
    }

    private fun distanceFromToday(currentDay: Int, targetDay: Int): Int {
        val distance = targetDay - currentDay
        return if (distance >= 0) distance else distance + 7
    }

    private fun convertCalendarDayToApp(calendarDay: Int): Int {
        return when (calendarDay) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    private fun timeToMinutes(apiTime: String): Int {
        val parts = apiTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return hour * 60 + minute
    }

    private fun labelForDay(dayValue: Int): String {
        return dayOptions.firstOrNull { it.second == dayValue }?.first ?: "Day"
    }
}
