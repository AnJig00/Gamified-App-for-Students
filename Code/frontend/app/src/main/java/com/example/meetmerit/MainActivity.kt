package com.example.meetmerit

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        setContentView(R.layout.activity_main)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val tvResult = findViewById<TextView>(R.id.tvResult)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        val tvFooter = findViewById<TextView>(R.id.tvFooter)

        // Back button -> return to Welcome
        btnBack.setOnClickListener { finish() }

        // Forgot password
        tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "Password reset coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Footer: "Don't have an account? Create account"
        val footerFull = "Don't have an account? Create account"
        val spannable = SpannableString(footerFull)
        val linkStart = footerFull.indexOf("Create account")
        val linkEnd = linkStart + "Create account".length
        spannable.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                startActivity(Intent(this@MainActivity, SignUpActivity::class.java))
                finish()
            }
            override fun updateDrawState(ds: TextPaint) {
                ds.color = Color.parseColor("#3B82F6")
                ds.isUnderlineText = false
            }
        }, linkStart, linkEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        tvFooter.text = spannable
        tvFooter.movementMethod = LinkMovementMethod.getInstance()

        // Login
        btnLogin.setOnClickListener {
            val username = etUsername?.text.toString().trim()
            val password = etPassword?.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "Logging in..."

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = RetrofitClient.instance.login(LoginRequest(username, password))

                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Welcome ${response.username}!", Toast.LENGTH_SHORT).show()

                        val editor = sharedPreferences.edit()
                        editor.putInt("USER_ID", response.user_id)
                        editor.putString("USERNAME", response.username)
                        editor.putInt("CURRENT_XP", response.current_xp)
                        editor.putInt("LEVEL", response.level)
                        editor.apply()

                        goToHome(response.user_id, response.username)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvResult.visibility = View.VISIBLE
                        tvResult.text = "Login failed. Please check your credentials."
                        btnLogin.isEnabled = true
                        btnLogin.text = "Log in"
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
