package com.example.meetmerit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView

enum class NearbyStudentAction {
    CONNECT,
    ACCEPT,
    PENDING,
    ADD_FRIEND,
    FRIEND
}

data class NearbyStudent(
    val token: String,
    val studentId: Int? = null,
    val username: String = "Loading...",
    val avatarUrl: String? = null,
    val department: String = "",
    val yearOfStudy: Int? = null,
    val rssi: Int = -100,
    val lastSeenAt: Long = 0L,
    val requestId: Int? = null,
    val action: NearbyStudentAction = NearbyStudentAction.CONNECT,
    val isResolving: Boolean = false,
)

class NearbyStudentAdapter(
    private var students: List<NearbyStudent>,
    private val onStudentClick: (NearbyStudent) -> Unit,
) : RecyclerView.Adapter<NearbyStudentAdapter.NearbyStudentViewHolder>() {

    inner class NearbyStudentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ShapeableImageView = view.findViewById(R.id.ivDeviceAvatar)
        val tvName: TextView = view.findViewById(R.id.tvDeviceName)
        val tvSubtitle: TextView = view.findViewById(R.id.tvDeviceAddress)
        val btnAction: MaterialButton = view.findViewById(R.id.btnConfirm)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NearbyStudentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return NearbyStudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: NearbyStudentViewHolder, position: Int) {
        val student = students[position]
        holder.ivAvatar.loadAvatar(student.avatarUrl)
        holder.tvName.text = student.username
        holder.tvSubtitle.text = buildSubtitle(student)
        holder.btnAction.text = actionLabel(student)

        val actionable = !student.isResolving &&
            student.action != NearbyStudentAction.PENDING &&
            student.action != NearbyStudentAction.FRIEND

        holder.btnAction.isEnabled = actionable
        holder.itemView.isEnabled = actionable
        holder.itemView.alpha = if (student.isResolving) 0.75f else 1f

        if (actionable) {
            holder.itemView.setOnClickListener { onStudentClick(student) }
        } else {
            holder.itemView.setOnClickListener(null)
        }
    }

    override fun getItemCount() = students.size

    fun updateData(newStudents: List<NearbyStudent>) {
        students = newStudents
        notifyDataSetChanged()
    }

    fun hasStudents(): Boolean = students.isNotEmpty()

    private fun buildSubtitle(student: NearbyStudent): String {
        val detailParts = mutableListOf<String>()
        if (student.department.isNotBlank()) {
            detailParts += student.department
        }
        student.yearOfStudy?.let { detailParts += "Year $it" }
        detailParts += proximityLabel(student.rssi)
        return detailParts.joinToString(" - ")
    }

    private fun actionLabel(student: NearbyStudent): String {
        if (student.isResolving) {
            return "Loading"
        }
        return when (student.action) {
            NearbyStudentAction.CONNECT -> "Connect"
            NearbyStudentAction.ACCEPT -> "Accept"
            NearbyStudentAction.PENDING -> "Pending"
            NearbyStudentAction.ADD_FRIEND -> "Add Friend"
            NearbyStudentAction.FRIEND -> "Friend"
        }
    }

    private fun proximityLabel(rssi: Int): String {
        return when {
            rssi >= -60 -> "Very close"
            rssi >= -72 -> "Nearby"
            else -> "Recently seen"
        }
    }
}
