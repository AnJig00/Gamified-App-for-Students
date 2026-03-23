package com.example.meetmerit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class LeaderboardAdapter(
    private var entries: List<LeagueLeaderboardUser>
) : RecyclerView.Adapter<LeaderboardAdapter.RankViewHolder>() {

    inner class RankViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardRoot: MaterialCardView = itemView.findViewById(R.id.cardRankItem)
        val tvRank: TextView = itemView.findViewById(R.id.tvRank)
        val tvAvatarInitial: TextView = itemView.findViewById(R.id.tvAvatarInitial)
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
        val rank = entry.rank
        val ctx = holder.itemView.context

        holder.tvRank.text = rank.toString()
        holder.tvAvatarInitial.text = entry.username.firstOrNull()?.uppercase() ?: "?"
        holder.tvUsername.text = if (entry.isCurrentUser) "${entry.username} (You)" else entry.username
        holder.tvLevel.text = "Level ${entry.level}"
        holder.tvXP.text = "${entry.weeklyXp} XP"

        val avatarBackground = when (rank) {
            1 -> R.color.md_medal_gold
            2 -> R.color.md_medal_silver
            3 -> R.color.md_medal_bronze
            else -> R.color.md_primary_container
        }
        val avatarText = if (rank <= 3) R.color.md_on_surface else R.color.md_on_primary_container
        holder.tvAvatarInitial.background.setTint(ContextCompat.getColor(ctx, avatarBackground))
        holder.tvAvatarInitial.setTextColor(ContextCompat.getColor(ctx, avatarText))

        when (rank) {
            1 -> {
                holder.tvRank.background.setTint(ContextCompat.getColor(ctx, R.color.md_medal_gold))
                holder.tvRank.setTextColor(ContextCompat.getColor(ctx, R.color.md_medal_gold_text))
            }
            2 -> {
                holder.tvRank.background.setTint(ContextCompat.getColor(ctx, R.color.md_medal_silver))
                holder.tvRank.setTextColor(ContextCompat.getColor(ctx, R.color.md_medal_silver_text))
            }
            3 -> {
                holder.tvRank.background.setTint(ContextCompat.getColor(ctx, R.color.md_medal_bronze))
                holder.tvRank.setTextColor(ContextCompat.getColor(ctx, R.color.md_medal_bronze_text))
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

        if (entry.isCurrentUser) {
            holder.cardRoot.setCardBackgroundColor(
                ContextCompat.getColor(ctx, R.color.md_primary_container)
            )
            holder.cardRoot.strokeColor = ContextCompat.getColor(ctx, R.color.md_primary)
            holder.cardRoot.strokeWidth = holder.itemView.resources.displayMetrics.density.toInt()
            holder.tvUsername.setTextColor(ContextCompat.getColor(ctx, R.color.md_on_primary_container))
            holder.tvLevel.setTextColor(ContextCompat.getColor(ctx, R.color.md_on_primary_container))
            holder.tvXP.setTextColor(ContextCompat.getColor(ctx, R.color.md_primary_variant))
        } else {
            holder.cardRoot.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.md_surface))
            holder.cardRoot.strokeColor = ContextCompat.getColor(ctx, R.color.md_outline)
            holder.cardRoot.strokeWidth = holder.itemView.resources.displayMetrics.density.toInt()
            holder.tvUsername.setTextColor(ContextCompat.getColor(ctx, R.color.md_on_surface))
            holder.tvLevel.setTextColor(
                ContextCompat.getColor(ctx, R.color.md_on_surface_variant)
            )
            holder.tvXP.setTextColor(ContextCompat.getColor(ctx, R.color.md_primary))
        }
    }

    override fun getItemCount() = entries.size

    fun updateData(newEntries: List<LeagueLeaderboardUser>) {
        entries = newEntries
        notifyDataSetChanged()
    }
}
