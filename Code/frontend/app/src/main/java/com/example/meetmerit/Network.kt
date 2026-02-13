package com.example.meetmerit

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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

data class LeaderboardEntry(
    val username: String,
    val xp: Int,
    val level: Int
)

data class FocusRequest(
    val user_id: Int,
    val minutes: Int
)

data class FocusResponse(
    val message: String,
    val new_xp: Int
)

data class TaskCompleteResponse(val message: String, val new_xp: Int)

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

    @GET("api/leaderboard/")
    suspend fun getLeaderboard(): List<LeaderboardEntry>

    @POST("api/focus/")
    suspend fun addFocusXP(@Body request: FocusRequest): FocusResponse
}


object RetrofitClient {
    //private const val BASE_URL = "http://10.0.2.2:8000/"  // Virtual
    private const val BASE_URL = "https://meet-merit-app-fc548669a22d.herokuapp.com/"   //Real
    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}
