package com.anniversary.app.ui.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.anniversary.app.R
import com.anniversary.app.data.cloud.CloudBaseManager
import com.anniversary.app.data.database.AnniversaryDatabase
import com.anniversary.app.databinding.ActivityLoginBinding
import com.anniversary.app.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var isLoggingIn = false

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
        fillLastUsername()
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

    private fun fillLastUsername() {
        val lastUsername = AuthManager.getLastUsername(this)
        if (lastUsername.isNotEmpty()) {
            binding.etPhone.setText(lastUsername)
            // Move focus to password field
            binding.etPassword.requestFocus()
        }
    }

    private fun attemptLogin() {
        if (isLoggingIn) return

        val username = binding.etPhone.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        // Clear previous error
        binding.tvError.visibility = View.GONE

        if (username.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.login_empty_fields))
            return
        }

        isLoggingIn = true
        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = getString(R.string.login_loading)

        lifecycleScope.launch {
            val result = AuthManager.signIn(this@LoginActivity, username, password)
            withContext(Dispatchers.Main) {
                isLoggingIn = false
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = getString(R.string.login)

                if (result != null) {
                    // Migrate old data (username="") to current user
                    lifecycleScope.launch(Dispatchers.IO) {
                        migrateDataToUser(username)
                    }
                    navigateToMain()
                } else {
                    showError(getString(R.string.login_failed))
                }
            }
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

    /**
     * Migrate local data that has no username (from before multi-user support)
     * to the current logged-in user.
     */
    private suspend fun migrateDataToUser(username: String) {
        val database = AnniversaryDatabase.getDatabase(this)
        val dao = database.anniversaryDao()
        // Get all records with empty username (legacy data)
        val legacyData = dao.getAllAnniversariesStatic("")
        for (ann in legacyData) {
            dao.update(ann.copy(username = username))
        }
    }
}

/**
 * Manages authentication via Tencent CloudBase.
 * Uses account/password sign-in through CloudBase Auth API.
 */
object AuthManager {

    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_LOGGED_IN = "is_logged_in"
    private const val KEY_PHONE = "logged_in_phone"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_LAST_USERNAME = "last_username"

    /**
     * Sign in with username and password via CloudBase.
     * Returns the result map on success, null on failure.
     */
    suspend fun signIn(context: Context, username: String, password: String): Map<String, Any>? {
        return withContext(Dispatchers.IO) {
            try {
                val result = CloudBaseManager.post(
                    "/auth/v1/signin",
                    mapOf("username" to username, "password" to password)
                )
                if (result != null) {
                    val accessToken = result["access_token"] as? String
                    val refreshToken = result["refresh_token"] as? String
                    val userId = result["sub"] as? String
                    // Update client token
                    CloudBaseManager.updateAccessToken(accessToken)
                    // Persist login state
                    saveLoginState(context, true, username, accessToken, refreshToken, userId)
                }
                result
            } catch (e: Exception) {
                null
            }
        }
    }

    fun isLoggedIn(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LOGGED_IN, false)
    }

    fun getLoggedInPhone(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PHONE, "") ?: ""
    }

    fun getLastUsername(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_USERNAME, "") ?: ""
    }

    fun getAccessToken(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCESS_TOKEN, null)
    }

    fun logout(context: Context) {
        saveLoginState(context, false, "", null, null, null)
    }

    private fun saveLoginState(
        context: Context,
        loggedIn: Boolean,
        username: String?,
        accessToken: String?,
        refreshToken: String?,
        userId: String?
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LOGGED_IN, loggedIn)
            .putString(KEY_PHONE, username ?: "")
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_ID, userId)
            .apply()

        // Save last username separately (not cleared on logout)
        if (!username.isNullOrEmpty()) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_USERNAME, username)
                .apply()
        }
    }
}
