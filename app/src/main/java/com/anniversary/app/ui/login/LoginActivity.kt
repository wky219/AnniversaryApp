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

        // If already logged in or skipped login, go straight to main
        if (AuthManager.canProceedToMain(this)) {
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

        binding.btnSkipLogin.setOnClickListener {
            showSkipLoginDialog()
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

    private fun showSkipLoginDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.skip_login_title)
            .setMessage(R.string.skip_login_message)
            .setPositiveButton(R.string.confirm) { _, _ ->
                AuthManager.skipLogin(this)
                navigateToMain()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
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
    private const val KEY_SKIP_LOGIN = "skip_login"

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

    fun isSkippedLogin(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SKIP_LOGIN, false)
    }

    /**
     * Check if user has either logged in or skipped login.
     * Used to determine if the app should show the login screen.
     */
    fun canProceedToMain(context: Context): Boolean {
        return isLoggedIn(context) || isSkippedLogin(context)
    }

    fun skipLogin(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SKIP_LOGIN, true)
            .putBoolean(KEY_LOGGED_IN, false)
            .putString(KEY_PHONE, "")
            .apply()
    }

    fun clearSkipLogin(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SKIP_LOGIN, false)
            .apply()
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
        // Also clear skip login state so user sees login screen after logout
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SKIP_LOGIN, false)
            .apply()
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
            .putBoolean(KEY_SKIP_LOGIN, false) // Clear skip login when user actually logs in
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
