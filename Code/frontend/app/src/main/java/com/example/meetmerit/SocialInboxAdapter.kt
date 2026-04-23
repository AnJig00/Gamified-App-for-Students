package com.example.meetmerit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView

enum class SocialInboxPrimaryAction {
    ACCEPT,
    ADD_FRIEND
}

data class SocialInboxItem(
    val requestId: Int,
    val studentId: Int,
    val username: String,
    val avatarUrl: String? = null,
    val department: String = "",
    val yearOfStudy: Int? = null,
    val subtitleSuffix: String,
    val primaryAction: SocialInboxPrimaryAction,
    val showReject: Boolean = false,
    val isResolving: Boolean = false,
)

class SocialInboxAdapter(
    private var items: List<SocialInboxItem>,
    private val onPrimaryAction: (SocialInboxItem) -> Unit,
    private val onRejectAction: (SocialInboxItem) -> Unit,
) : RecyclerView.Adapter<SocialInboxAdapter.SocialInboxViewHolder>() {

    inner class SocialInboxViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ShapeableImageView = view.findViewById(R.id.ivRequestAvatar)
        val tvName: TextView = view.findViewById(R.id.tvRequestName)
        val tvSubtitle: TextView = view.findViewById(R.id.tvRequestSubtitle)
        val btnPrimary: MaterialButton = view.findViewById(R.id.btnRequestPrimary)
        val btnSecondary: MaterialButton = view.findViewById(R.id.btnRequestSecondary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SocialInboxViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_social_request, parent, false)
        return SocialInboxViewHolder(view)
    }

    override fun onBindViewHolder(holder: SocialInboxViewHolder, position: Int) {
        val item = items[position]
        holder.ivAvatar.loadAvatar(item.avatarUrl)
        holder.tvName.text = item.username
        holder.tvSubtitle.text = buildSubtitle(item)
        holder.btnPrimary.text = primaryLabel(item)
        holder.btnPrimary.isEnabled = !item.isResolving
        holder.btnSecondary.visibility = if (item.showReject) View.VISIBLE else View.GONE
        holder.btnSecondary.isEnabled = !item.isResolving
        holder.itemView.alpha = if (item.isResolving) 0.75f else 1f

        holder.btnPrimary.setOnClickListener { onPrimaryAction(item) }
        holder.btnSecondary.setOnClickListener { onRejectAction(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<SocialInboxItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun hasItems(): Boolean = items.isNotEmpty()

    private fun buildSubtitle(item: SocialInboxItem): String {
        val detailParts = mutableListOf<String>()
        if (item.department.isNotBlank()) {
            detailParts += item.department
        }
        item.yearOfStudy?.let { detailParts += "Year $it" }
        detailParts += item.subtitleSuffix
        return detailParts.joinToString(" - ")
    }

    private fun primaryLabel(item: SocialInboxItem): String {
        if (item.isResolving) {
            return "Loading"
        }
        return when (item.primaryAction) {
            SocialInboxPrimaryAction.ACCEPT -> "Accept"
            SocialInboxPrimaryAction.ADD_FRIEND -> "Add Friend"
        }
    }
}
