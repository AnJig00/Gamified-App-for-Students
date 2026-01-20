package com.example.meetmerit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LeaderboardAdapter(
    private var entries: List<LeaderboardEntry>
) : RecyclerView.Adapter<LeaderboardAdapter.RankViewHolder>() {

    inner class RankViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        val tvLevel: TextView = itemView.findViewById(R.id.tvLevel)
        val tvXP: TextView = itemView.findViewById(R.id.tvXP)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RankViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_leaderboard, parent, false)
        return RankViewHolder(view)
    }

    override fun onBindViewHolder(holder: RankViewHolder, position: Int) {
        val entry = entries[position]
        holder.tvUsername.text = "${position + 1}. ${entry.username}"
        holder.tvLevel.text = "Lv.${entry.level}"
        holder.tvXP.text = "${entry.xp} XP"
    }

    override fun getItemCount() = entries.size

    fun updateData(newEntries: List<LeaderboardEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }
}