package com.anniversary.app.ui.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import com.anniversary.app.R
import com.anniversary.app.databinding.ActivityLoginBinding
import com.anniversary.app.ui.main.MainActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // If already logged in, go straight to main
        if (AuthManager.isLoggedIn(this)) {
            navigateToMain()
            return
        }

        setupViews()
    }

    private fun setupViews() {
        binding.btnLogin.setOnClickListener {
            attemptLogin()
        }

        // Allow pressing "Done" on keyboard to login
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else {
                false
            }
        }
    }

    private fun attemptLogin() {
        val phone = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        // Clear previous error
        binding.tvError.visibility = View.GONE

        if (phone.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.login_empty_fields))
            return
        }

        if (AuthManager.authenticate(phone, password)) {
            AuthManager.setLoggedIn(this, true, phone)
            navigateToMain()
        } else {
            showError(getString(R.string.login_failed))
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}

/**
 * Manages authentication state.
 * Currently uses hardcoded credentials (pseudo-login).
 */
object AuthManager {

    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_LOGGED_IN = "is_logged_in"
    private const val KEY_PHONE = "logged_in_phone"

    // Hardcoded credentials for pseudo-login
    private const val VALID_PHONE = "15286814818"
    private const val VALID_PASSWORD = "012229"

    fun authenticate(phone: String, password: String): Boolean {
        return phone == VALID_PHONE && password == VALID_PASSWORD
    }

    fun isLoggedIn(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOGGED_IN, false)
    }

    fun setLoggedIn(context: Context, loggedIn: Boolean, phone: String = "") {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOGGED_IN, loggedIn)
            .putString(KEY_PHONE, phone)
            .apply()
    }

    fun getLoggedInPhone(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PHONE, VALID_PHONE) ?: VALID_PHONE
    }

    fun logout(context: Context) {
        setLoggedIn(context, false)
    }
}
