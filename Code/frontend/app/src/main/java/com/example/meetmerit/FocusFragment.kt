package com.example.meetmerit

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FocusFragment : Fragment() {

    // ── Existing view references (IDs preserved) ──
    private lateinit var tvTimer: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvSelectedTime: TextView
    private lateinit var layoutSelector: LinearLayout

    // ── New view references ──
    private lateinit var circularProgress: CircularProgressIndicator
    private lateinit var chipGroup: ChipGroup

    // ── Timer state ──
    private var timer: CountDownTimer? = null
    private var isTimerRunning = false
    private var currentUserId: Int = -1

    private var selectedMinutes: Int = 25
    private var timeLeftInMillis: Long = 25 * 60 * 1000L
    private var totalTimeInMillis: Long = 25 * 60 * 1000L

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_focus, container, false)

        // ── Bind existing IDs ──
        tvTimer = view.findViewById(R.id.tvTimer)
        btnStart = view.findViewById(R.id.btnStartFocus)
        btnStop = view.findViewById(R.id.btnStopFocus)
        tvSelectedTime = view.findViewById(R.id.tvSelectedTime)
        layoutSelector = view.findViewById(R.id.layoutTimeSelector)

        // ── Bind new IDs ──
        circularProgress = view.findViewById(R.id.circularProgress)
        chipGroup = view.findViewById(R.id.chipGroupDuration)

        currentUserId = activity?.intent?.getIntExtra("USER_ID", -1) ?: -1

        // ── ChipGroup duration selection (replaces SeekBar) ──
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                selectedMinutes = when (checkedIds.first()) {
                    R.id.chip25 -> 25
                    R.id.chip45 -> 45
                    R.id.chip60 -> 60
                    else -> 25
                }
                timeLeftInMillis = selectedMinutes * 60 * 1000L
                totalTimeInMillis = timeLeftInMillis
                updateCountDownText()
                circularProgress.setProgressCompat(100, true)
            }
        }

        // ── Init circular progress (full ring = idle state) ──
        circularProgress.max = 100
        circularProgress.progress = 100

        // ── Button listeners ──
        btnStart.setOnClickListener { startTimer() }
        btnStop.setOnClickListener { stopTimer() }

        updateCountDownText()
        return view
    }

    // ────────────────────────────────────────────────────────
    // State 2 → Running
    // ────────────────────────────────────────────────────────
    private fun startTimer() {
        totalTimeInMillis = selectedMinutes * 60 * 1000L

        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateCountDownText()
                updateCircularProgress()
            }

            override fun onFinish() {
                isTimerRunning = false
                timeLeftInMillis = 0
                updateCountDownText()
                circularProgress.setProgressCompat(0, true)

                submitFocusSession()
                resetTimerUI()
            }
        }.start()

        isTimerRunning = true

        // UI → Running state
        btnStart.visibility = View.GONE
        btnStop.visibility = View.VISIBLE
        layoutSelector.visibility = View.GONE

        // Hide bottom navigation for an immersive focus experience
        (activity as? HomeActivity)?.setBottomNavVisibility(false)
    }

    // ────────────────────────────────────────────────────────
    // Give Up → back to Idle
    // ────────────────────────────────────────────────────────
    private fun stopTimer() {
        timer?.cancel()
        isTimerRunning = false
        timeLeftInMillis = selectedMinutes * 60 * 1000L
        updateCountDownText()
        resetTimerUI()
        Toast.makeText(context, "Focus session cancelled", Toast.LENGTH_SHORT).show()
    }

    // ────────────────────────────────────────────────────────
    // State 1 → Idle  (reset everything)
    // ────────────────────────────────────────────────────────
    @SuppressLint("SetTextI18n")
    private fun resetTimerUI() {
        btnStart.visibility = View.VISIBLE
        btnStop.visibility = View.GONE
        layoutSelector.visibility = View.VISIBLE

        timeLeftInMillis = selectedMinutes * 60 * 1000L
        totalTimeInMillis = timeLeftInMillis
        circularProgress.setProgressCompat(100, true)
        updateCountDownText()

        // Restore bottom navigation
        (activity as? HomeActivity)?.setBottomNavVisibility(true)
    }

    // ────────────────────────────────────────────────────────
    // Update helpers
    // ────────────────────────────────────────────────────────
    private fun updateCountDownText() {
        val minutes = (timeLeftInMillis / 1000) / 60
        val seconds = (timeLeftInMillis / 1000) % 60
        tvTimer.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun updateCircularProgress() {
        if (totalTimeInMillis > 0) {
            val progress = (timeLeftInMillis * 100 / totalTimeInMillis).toInt()
            circularProgress.setProgressCompat(progress, true)
        }
    }

    // ────────────────────────────────────────────────────────
    // Submit XP to backend (unchanged logic)
    // ────────────────────────────────────────────────────────
    private fun submitFocusSession() {
        if (currentUserId == -1) return
        val minutesToSend = if (selectedMinutes < 1) 1 else selectedMinutes

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = FocusRequest(currentUserId, minutesToSend)
                val response = RetrofitClient.instance.addFocusXP(request)

                withContext(Dispatchers.Main) {
                    android.app.AlertDialog.Builder(context)
                        .setTitle("Focus Complete!")
                        .setMessage(response.message)
                        .setPositiveButton("Awesome!") { _, _ -> }
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Network Error: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────
    // Cleanup — cancel timer & restore nav if fragment is destroyed
    // ────────────────────────────────────────────────────────
    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        (activity as? HomeActivity)?.setBottomNavVisibility(true)
    }
}
