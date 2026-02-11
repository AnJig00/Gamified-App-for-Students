package com.example.meetmerit

import android.annotation.SuppressLint
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

    // My Rank card views
    private lateinit var cardMyRank: MaterialCardView
    private lateinit var tvMyRankPosition: TextView
    private lateinit var tvMyRankUsername: TextView
    private lateinit var tvMyRankXp: TextView

    private var currentUsername: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_leaderboard, container, false)

        val rvList = view.findViewById<RecyclerView>(R.id.rvLeaderboard)
        progressBar = view.findViewById(R.id.progressBarLeaderboard)

        // My Rank card
        cardMyRank = view.findViewById(R.id.cardMyRank)
        tvMyRankPosition = view.findViewById(R.id.tvMyRankPosition)
        tvMyRankUsername = view.findViewById(R.id.tvMyRankUsername)
        tvMyRankXp = view.findViewById(R.id.tvMyRankXp)

        // Get current username from intent
        currentUsername = activity?.intent?.getStringExtra("USERNAME")

        rvList.layoutManager = LinearLayoutManager(context)
        adapter = LeaderboardAdapter(emptyList())
        rvList.adapter = adapter

        fetchLeaderboard()

        return view
    }

    @SuppressLint("SetTextI18n")
    private fun fetchLeaderboard() {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ranks = RetrofitClient.instance.getLeaderboard()

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    adapter.updateData(ranks)
                    populateMyRank(ranks)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Rank Update: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun populateMyRank(ranks: List<LeaderboardEntry>) {
        val username = currentUsername ?: return

        val index = ranks.indexOfFirst {
            it.username.equals(username, ignoreCase = true)
        }

        if (index >= 0) {
            val entry = ranks[index]
            tvMyRankPosition.text = "${index + 1}"
            tvMyRankUsername.text = entry.username
            tvMyRankXp.text = "${entry.xp} XP"
            cardMyRank.visibility = View.VISIBLE
        }
    }
}
