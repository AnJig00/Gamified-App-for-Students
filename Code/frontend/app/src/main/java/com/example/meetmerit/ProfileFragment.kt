package com.example.meetmerit

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.MediaType
import okhttp3.RequestBody

class ProfileFragment : Fragment() {

    private data class BadgeSnapshot(
        val profile: ProfileResponse,
        val noteCount: Int,
        val timetableCount: Int,
        val socialInteractions: Int
    )

    private data class BadgeDefinition(
        val title: String,
        val description: String,
        val requirement: String,
        val iconRes: Int,
        val rarity: BadgeRarity,
        val isUnlocked: (BadgeSnapshot) -> Boolean
    )

    private lateinit var badgeAdapter: BadgeAdapter

    private lateinit var ivProfileAvatar: ShapeableImageView
    private lateinit var tvAvatarInitial: TextView
    private lateinit var tvUsername: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvLeagueChip: TextView
    private lateinit var tvLeagueOutcome: TextView
    private lateinit var tvLevelHeadline: TextView
    private lateinit var tvLevelProgressText: TextView
    private lateinit var progressLevel: LinearProgressIndicator
    private lateinit var tvLevelHint: TextView
    private lateinit var tvStatXp: TextView
    private lateinit var tvStatWeeklyXp: TextView
    private lateinit var tvStatCredits: TextView
    private lateinit var tvStatRank: TextView
    private lateinit var tvLeagueSummaryPrimary: TextView
    private lateinit var tvLeagueSummarySecondary: TextView
    private lateinit var tvBadgesCount: TextView
    private lateinit var btnUploadAvatar: MaterialButton
    private lateinit var btnToggleBadges: MaterialButton
    private lateinit var btnViewAllBadges: MaterialButton
    private lateinit var btnLogout: MaterialButton

    private var currentUsername: String? = null
    private var currentUserId: Int = -1
    private var currentProfile: ProfileResponse? = null
    private var currentBadgeModels: List<ProfileBadgeUiModel> = emptyList()
    private var badgesExpanded = false
    private var isAvatarUploading = false

