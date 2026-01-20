package com.example.meetmerit

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FocusFragment : Fragment() {

    private lateinit var tvTimer: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var seekBar: SeekBar
    private lateinit var tvSelectedTime: TextView
    private lateinit var layoutSelector: LinearLayout
    private lateinit var btnConnect: Button

    private var timer: CountDownTimer? = null
    private var isTimerRunning = false
    private var currentUserId: Int = -1

    private var selectedMinutes: Int = 25
    private var timeLeftInMillis: Long = 25 * 60 * 1000

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_focus, container, false)

        tvTimer = view.findViewById(R.id.tvTimer)
        btnStart = view.findViewById(R.id.btnStartFocus)
        btnStop = view.findViewById(R.id.btnStopFocus)
        seekBar = view.findViewById(R.id.seekBarTime)
        tvSelectedTime = view.findViewById(R.id.tvSelectedTime)
        layoutSelector = view.findViewById(R.id.layoutTimeSelector)
        btnConnect = view.findViewById(R.id.btnConnectDevice)

        currentUserId = activity?.intent?.getIntExtra("USER_ID", -1) ?: -1

        btnConnect.setOnClickListener {
            try {
                findNavController().navigate(R.id.scanFragment)
            } catch (e: Exception) {
                Toast.makeText(context, "Nav Error: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                var minutes = progress
                if (minutes < 1) minutes = 1

                selectedMinutes = minutes

                tvSelectedTime.text = "Set Duration: $minutes min"

                tvTimer.text = String.format(Locale.US, "%02d:00", minutes)

                timeLeftInMillis = minutes * 60 * 1000L
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnStart.setOnClickListener {
            startTimer()
        }

        btnStop.setOnClickListener {
            stopTimer()
        }

        updateCountDownText()
        return view
    }

    private fun startTimer() {
        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateCountDownText()
            }

            override fun onFinish() {
                isTimerRunning = false
                timeLeftInMillis = 0
                updateCountDownText()

                submitFocusSession()
                resetTimerUI()
            }
        }.start()

        isTimerRunning = true
        btnStart.visibility = View.GONE
        btnStop.visibility = View.VISIBLE
        layoutSelector.visibility = View.GONE
        btnConnect.visibility = View.GONE
    }

    private fun stopTimer() {
        timer?.cancel()
        isTimerRunning = false
        timeLeftInMillis = selectedMinutes * 60 * 1000L
        updateCountDownText()
        resetTimerUI()
        Toast.makeText(context, "Focus session cancelled", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("SetTextI18n")
    private fun resetTimerUI() {
        btnStart.visibility = View.VISIBLE
        btnStop.visibility = View.GONE
        layoutSelector.visibility = View.VISIBLE
        btnConnect.visibility = View.VISIBLE
        timeLeftInMillis = selectedMinutes * 60 * 1000L
        updateCountDownText()
    }

    private fun updateCountDownText() {
        val minutes = (timeLeftInMillis / 1000) / 60
        val seconds = (timeLeftInMillis / 1000) % 60

        val timeFormatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)
        tvTimer.text = timeFormatted
    }

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
                    Toast.makeText(context, "Network Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}