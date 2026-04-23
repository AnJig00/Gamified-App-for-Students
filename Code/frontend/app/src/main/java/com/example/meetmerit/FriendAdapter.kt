package com.example.meetmerit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView

data class SocialFriendItem(
    val friendshipId: Int,
    val friendId: Int,
    val username: String,
    val avatarUrl: String? = null,
    val department: String = "",
    val yearOfStudy: Int? = null,
)

class FriendAdapter(
    private var friends: List<SocialFriendItem>,
) : RecyclerView.Adapter<FriendAdapter.FriendViewHolder>() {

    inner class FriendViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ShapeableImageView = view.findViewById(R.id.ivFriendAvatar)
        val tvName: TextView = view.findViewById(R.id.tvFriendName)
        val tvSubtitle: TextView = view.findViewById(R.id.tvFriendSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_social_friend, parent, false)
        return FriendViewHolder(view)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        val friend = friends[position]
        holder.ivAvatar.loadAvatar(friend.avatarUrl)
        holder.tvName.text = friend.username
        holder.tvSubtitle.text = buildSubtitle(friend)
    }

    override fun getItemCount(): Int = friends.size

    fun updateData(newFriends: List<SocialFriendItem>) {
        friends = newFriends
        notifyDataSetChanged()
    }

    fun hasFriends(): Boolean = friends.isNotEmpty()

    private fun buildSubtitle(friend: SocialFriendItem): String {
        val detailParts = mutableListOf<String>()
        if (friend.department.isNotBlank()) {
            detailParts += friend.department
        }
        friend.yearOfStudy?.let { detailParts += "Year $it" }
        detailParts += "Friend"
        return detailParts.joinToString(" - ")
    }
}
