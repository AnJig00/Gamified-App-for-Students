package com.example.meetmerit

import android.annotation.SuppressLint
import android.content.Context
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
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LeaderboardFragment : Fragment() {

    private lateinit var adapter: LeaderboardAdapter
    private lateinit var progressBar: ProgressBar

    private lateinit var tvLeaderboardSubtitle: TextView
    private lateinit var tvLeagueLabel: TextView
    private lateinit var tvTrophy: TextView
    private lateinit var tvLeagueTitle: TextView
    private lateinit var tvLeagueSubtitle: TextView
    private lateinit var tvLeagueAdvanceHint: TextView
    private lateinit var tvRanksSectionTitle: TextView
    private lateinit var tvSummaryTitle: TextView
    private lateinit var tvSummarySubtitle: TextView

    private lateinit var cardMyRank: MaterialCardView
    private lateinit var tvMyRankPosition: TextView
    private lateinit var tvMyRankUsername: TextView
    private lateinit var tvMyRankXp: TextView

    private var currentUsername: String? = null
    private var currentUserId: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_leaderboard, container, false)

        val rvList = view.findViewById<RecyclerView>(R.id.rvLeaderboard)
        progressBar = view.findViewById(R.id.progressBarLeaderboard)

        tvLeaderboardSubtitle = view.findViewById(R.id.tvLeaderboardSubtitle)
        tvLeagueLabel = view.findViewById(R.id.tvLeagueLabel)
        tvTrophy = view.findViewById(R.id.tvTrophy)
        tvLeagueTitle = view.findViewById(R.id.tvLeagueTitle)
        tvLeagueSubtitle = view.findViewById(R.id.tvLeagueSubtitle)
        tvLeagueAdvanceHint = view.findViewById(R.id.tvLeagueAdvanceHint)
        tvRanksSectionTitle = view.findViewById(R.id.tvRanksSectionTitle)
        tvSummaryTitle = view.findViewById(R.id.tvSummaryTitle)
        tvSummarySubtitle = view.findViewById(R.id.tvSummarySubtitle)

        cardMyRank = view.findViewById(R.id.cardMyRank)
        tvMyRankPosition = view.findViewById(R.id.tvMyRankPosition)
        tvMyRankUsername = view.findViewById(R.id.tvMyRankUsername)
        tvMyRankXp = view.findViewById(R.id.tvMyRankXp)

        val prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        currentUsername = prefs.getString("USERNAME", activity?.intent?.getStringExtra("USERNAME"))
        currentUserId = prefs.getInt("USER_ID", -1)

        rvList.layoutManager = LinearLayoutManager(context)
        adapter = LeaderboardAdapter(emptyList())
        rvList.adapter = adapter

        fetchLeaderboard()

        return view
    }

    private fun fetchLeaderboard() {
        if (currentUserId <= 0) {
            renderSignedOutState()
            return
        }

        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val status = RetrofitClient.instance.getLeagueStatus(currentUserId)
                val leaderboard = RetrofitClient.instance.getLeagueLeaderboard(currentUserId)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    adapter.updateData(leaderboard.entries)
                    bindHeader(status, leaderboard)
                    populateMyRank(status)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "League update failed: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bindHeader(
        status: LeagueStatusResponse,
        leaderboard: LeagueLeaderboardResponse
    ) {
        tvLeaderboardSubtitle.text =
            "Weekly standings reset every Monday. Earn XP from tasks and focus sessions."
        tvLeagueLabel.text = status.weekLabel
        tvLeagueTitle.text = status.leagueName
        tvLeagueSubtitle.text =
            "Week ${status.weekLabel} • ${leaderboard.participants} students in this division"
        tvTrophy.text = status.rank?.let { "#$it" } ?: "L${status.leagueTier}"
        tvRanksSectionTitle.text = "This Week's Standings"

        tvLeagueAdvanceHint.text = when {
            status.rank == null -> "Start earning XP this week to enter the live standings."
            status.isTopLeague && status.rank == 1 ->
                "You're leading the top division. Keep stacking XP to stay there."
            status.isTopLeague ->
                "You're already in the top division. The goal now is a stronger weekly finish."
            status.pointsToPromotion == 0 ->
                "You're currently inside the top ${status.promotionSlots}. Hold your place until settlement."
            status.pointsToPromotion != null ->
                "${status.pointsToPromotion} XP would move you into the promotion zone."
            else ->
                "Top ${status.promotionSlots} students promote when the week closes."
        }

        tvSummaryTitle.text = when (status.lastOutcome) {
            "PROMOTED" -> "Last week moved you up"
            "RELEGATED" -> "Last week dropped you down"
            "STAYED" -> "You held your division"
            else -> "Your first league week is live"
        }

        tvSummarySubtitle.text = buildString {
            append(status.lastOutcomeLabel)
            append(". ")
            append(
                when {
                    status.rank == null -> "Complete a task or finish a focus session to appear in the table."
                    status.pointsToPromotion == 0 && !status.isTopLeague ->
                        "You're in position to be promoted if you keep this pace."
                    status.relegationCutoffRank != null &&
                        status.rank >= status.relegationCutoffRank &&
                        !status.isBottomLeague ->
                        if (status.pointsAboveRelegation != null) {
                            "${status.pointsAboveRelegation} XP would move you back above the drop line."
                        } else {
                            "You're near the drop line. A few more XP could change that."
                        }
                    else -> "Current weekly XP: ${status.weeklyXp}."
                }
            )
        }
    }

    @SuppressLint("SetTextI18n")
    private fun populateMyRank(status: LeagueStatusResponse) {
        val rank = status.rank
        if (rank == null) {
            cardMyRank.visibility = View.GONE
            return
        }

        cardMyRank.visibility = View.VISIBLE
        tvMyRankPosition.text = rank.toString()
        tvMyRankUsername.text = currentUsername ?: "You"
        tvMyRankXp.text = "${status.weeklyXp} XP"
    }

    private fun renderSignedOutState() {
        progressBar.visibility = View.GONE
        cardMyRank.visibility = View.GONE
        tvLeagueLabel.text = "Sign in required"
        tvLeagueTitle.text = "Weekly League"
        tvLeagueSubtitle.text = "Sign in to load your real league position and promotion status."
        tvLeagueAdvanceHint.text = "League standings are tied to your account."
        tvSummaryTitle.text = "League update"
        tvSummarySubtitle.text = "Once you sign in, this screen will show your current weekly division."
    }
}
