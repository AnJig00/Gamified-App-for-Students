package com.example.meetmerit

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.DELETE
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.Part
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query


data class RegisterRequest(val email: String, val username: String, val password: String)
data class RegisterResponse(val message: String, val id: Int, val username: String, val email: String)

data class LoginRequest(val username: String, val password: String)
data class LoginResponse(
    val message: String,
    val user_id: Int,
    val username: String,
    val token: String? = null,
    val current_xp: Int = 0,
    val level: Int = 1
)

data class Task(
    val id: Int,
    val title: String,
    val is_completed: Boolean,
    @SerializedName("due_date")
    val dueDate: String? = null
)

data class Note(
    val id: Int,
    val title: String,
    @SerializedName("content_markdown")
    val contentMarkdown: String,
    @SerializedName("note_type")
    val noteType: String,
    @SerializedName("course_name")
    val courseName: String = "",
    @SerializedName("linked_task")
    val linkedTaskId: Int? = null,
    @SerializedName("linked_timetable_entry")
    val linkedTimetableEntryId: Int? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null
)

data class TimetableEntry(
    val id: Int,
    @SerializedName("course_name")
    val courseName: String,
    @SerializedName("day_of_week")
    val dayOfWeek: Int,
    @SerializedName("start_time")
    val startTime: String,
    @SerializedName("end_time")
    val endTime: String,
    val classroom: String
)

data class LeaderboardEntry(
    val username: String,
    val xp: Int,
    val level: Int
)

data class LeagueStatusResponse(
    @SerializedName("league_code")
    val leagueCode: String,
    @SerializedName("league_name")
    val leagueName: String,
    @SerializedName("league_tier")
    val leagueTier: Int,
    @SerializedName("is_top_league")
    val isTopLeague: Boolean,
    @SerializedName("is_bottom_league")
    val isBottomLeague: Boolean,
    @SerializedName("week_start")
    val weekStart: String,
    @SerializedName("week_end")
    val weekEnd: String,
    @SerializedName("week_label")
    val weekLabel: String,
    @SerializedName("weekly_xp")
    val weeklyXp: Int,
    val rank: Int?,
    val participants: Int,
    @SerializedName("promotion_slots")
    val promotionSlots: Int,
    @SerializedName("relegation_slots")
    val relegationSlots: Int,
    @SerializedName("promotion_cutoff_rank")
    val promotionCutoffRank: Int?,
    @SerializedName("relegation_cutoff_rank")
    val relegationCutoffRank: Int?,
    @SerializedName("points_to_promotion")
    val pointsToPromotion: Int?,
    @SerializedName("points_above_relegation")
    val pointsAboveRelegation: Int?,
    @SerializedName("last_outcome")
    val lastOutcome: String,
    @SerializedName("last_outcome_label")
    val lastOutcomeLabel: String
)

data class LeagueLeaderboardUser(
    val rank: Int,
    val username: String,
    @SerializedName("weekly_xp")
    val weeklyXp: Int,
    val level: Int,
    @SerializedName("is_current_user")
    val isCurrentUser: Boolean
)

data class LeagueLeaderboardResponse(
    @SerializedName("league_code")
    val leagueCode: String,
    @SerializedName("league_name")
    val leagueName: String,
    @SerializedName("week_start")
    val weekStart: String,
    @SerializedName("week_end")
    val weekEnd: String,
    @SerializedName("week_label")
    val weekLabel: String,
    val participants: Int,
    @SerializedName("promotion_slots")
    val promotionSlots: Int,
    @SerializedName("relegation_slots")
    val relegationSlots: Int,
    val entries: List<LeagueLeaderboardUser>
)

data class ProfileResponse(
    val username: String,
    val email: String,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    val department: String = "",
    @SerializedName("year_of_study")
    val yearOfStudy: Int? = null,
    @SerializedName("social_discoverable")
    val socialDiscoverable: Boolean = true,
    @SerializedName("current_xp")
    val currentXp: Int,
    val level: Int,
    val credits: Int,
    @SerializedName("global_rank")
    val globalRank: Int,
    @SerializedName("completed_tasks")
    val completedTasks: Int,
    @SerializedName("league_name")
    val leagueName: String,
    @SerializedName("weekly_xp")
    val weeklyXp: Int,
    @SerializedName("last_outcome_label")
    val lastOutcomeLabel: String,
    @SerializedName("xp_into_level")
    val xpIntoLevel: Int,
    @SerializedName("xp_per_level")
    val xpPerLevel: Int,
    @SerializedName("xp_remaining_to_next_level")
    val xpRemainingToNextLevel: Int,
    @SerializedName("progress_percent")
    val progressPercent: Int
)

