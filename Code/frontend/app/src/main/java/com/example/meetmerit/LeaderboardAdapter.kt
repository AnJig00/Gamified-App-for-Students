package com.example.meetmerit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class LeaderboardAdapter(
    private var entries: List<LeaderboardEntry>
) : RecyclerView.Adapter<LeaderboardAdapter.RankViewHolder>() {

    inner class RankViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRank: TextView = itemView.findViewById(R.id.tvRank)
        val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        val tvLevel: TextView = itemView.findViewById(R.id.tvLevel)
        val tvXP: TextView = itemView.findViewById(R.id.tvXP)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RankViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard, parent, false)
        return RankViewHolder(view)
    }

    override fun onBindViewHolder(holder: RankViewHolder, position: Int) {
        val entry = entries[position]
        val rank = position + 1
        val ctx = holder.itemView.context

        // Rank badge
        holder.tvRank.text = rank.toString()

        // Medal colors for top 3
        when (rank) {
            1 -> {
                holder.tvRank.background.setTint(
                    ContextCompat.getColor(ctx, R.color.md_medal_gold)
                )
                holder.tvRank.setTextColor(
                    ContextCompat.getColor(ctx, R.color.md_medal_gold_text)
                )
            }
            2 -> {
                holder.tvRank.background.setTint(
                    ContextCompat.getColor(ctx, R.color.md_medal_silver)
                )
                holder.tvRank.setTextColor(
                    ContextCompat.getColor(ctx, R.color.md_medal_silver_text)
                )
            }
            3 -> {
                holder.tvRank.background.setTint(
                    ContextCompat.getColor(ctx, R.color.md_medal_bronze)
                )
                holder.tvRank.setTextColor(
                    ContextCompat.getColor(ctx, R.color.md_medal_bronze_text)
                )
            }
            else -> {
                holder.tvRank.background.setTint(
                    ContextCompat.getColor(ctx, R.color.md_surface_variant)
                )
                holder.tvRank.setTextColor(
                    ContextCompat.getColor(ctx, R.color.md_on_surface_variant)
                )
            }
        }

        // Text fields (EXISTING IDs — logic preserved)
        holder.tvUsername.text = entry.username
        holder.tvLevel.text = "Lv.${entry.level}"
        holder.tvXP.text = "${entry.xp} XP"
    }

    override fun getItemCount() = entries.size

    fun updateData(newEntries: List<LeaderboardEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }
}
