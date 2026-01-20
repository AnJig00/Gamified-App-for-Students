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

class LeaderboardFragment : Fragment() {

    private lateinit var adapter: LeaderboardAdapter
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_tasks, container, false)

        val rvList = view.findViewById<RecyclerView>(R.id.rvTasks)
        progressBar = view.findViewById(R.id.progressBar)

        val fab = view.findViewById<View>(R.id.fabAddTask)
        fab.visibility = View.GONE

        rvList.layoutManager = LinearLayoutManager(context)
        adapter = LeaderboardAdapter(emptyList())
        rvList.adapter = adapter

        fetchLeaderboard()

        return view
    }

    private fun fetchLeaderboard() {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ranks = RetrofitClient.instance.getLeaderboard()

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    adapter.updateData(ranks)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Rank Update: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}