data class FocusRequest(
    val user_id: Int,
    val minutes: Int
)

data class FocusResponse(
    val message: String,
    val new_xp: Int,
    val weekly_xp: Int? = null,
    @SerializedName("awarded_xp")
    val awardedXp: Int = 0,
    @SerializedName("requested_minutes")
    val requestedMinutes: Int = 0,
    @SerializedName("rewarded_minutes_today")
    val rewardedMinutesToday: Int = 0,
    @SerializedName("daily_focus_xp_limit")
    val dailyFocusXpLimit: Int = 0,
    @SerializedName("daily_limit_reached")
    val dailyLimitReached: Boolean = false,
)

data class TaskCompleteResponse(
    val message: String,
    val new_xp: Int,
    val weekly_xp: Int? = null
)

data class SocialPresenceStartResponse(
    val token: String,
    @SerializedName("expires_at")
    val expiresAt: String,
    @SerializedName("ttl_seconds")
    val ttlSeconds: Int,
    @SerializedName("service_uuid")
    val serviceUuid: String
)

data class SocialResolveRequest(
    val token: String
)

data class SocialResolveResponse(
    @SerializedName("student_id")
    val studentId: Int,
    val username: String,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    val department: String,
    @SerializedName("year_of_study")
    val yearOfStudy: Int?
)

data class SocialConnectRequest(
    @SerializedName("target_student_id")
    val targetStudentId: Int,
    val rssi: Int? = null
)

data class SocialRespondRequest(
    @SerializedName("request_id")
    val requestId: Int,
    val action: String
)

data class SocialFriendRequest(
    @SerializedName("request_id")
    val requestId: Int
)

data class SocialConnectionRequest(
    val id: Int,
    @SerializedName("from_student")
    val fromStudentId: Int,
    @SerializedName("from_username")
    val fromUsername: String,
    @SerializedName("from_avatar_url")
    val fromAvatarUrl: String? = null,
    @SerializedName("from_department")
    val fromDepartment: String = "",
    @SerializedName("from_year_of_study")
    val fromYearOfStudy: Int? = null,
    @SerializedName("to_student")
    val toStudentId: Int,
    @SerializedName("to_username")
    val toUsername: String,
    @SerializedName("to_avatar_url")
    val toAvatarUrl: String? = null,
    @SerializedName("to_department")
    val toDepartment: String = "",
    @SerializedName("to_year_of_study")
    val toYearOfStudy: Int? = null,
    val status: String,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("responded_at")
    val respondedAt: String? = null
)

data class SocialConnectResponse(
    val message: String,
    val request: SocialConnectionRequest
)

data class SocialConfirmResponse(
    val message: String,
    val request: SocialConnectionRequest,
    @SerializedName("encounter_id")
    val encounterId: Int = 0,
    @SerializedName("xp_awarded")
    val xpAwarded: Boolean = false,
    @SerializedName("recipient_new_xp")
    val recipientNewXp: Int = 0,
    @SerializedName("sender_new_xp")
    val senderNewXp: Int = 0
)

data class SocialRequestsResponse(
    val incoming: List<SocialConnectionRequest>,
    val outgoing: List<SocialConnectionRequest>
)

data class SocialFriendSummary(
    val id: Int,
    @SerializedName("friend_id")
    val friendId: Int,
    val username: String,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    val department: String,
    @SerializedName("year_of_study")
    val yearOfStudy: Int? = null,
    @SerializedName("created_at")
    val createdAt: String
)

data class SocialFriendsResponse(
    val friends: List<SocialFriendSummary>
)

data class AvatarUploadResponse(
    val message: String,
    @SerializedName("avatar_url")
    val avatarUrl: String?
)

data class Friendship(
    val id: Int,
    @SerializedName("student_a")
    val studentAId: Int,
    @SerializedName("student_a_username")
    val studentAUsername: String,
    @SerializedName("student_b")
    val studentBId: Int,
    @SerializedName("student_b_username")
    val studentBUsername: String,
    @SerializedName("created_at")
    val createdAt: String
)

data class SocialFriendResponse(
    val message: String,
    val friendship: Friendship
)

interface ApiService {
    @POST("api/register/")
    suspend fun register(@Body request: RegisterRequest): RegisterResponse

