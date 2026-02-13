package com.example.meetmerit

import android.content.Intent
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
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.HttpException

class SignUpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val tilEmail = findViewById<TextInputLayout>(R.id.tilEmail)
        val tilUsername = findViewById<TextInputLayout>(R.id.tilUsername)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val tilConfirmPassword = findViewById<TextInputLayout>(R.id.tilConfirmPassword)
        val btnCreateAccount = findViewById<MaterialButton>(R.id.btnCreateAccount)
        val tvError = findViewById<TextView>(R.id.tvError)
        val tvFooter = findViewById<TextView>(R.id.tvFooter)

        // Back button
        btnBack.setOnClickListener { finish() }

        // Footer: "Already have an account? Log in"
        val footerFull = "Already have an account? Log in"
        val spannable = SpannableString(footerFull)
        val linkStart = footerFull.indexOf("Log in")
        val linkEnd = linkStart + "Log in".length
        spannable.setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                startActivity(Intent(this@SignUpActivity, MainActivity::class.java))
                finish()
            }
            override fun updateDrawState(ds: TextPaint) {
                ds.color = Color.parseColor("#3B82F6")
                ds.isUnderlineText = false
            }
        }, linkStart, linkEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        tvFooter.text = spannable
        tvFooter.movementMethod = LinkMovementMethod.getInstance()

        // Create account
        btnCreateAccount.setOnClickListener {
            // Clear previous errors
            tilEmail.error = null
            tilUsername.error = null
            tilPassword.error = null
            tilConfirmPassword.error = null
            tvError.visibility = View.GONE

            val email = etEmail?.text.toString().trim()
            val username = etUsername?.text.toString().trim()
            val password = etPassword?.text.toString().trim()
            val confirmPassword = etConfirmPassword?.text.toString().trim()

            // Validate fields
            if (email.isEmpty()) {
                tilEmail.error = "Email is required"
                return@setOnClickListener
            }
            if (username.isEmpty()) {
                tilUsername.error = "Username is required"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                tilPassword.error = "Password is required"
                return@setOnClickListener
            }
            if (password.length < 6) {
                tilPassword.error = "Password must be at least 6 characters"
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                tilConfirmPassword.error = "Passwords do not match"
                return@setOnClickListener
            }

            btnCreateAccount.isEnabled = false
            btnCreateAccount.text = "Creating account..."

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    RetrofitClient.instance.register(RegisterRequest(email, username, password))

                    withContext(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Account created!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@SignUpActivity, MainActivity::class.java))
                        finish()
                    }
                } catch (e: HttpException) {
                    val errorBody = e.response()?.errorBody()?.string()
                    withContext(Dispatchers.Main) {
                        val message = parseErrorMessage(errorBody)
                        tvError.text = message
                        tvError.visibility = View.VISIBLE
                        btnCreateAccount.isEnabled = true
                        btnCreateAccount.text = "Create account"
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tvError.text = "Connection failed. Please try again."
                        tvError.visibility = View.VISIBLE
                        btnCreateAccount.isEnabled = true
                        btnCreateAccount.text = "Create account"
                    }
                }
            }
        }
    }

    private fun parseErrorMessage(errorBody: String?): String {
        if (errorBody.isNullOrEmpty()) return "Registration failed. Please try again."
        return try {
            val json = JSONObject(errorBody)
            val messages = mutableListOf<String>()
            json.keys().forEach { key ->
                val arr = json.optJSONArray(key)
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        messages.add(arr.getString(i))
                    }
                } else {
                    messages.add(json.getString(key))
                }
            }
            messages.joinToString("\n")
        } catch (e: Exception) {
            "Registration failed. Please try again."
        }
    }
}
