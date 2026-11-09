package com.pixeltoonz.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val signInButton = findViewById<Button>(R.id.signInButton)
        val togglePassword = findViewById<ImageButton>(R.id.togglePassword)
        val forgotPassword = findViewById<TextView>(R.id.forgotPassword)
        val createAccount = findViewById<TextView>(R.id.createAccount)

        var passwordVisible = false
        togglePassword.setOnClickListener {
            passwordVisible = !passwordVisible
            passwordInput.transformationMethod = if (passwordVisible) {
                android.text.method.HideReturnsTransformationMethod.getInstance()
            } else {
                android.text.method.PasswordTransformationMethod.getInstance()
            }
            passwordInput.setSelection(passwordInput.text.length)
            togglePassword.setImageResource(if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility)
        }

        signInButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            when {
                email.isEmpty() -> emailInput.error = "Enter your email"
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> emailInput.error = "Enter a valid email"
                password.isEmpty() -> passwordInput.error = "Enter your password"
                else -> Toast.makeText(this, "Login UI ready. Connect your authentication service to sign in.", Toast.LENGTH_LONG).show()
            }
        }

        forgotPassword.setOnClickListener {
            Toast.makeText(this, "Connect a password recovery flow here.", Toast.LENGTH_SHORT).show()
        }
        createAccount.setOnClickListener {
            Toast.makeText(this, "Connect your registration screen here.", Toast.LENGTH_SHORT).show()
        }
    }
}
