package com.example.meetmerit

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.DELETE
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
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
    val weekly_xp: Int? = null
)

data class TaskCompleteResponse(
    val message: String,
    val new_xp: Int,
    val weekly_xp: Int? = null
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

    @GET("api/league/status/")
    suspend fun getLeagueStatus(@Query("user_id") userId: Int): LeagueStatusResponse

    @GET("api/league/leaderboard/")
    suspend fun getLeagueLeaderboard(@Query("user_id") userId: Int): LeagueLeaderboardResponse

    @POST("api/focus/")
    suspend fun addFocusXP(@Body request: FocusRequest): FocusResponse
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
