package com.example.meetmerit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auto-login: if already logged in, skip straight to Home
        val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val savedUserId = prefs.getInt("USER_ID", -1)
        val savedUsername = prefs.getString("USERNAME", null)

        if (savedUserId != -1 && savedUsername != null) {
            val intent = Intent(this, HomeActivity::class.java)
            intent.putExtra("USER_ID", savedUserId)
            intent.putExtra("USERNAME", savedUsername)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_welcome)

        findViewById<MaterialButton>(R.id.btnSignUp).setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnLogIn).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
    }
}