    @POST("api/login/")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/tasks/")
    suspend fun getTasks(@Query("user_id") userId: Int): List<Task>

    @POST("api/tasks/")
    suspend fun createTask(@Query("user_id") userId: Int, @Body task: Task): Task

    @PATCH("api/tasks/{id}/")
    suspend fun completeTask(@Path("id") id: Int, @Query("user_id") userId: Int, @Body task: Task): TaskCompleteResponse

    @DELETE("api/tasks/{id}/")
    suspend fun deleteTask(
        @Path("id") id: Int,
        @Query("user_id") userId: Int
    ): Response<Unit>

    @GET("api/notes/")
    suspend fun getNotes(
        @Query("user_id") userId: Int,
        @Query("linked_task_id") linkedTaskId: Int? = null,
        @Query("linked_timetable_entry_id") linkedTimetableEntryId: Int? = null,
        @Query("note_type") noteType: String? = null,
        @Query("course_name") courseName: String? = null,
        @Query("query") query: String? = null
    ): List<Note>

    @POST("api/notes/")
    suspend fun createNote(
        @Query("user_id") userId: Int,
        @Body note: Note
    ): Note

    @PATCH("api/notes/{id}/")
    suspend fun updateNote(
        @Path("id") id: Int,
        @Query("user_id") userId: Int,
        @Body note: Note
    ): Note

    @DELETE("api/notes/{id}/")
    suspend fun deleteNote(
        @Path("id") id: Int,
        @Query("user_id") userId: Int
    ): Response<Unit>

    @GET("api/timetable/")
    suspend fun getTimetable(@Query("user_id") userId: Int): List<TimetableEntry>

    @POST("api/timetable/")
    suspend fun createTimetableEntry(
        @Query("user_id") userId: Int,
        @Body entry: TimetableEntry
    ): TimetableEntry

    @PATCH("api/timetable/{id}/")
    suspend fun updateTimetableEntry(
        @Path("id") id: Int,
        @Query("user_id") userId: Int,
        @Body entry: TimetableEntry
    ): TimetableEntry

    @DELETE("api/timetable/{id}/")
    suspend fun deleteTimetableEntry(
        @Path("id") id: Int,
        @Query("user_id") userId: Int
    ): Response<Unit>

    @GET("api/leaderboard/")
    suspend fun getLeaderboard(): List<LeaderboardEntry>

    @GET("api/profile/")
    suspend fun getProfile(@Query("user_id") userId: Int): ProfileResponse

    @Multipart
    @POST("api/profile/avatar/")
    suspend fun uploadProfileAvatar(
        @Query("user_id") userId: Int,
        @Part avatar: MultipartBody.Part
    ): AvatarUploadResponse

    @GET("api/league/status/")
    suspend fun getLeagueStatus(@Query("user_id") userId: Int): LeagueStatusResponse

    @GET("api/league/leaderboard/")
    suspend fun getLeagueLeaderboard(@Query("user_id") userId: Int): LeagueLeaderboardResponse

    @POST("api/focus/")
    suspend fun addFocusXP(@Body request: FocusRequest): FocusResponse

    @POST("api/social/presence/start/")
    suspend fun startSocialPresence(@Query("user_id") userId: Int): SocialPresenceStartResponse

    @POST("api/social/presence/stop/")
    suspend fun stopSocialPresence(@Query("user_id") userId: Int): retrofit2.Response<Unit>

    @POST("api/social/resolve/")
    suspend fun resolveSocialToken(@Body request: SocialResolveRequest): SocialResolveResponse

    @POST("api/social/connect/")
    suspend fun connectToStudent(
        @Query("user_id") userId: Int,
        @Body request: SocialConnectRequest
    ): SocialConnectResponse

    @POST("api/social/confirm/")
    suspend fun confirmSocialRequest(
        @Query("user_id") userId: Int,
        @Body request: SocialRespondRequest
    ): SocialConfirmResponse

    @GET("api/social/requests/")
    suspend fun getSocialRequests(@Query("user_id") userId: Int): SocialRequestsResponse

    @GET("api/social/friends/")
    suspend fun getFriends(@Query("user_id") userId: Int): SocialFriendsResponse

    @POST("api/social/friends/")
    suspend fun addFriend(
        @Query("user_id") userId: Int,
        @Body request: SocialFriendRequest
    ): SocialFriendResponse
}


object RetrofitClient {
    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}
