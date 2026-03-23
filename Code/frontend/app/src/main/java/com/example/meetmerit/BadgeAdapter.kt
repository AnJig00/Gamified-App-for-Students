package com.example.meetmerit

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

enum class BadgeRarity(
    val label: String,
    val backgroundRes: Int,
    val accentColorRes: Int
) {
    COMMON("Common", R.drawable.bg_badge_common, R.color.app_warning_text),
    RARE("Rare", R.drawable.bg_badge_rare, R.color.md_on_secondary_container),
    EPIC("Epic", R.drawable.bg_badge_epic, R.color.app_violet_text),
    LEGENDARY("Legendary", R.drawable.bg_badge_legendary, R.color.md_on_primary_container)
}

data class ProfileBadgeUiModel(
    val title: String,
    val description: String,
    val requirement: String,
    val statusLine: String,
    val iconRes: Int,
    val rarity: BadgeRarity,
    val isUnlocked: Boolean
)

class BadgeAdapter(
    private var badges: List<ProfileBadgeUiModel>,
    private val onBadgeClick: (ProfileBadgeUiModel) -> Unit
) : RecyclerView.Adapter<BadgeAdapter.BadgeViewHolder>() {

    inner class BadgeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val badgeArt: View = itemView.findViewById(R.id.viewBadgeArt)
        val ivBadgeIcon: ImageView = itemView.findViewById(R.id.ivBadgeIcon)
        val tvTitle: TextView = itemView.findViewById(R.id.tvBadgeTitle)
        val tvRarity: TextView = itemView.findViewById(R.id.tvBadgeRarity)
        val tvStatus: TextView = itemView.findViewById(R.id.tvBadgeStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BadgeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile_badge, parent, false)
        return BadgeViewHolder(view)
    }

    override fun onBindViewHolder(holder: BadgeViewHolder, position: Int) {
        val badge = badges[position]
        val context = holder.itemView.context

        holder.badgeArt.setBackgroundResource(
            if (badge.isUnlocked) badge.rarity.backgroundRes else R.drawable.bg_badge_locked
        )
        holder.ivBadgeIcon.setImageResource(badge.iconRes)
        holder.ivBadgeIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(
                context,
                if (badge.isUnlocked) badge.rarity.accentColorRes else R.color.md_on_surface_variant
            )
        )
        holder.tvTitle.text = badge.title
        holder.tvRarity.text = badge.rarity.label
        holder.tvStatus.text = badge.statusLine
        holder.tvStatus.setTextColor(
            ContextCompat.getColor(
                context,
                if (badge.isUnlocked) R.color.md_on_surface else R.color.md_on_surface_variant
            )
        )
        holder.itemView.alpha = if (badge.isUnlocked) 1f else 0.78f
        holder.itemView.setOnClickListener { onBadgeClick(badge) }
    }

    override fun getItemCount(): Int = badges.size

    fun updateData(newBadges: List<ProfileBadgeUiModel>) {
        badges = newBadges
        notifyDataSetChanged()
    }
}
