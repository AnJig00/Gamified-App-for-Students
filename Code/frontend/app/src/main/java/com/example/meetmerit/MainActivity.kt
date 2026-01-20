package com.example.meetmerit

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        val savedUserId = sharedPreferences.getInt("USER_ID", -1)
        val savedUsername = sharedPreferences.getString("USERNAME", null)

        if (savedUserId != -1 && savedUsername != null) {
            goToHome(savedUserId, savedUsername)
            return
        }

        setContentView(R.layout.activity_main)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvResult = findViewById<TextView>(R.id.tvResult)

        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            tvResult.text = "Status: Connecting..."

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = RetrofitClient.instance.login(LoginRequest(username, password))

                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Welcome ${response.username}!", Toast.LENGTH_SHORT).show()

                        val editor = sharedPreferences.edit()
                        editor.putInt("USER_ID", response.user_id)
                        editor.putString("USERNAME", response.username)
                        editor.apply()

                        goToHome(response.user_id, response.username)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvResult.text = "Error: ${e.message}"
                        Toast.makeText(applicationContext, "Login Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun goToHome(userId: Int, username: String) {
        val intent = Intent(this, HomeActivity::class.java)
        intent.putExtra("USER_ID", userId)
        intent.putExtra("USERNAME", username)
        startActivity(intent)
        finish()
    }
}