    private val avatarPickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                uploadAvatar(uri)
            }
        }

    private val badgeDefinitions = listOf(
        BadgeDefinition(
            title = "First Task",
            description = "Finish your first task to start your productivity run.",
            requirement = "Complete 1 task",
            iconRes = android.R.drawable.ic_menu_agenda,
            rarity = BadgeRarity.COMMON,
            isUnlocked = { snapshot -> snapshot.profile.completedTasks >= 1 }
        ),
        BadgeDefinition(
            title = "Task Tactician",
            description = "Show consistency by clearing a full stack of tasks.",
            requirement = "Complete 7 tasks",
            iconRes = android.R.drawable.ic_menu_sort_by_size,
            rarity = BadgeRarity.RARE,
            isUnlocked = { snapshot -> snapshot.profile.completedTasks >= 7 }
        ),
        BadgeDefinition(
            title = "Planner Mode",
            description = "Build out a real week instead of relying on memory.",
            requirement = "Add 5 timetable classes",
            iconRes = android.R.drawable.ic_menu_my_calendar,
            rarity = BadgeRarity.COMMON,
            isUnlocked = { snapshot -> snapshot.timetableCount >= 5 }
        ),
        BadgeDefinition(
            title = "Note Keeper",
            description = "Use the notebook as part of your study flow.",
            requirement = "Create 3 notes",
            iconRes = android.R.drawable.ic_menu_edit,
            rarity = BadgeRarity.RARE,
            isUnlocked = { snapshot -> snapshot.noteCount >= 3 }
        ),
        BadgeDefinition(
            title = "Social Spark",
            description = "Confirm your first nearby interaction in the social screen.",
            requirement = "Complete 1 social interaction",
            iconRes = android.R.drawable.ic_menu_share,
            rarity = BadgeRarity.RARE,
            isUnlocked = { snapshot -> snapshot.socialInteractions >= 1 }
        ),
        BadgeDefinition(
            title = "XP Builder",
            description = "Your study routine is starting to compound.",
            requirement = "Reach 100 total XP",
            iconRes = android.R.drawable.arrow_up_float,
            rarity = BadgeRarity.EPIC,
            isUnlocked = { snapshot -> snapshot.profile.currentXp >= 100 }
        ),
        BadgeDefinition(
            title = "Level 3",
            description = "Push your account past the first real milestone.",
            requirement = "Reach level 3",
            iconRes = android.R.drawable.ic_lock_idle_alarm,
            rarity = BadgeRarity.EPIC,
            isUnlocked = { snapshot -> snapshot.profile.level >= 3 }
        ),
        BadgeDefinition(
            title = "Top 10",
            description = "Break into the top 10 of the global ranking table.",
            requirement = "Reach global top 10",
            iconRes = android.R.drawable.btn_star_big_on,
            rarity = BadgeRarity.LEGENDARY,
            isUnlocked = { snapshot -> snapshot.profile.globalRank in 1..10 }
        ),
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ivProfileAvatar = view.findViewById(R.id.ivProfileAvatar)
        tvAvatarInitial = view.findViewById(R.id.tvAvatarInitial)
        tvUsername = view.findViewById(R.id.tv_username)
        tvEmail = view.findViewById(R.id.tvProfileEmail)
        tvLeagueChip = view.findViewById(R.id.tvLeagueChip)
        tvLeagueOutcome = view.findViewById(R.id.tvLeagueOutcome)
        tvLevelHeadline = view.findViewById(R.id.tvLevelHeadline)
        tvLevelProgressText = view.findViewById(R.id.tvLevelProgressText)
        progressLevel = view.findViewById(R.id.progressLevel)
        tvLevelHint = view.findViewById(R.id.tvLevelHint)
        tvStatXp = view.findViewById(R.id.tvStatXpValue)
        tvStatWeeklyXp = view.findViewById(R.id.tvStatWeeklyXpValue)
        tvStatCredits = view.findViewById(R.id.tvStatCreditsValue)
        tvStatRank = view.findViewById(R.id.tvStatRankValue)
        tvLeagueSummaryPrimary = view.findViewById(R.id.tvLeagueSummaryPrimary)
        tvLeagueSummarySecondary = view.findViewById(R.id.tvLeagueSummarySecondary)
        tvBadgesCount = view.findViewById(R.id.tvBadgesCount)
        btnUploadAvatar = view.findViewById(R.id.btnUploadAvatar)
        btnToggleBadges = view.findViewById(R.id.btnToggleBadges)
        btnViewAllBadges = view.findViewById(R.id.btnViewAllBadges)
        btnLogout = view.findViewById(R.id.btn_logout)

        val rvBadges = view.findViewById<RecyclerView>(R.id.rvBadges)
        badgeAdapter = BadgeAdapter(emptyList()) { badge -> showBadgeDialog(badge) }
        rvBadges.layoutManager = GridLayoutManager(requireContext(), 2)
        rvBadges.adapter = badgeAdapter
        rvBadges.isNestedScrollingEnabled = false

        val prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        currentUsername = prefs.getString("USERNAME", null)
        currentUserId = prefs.getInt("USER_ID", -1)

        val initial = currentUsername?.firstOrNull()?.uppercase() ?: "S"
        tvAvatarInitial.text = initial
        ivProfileAvatar.loadAvatar(null, tvAvatarInitial)
        tvUsername.text = currentUsername ?: "Student"
        tvEmail.text = "Loading profile..."
        tvLeagueChip.text = "League"
        tvLeagueOutcome.text = "Fetching your latest progress."
        tvLevelHeadline.text = "Level --"
        tvLevelProgressText.text = "-- / -- XP"
        tvLevelHint.text = "Profile stats will appear once your account data loads."
        tvStatXp.text = "--"
        tvStatWeeklyXp.text = "--"
        tvStatCredits.text = "--"
        tvStatRank.text = "--"
        tvLeagueSummaryPrimary.text = "Syncing your current league..."
        tvLeagueSummarySecondary.text = "Pulling your latest results from the server."
        tvBadgesCount.text = "0 / ${badgeDefinitions.size}"
        btnToggleBadges.visibility = View.GONE

        btnUploadAvatar.setOnClickListener {
            if (!isAvatarUploading) {
                avatarPickerLauncher.launch("image/*")
            }
        }
        btnToggleBadges.setOnClickListener {
            badgesExpanded = !badgesExpanded
            updateVisibleBadges()
        }
        btnViewAllBadges.setOnClickListener { showAllBadgesDialog() }
        btnLogout.setOnClickListener { showLogoutConfirmation() }

        fetchProfileStats()
    }

    @SuppressLint("SetTextI18n")
    private fun fetchProfileStats() {
        if (currentUserId <= 0) {
            Toast.makeText(context, "Please log in again to load your profile.", Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profile = RetrofitClient.instance.getProfile(currentUserId)
                val noteCount = runCatching {
                    RetrofitClient.instance.getNotes(currentUserId).size
                }.getOrDefault(0)
                val timetableCount = runCatching {
                    RetrofitClient.instance.getTimetable(currentUserId).size
                }.getOrDefault(0)
                val socialInteractions = requireActivity()
                    .getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                    .getInt(PREF_SOCIAL_INTERACTIONS, 0)

                val snapshot = BadgeSnapshot(
                    profile = profile,
                    noteCount = noteCount,
                    timetableCount = timetableCount,
                    socialInteractions = socialInteractions
                )

                withContext(Dispatchers.Main) {
                    currentProfile = profile
                    bindProfile(profile)
                    bindBadges(snapshot)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Could not load profile", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bindProfile(profile: ProfileResponse) {
        val prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("USERNAME", profile.username)
            .putInt("CURRENT_XP", profile.currentXp)
            .putInt("LEVEL", profile.level)
            .apply()

        currentUsername = profile.username
        tvAvatarInitial.text = profile.username.firstOrNull()?.uppercase() ?: "S"
        ivProfileAvatar.loadAvatar(profile.avatarUrl, tvAvatarInitial)
        tvUsername.text = profile.username
        tvEmail.text = if (profile.email.isNotBlank()) profile.email else "No email on file yet"
        btnUploadAvatar.text = if (profile.avatarUrl.isNullOrBlank()) "Upload Photo" else "Change Photo"
        tvLeagueChip.text = profile.leagueName
        tvLeagueOutcome.text = profile.lastOutcomeLabel

        tvLevelHeadline.text = "Level ${profile.level}"
        tvLevelProgressText.text = "${profile.xpIntoLevel} / ${profile.xpPerLevel} XP"
        progressLevel.progress = profile.progressPercent
        tvLevelHint.text = "${profile.xpRemainingToNextLevel} XP to reach level ${profile.level + 1}."

        tvStatXp.text = profile.currentXp.toString()
        tvStatWeeklyXp.text = profile.weeklyXp.toString()
        tvStatCredits.text = profile.credits.toString()
        tvStatRank.text = "#${profile.globalRank}"

        tvLeagueSummaryPrimary.text = "${profile.leagueName} • ${profile.weeklyXp} weekly XP"
        tvLeagueSummarySecondary.text =
            "${profile.lastOutcomeLabel}. ${profile.completedTasks} completed task${if (profile.completedTasks == 1) "" else "s"} so far."
    }

    private fun bindBadges(snapshot: BadgeSnapshot) {
        currentBadgeModels = badgeDefinitions.map { badge ->
            val unlocked = badge.isUnlocked(snapshot)
            ProfileBadgeUiModel(
                title = badge.title,
                description = badge.description,
                requirement = badge.requirement,
                statusLine = if (unlocked) {
                    "Unlocked • ${badge.requirement}"
                } else {
                    "Locked • ${badge.requirement}"
                },
                iconRes = badge.iconRes,
                rarity = badge.rarity,
                isUnlocked = unlocked
            )
        }

        val unlockedCount = currentBadgeModels.count { it.isUnlocked }
        tvBadgesCount.text = "$unlockedCount / ${currentBadgeModels.size}"
        updateVisibleBadges()
    }

    @SuppressLint("SetTextI18n")
    private fun updateVisibleBadges() {
        val hasOverflow = currentBadgeModels.size > BADGE_PREVIEW_COUNT
        val visibleBadges = if (badgesExpanded || !hasOverflow) {
            currentBadgeModels
        } else {
            currentBadgeModels.take(BADGE_PREVIEW_COUNT)
        }

        badgeAdapter.updateData(visibleBadges)
        btnToggleBadges.visibility = if (hasOverflow) View.VISIBLE else View.GONE
        btnToggleBadges.text = if (badgesExpanded) {
            "Show Less"
        } else {
            "Show More (${currentBadgeModels.size - BADGE_PREVIEW_COUNT} more)"
        }
    }

    private fun showBadgeDialog(badge: ProfileBadgeUiModel) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(badge.title)
            .setMessage(
                buildString {
                    append(badge.description)
                    append("\n\nRarity: ${badge.rarity.label}")
                    append("\nRequirement: ${badge.requirement}")
                    append("\nStatus: ${if (badge.isUnlocked) "Unlocked" else "Locked"}")
                }
            )
            .setPositiveButton("Close", null)
            .show()
    }

    private fun showAllBadgesDialog() {
        val models = currentBadgeModels.ifEmpty {
            badgeDefinitions.map { badge ->
                ProfileBadgeUiModel(
                    title = badge.title,
                    description = badge.description,
                    requirement = badge.requirement,
                    statusLine = "Locked • ${badge.requirement}",
                    iconRes = badge.iconRes,
                    rarity = badge.rarity,
                    isUnlocked = false
                )
            }
        }

        val summary = models.joinToString("\n\n") { badge ->
            buildString {
                append(badge.title)
                append("\n")
                append(badge.rarity.label)
                append("\n")
                append(badge.requirement)
                append("\n")
                append(if (badge.isUnlocked) "Unlocked" else "Locked")
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Badge Details")
            .setMessage(summary)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showLogoutConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Log out?")
            .setMessage("Your account progress stays on the server. This only clears the current device session.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Log out") { _, _ ->
                performLogout()
            }
            .show()
    }

    private fun performLogout() {
        val sharedPreferences =
            requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        sharedPreferences.edit().clear().apply()

        val intent = Intent(requireActivity(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)

        requireActivity().finish()
    }

    private fun uploadAvatar(uri: Uri) {
        if (currentUserId <= 0) {
            Toast.makeText(context, "Please log in again to upload an avatar.", Toast.LENGTH_SHORT).show()
            return
        }

        val resolver = requireContext().contentResolver
        val mimeType = resolver.getType(uri) ?: "image/*"
        val fileName = queryDisplayName(uri) ?: "avatar-upload"
        val avatarBytes = try {
            resolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }

        if (avatarBytes == null) {
            Toast.makeText(context, "Could not read the selected image.", Toast.LENGTH_SHORT).show()
            return
        }
        if (avatarBytes.size > MAX_AVATAR_BYTES) {
            Toast.makeText(context, "Avatar images must be 2 MB or smaller.", Toast.LENGTH_SHORT).show()
            return
        }

        val requestBody = RequestBody.create(MediaType.parse(mimeType), avatarBytes)
        val avatarPart = MultipartBody.Part.createFormData("avatar", fileName, requestBody)

        isAvatarUploading = true
        btnUploadAvatar.isEnabled = false
        btnUploadAvatar.text = "Uploading..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.instance.uploadProfileAvatar(currentUserId, avatarPart)
                withContext(Dispatchers.Main) {
                    isAvatarUploading = false
                    btnUploadAvatar.isEnabled = true
                    val profile = currentProfile?.copy(avatarUrl = response.avatarUrl)
                    if (profile != null) {
                        currentProfile = profile
                        bindProfile(profile)
                    } else {
                        fetchProfileStats()
                    }
                    Toast.makeText(context, "Avatar updated.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isAvatarUploading = false
                    btnUploadAvatar.isEnabled = true
                    btnUploadAvatar.text = if (currentProfile?.avatarUrl.isNullOrBlank()) {
                        "Upload Photo"
                    } else {
                        "Change Photo"
                    }
                    Toast.makeText(context, "Could not upload avatar.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val resolver = requireContext().contentResolver
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getString(index)
                }
            }
        }
        return null
    }

    companion object {
        private const val BADGE_PREVIEW_COUNT = 4
        private const val MAX_AVATAR_BYTES = 2 * 1024 * 1024
        private const val PREF_SOCIAL_INTERACTIONS = "SOCIAL_INTERACTIONS"
    }
}
