package com.example.meetmerit

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    private lateinit var tvUsername: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvStatXp: TextView
    private lateinit var tvStatLevel: TextView
    private lateinit var tvStatRank: TextView
    private lateinit var btnLogout: MaterialButton

    // Badge icons
    private lateinit var tvBadge1Icon: TextView
    private lateinit var tvBadge2Icon: TextView
    private lateinit var tvBadge3Icon: TextView
    private lateinit var tvBadge4Icon: TextView

    private var currentUsername: String? = null
    private var currentUserId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind views
        tvUsername = view.findViewById(R.id.tv_username)
        tvEmail = view.findViewById(R.id.tvProfileEmail)
        tvStatXp = view.findViewById(R.id.tvStatXpValue)
        tvStatLevel = view.findViewById(R.id.tvStatLevelValue)
        tvStatRank = view.findViewById(R.id.tvStatRankValue)
        btnLogout = view.findViewById(R.id.btn_logout)

        tvBadge1Icon = view.findViewById(R.id.tvBadge1Icon)
        tvBadge2Icon = view.findViewById(R.id.tvBadge2Icon)
        tvBadge3Icon = view.findViewById(R.id.tvBadge3Icon)
        tvBadge4Icon = view.findViewById(R.id.tvBadge4Icon)

        // Load user info from SharedPreferences
        val prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        currentUsername = prefs.getString("USERNAME", null)
        currentUserId = prefs.getInt("USER_ID", -1)

        // Populate username
        tvUsername.text = currentUsername ?: "Student"
        tvEmail.text = "Keep learning, keep growing!"

        // Set initial placeholder values
        tvStatXp.text = "—"
        tvStatLevel.text = "—"
        tvStatRank.text = "—"

        // Fetch live stats from leaderboard API
        fetchProfileStats()

        // Logout
        btnLogout.setOnClickListener {
            performLogout()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun fetchProfileStats() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ranks = RetrofitClient.instance.getLeaderboard()

                withContext(Dispatchers.Main) {
                    val username = currentUsername ?: return@withContext

                    val index = ranks.indexOfFirst {
                        it.username.equals(username, ignoreCase = true)
                    }

                    if (index >= 0) {
                        val entry = ranks[index]
                        tvStatXp.text = "${entry.xp}"
                        tvStatLevel.text = "${entry.level}"
                        tvStatRank.text = "#${index + 1}"

                        // Highlight earned badges based on stats
                        highlightBadges(entry.xp, entry.level, index + 1)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Could not load stats", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun highlightBadges(xp: Int, level: Int, rank: Int) {
        val ctx = context ?: return
        val earnedTint = ContextCompat.getColor(ctx, R.color.md_primary_container)

        // Badge 1: First Task — earned if user has any XP
        if (xp > 0) {
            tvBadge1Icon.background.setTint(earnedTint)
        }

        // Badge 2: Focus Pro — earned at level 3+
        if (level >= 3) {
            tvBadge2Icon.background.setTint(earnedTint)
        }

        // Badge 3: Social Bee — earned at 100+ XP (has interacted socially)
        if (xp >= 100) {
            tvBadge3Icon.background.setTint(earnedTint)
        }

        // Badge 4: Top 10 — earned if ranked in top 10
        if (rank <= 10) {
            tvBadge4Icon.background.setTint(earnedTint)
        }
    }

    private fun performLogout() {
        val sharedPreferences = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()

        val intent = Intent(requireActivity(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)

        requireActivity().finish()
    }
}
