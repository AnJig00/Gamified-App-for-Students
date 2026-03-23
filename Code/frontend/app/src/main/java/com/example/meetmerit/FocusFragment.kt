package com.example.meetmerit

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class FocusFragment : Fragment() {

    private enum class FocusUiState {
        SETUP,
        ACTIVE,
        SUCCESS,
        INTERRUPTED
    }

    private lateinit var layoutFocusSetup: LinearLayout
    private lateinit var layoutFocusActive: LinearLayout
    private lateinit var layoutFocusSuccess: LinearLayout
    private lateinit var layoutFocusInterrupted: LinearLayout

    private lateinit var tvTimerPreview: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvSelectedTime: TextView
    private lateinit var tvSetupReward: TextView
    private lateinit var tvActiveXpHint: TextView
    private lateinit var tvSuccessMessage: TextView
    private lateinit var tvSuccessReward: TextView
    private lateinit var tvInterruptedMessage: TextView

    private lateinit var btnStart: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var btnSuccessReset: MaterialButton
    private lateinit var btnInterruptedReset: MaterialButton

    private lateinit var circularProgress: CircularProgressIndicator
    private lateinit var chipGroup: ChipGroup

    private var timer: CountDownTimer? = null
    private var currentUserId: Int = -1
    private var selectedMinutes: Int = 25
    private var timeLeftInMillis: Long = 25 * 60 * 1000L
    private var totalTimeInMillis: Long = 25 * 60 * 1000L
    private var uiState: FocusUiState = FocusUiState.SETUP

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_focus, container, false)

        layoutFocusSetup = view.findViewById(R.id.layoutFocusSetup)
        layoutFocusActive = view.findViewById(R.id.layoutFocusActive)
        layoutFocusSuccess = view.findViewById(R.id.layoutFocusSuccess)
        layoutFocusInterrupted = view.findViewById(R.id.layoutFocusInterrupted)

        tvTimerPreview = view.findViewById(R.id.tvTimerPreview)
        tvTimer = view.findViewById(R.id.tvTimer)
        tvSelectedTime = view.findViewById(R.id.tvSelectedTime)
        tvSetupReward = view.findViewById(R.id.tvSetupReward)
        tvActiveXpHint = view.findViewById(R.id.tvActiveXpHint)
        tvSuccessMessage = view.findViewById(R.id.tvSuccessMessage)
        tvSuccessReward = view.findViewById(R.id.tvSuccessReward)
        tvInterruptedMessage = view.findViewById(R.id.tvInterruptedMessage)

        btnStart = view.findViewById(R.id.btnStartFocus)
        btnStop = view.findViewById(R.id.btnStopFocus)
        btnSuccessReset = view.findViewById(R.id.btnSuccessReset)
        btnInterruptedReset = view.findViewById(R.id.btnInterruptedReset)

        circularProgress = view.findViewById(R.id.circularProgress)
        chipGroup = view.findViewById(R.id.chipGroupDuration)

        val prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        currentUserId = prefs.getInt("USER_ID", -1)

        circularProgress.max = 100
        circularProgress.progress = 100

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                selectedMinutes = when (checkedIds.first()) {
                    R.id.chip25 -> 25
                    R.id.chip45 -> 45
                    R.id.chip60 -> 60
                    else -> 25
                }
                resetTimerValues()
                bindSessionCopy()
            }
        }

        btnStart.setOnClickListener { startTimer() }
        btnStop.setOnClickListener { stopTimer() }
        btnSuccessReset.setOnClickListener { resetToSetup() }
        btnInterruptedReset.setOnClickListener { resetToSetup() }

        resetTimerValues()
        bindSessionCopy()
        renderState(FocusUiState.SETUP)

        return view
    }

    private fun startTimer() {
        timer?.cancel()
        resetTimerValues()
        renderState(FocusUiState.ACTIVE)

        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateCountDownText()
                updateCircularProgress()
            }

            override fun onFinish() {
                timer = null
                timeLeftInMillis = 0
                updateCountDownText()
                circularProgress.setProgressCompat(0, true)
                tvSuccessMessage.text = "You stayed focused for $selectedMinutes minutes."
                tvSuccessReward.text = "+$selectedMinutes XP"
                renderState(FocusUiState.SUCCESS)
                submitFocusSession()
            }
        }.start()
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
        tvInterruptedMessage.text = "No XP awarded this time. You can start another session when ready."
        renderState(FocusUiState.INTERRUPTED)
    }

    private fun resetToSetup() {
        timer?.cancel()
        timer = null
        resetTimerValues()
        bindSessionCopy()
        renderState(FocusUiState.SETUP)
    }

    private fun renderState(state: FocusUiState) {
        uiState = state

        layoutFocusSetup.visibility =
            if (state == FocusUiState.SETUP) View.VISIBLE else View.GONE
        layoutFocusActive.visibility =
            if (state == FocusUiState.ACTIVE) View.VISIBLE else View.GONE
        layoutFocusSuccess.visibility =
            if (state == FocusUiState.SUCCESS) View.VISIBLE else View.GONE
        layoutFocusInterrupted.visibility =
            if (state == FocusUiState.INTERRUPTED) View.VISIBLE else View.GONE

        val showBottomNav = state == FocusUiState.SETUP
        (activity as? HomeActivity)?.setBottomNavVisibility(showBottomNav)
    }

    private fun resetTimerValues() {
        totalTimeInMillis = selectedMinutes * 60 * 1000L
        timeLeftInMillis = totalTimeInMillis
        circularProgress.setProgressCompat(100, false)
        updateCountDownText()
    }

    private fun bindSessionCopy() {
        val previewText = formatTime((selectedMinutes * 60).toLong())
        tvTimerPreview.text = previewText
        tvSelectedTime.text = "Selected duration • $selectedMinutes min"
        tvSetupReward.text = "Complete $selectedMinutes min to earn $selectedMinutes XP."
        tvActiveXpHint.text = "This session is worth $selectedMinutes XP."
        tvSuccessReward.text = "+$selectedMinutes XP"
    }

    private fun updateCountDownText() {
        tvTimer.text = formatTime(timeLeftInMillis / 1000)
    }

    private fun updateCircularProgress() {
        if (totalTimeInMillis > 0) {
            val progress = (timeLeftInMillis * 100 / totalTimeInMillis).toInt()
            circularProgress.setProgressCompat(progress, true)
        }
    }

    private fun formatTime(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    @SuppressLint("SetTextI18n")
    private fun submitFocusSession() {
        if (currentUserId == -1) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = FocusRequest(currentUserId, selectedMinutes.coerceAtLeast(1))
                RetrofitClient.instance.addFocusXP(request)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Focus session completed, but syncing XP failed.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        timer?.cancel()
        (activity as? HomeActivity)?.setBottomNavVisibility(true)
        super.onDestroyView()
    }
}
