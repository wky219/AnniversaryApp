package com.anniversary.app.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.anniversary.app.R
import com.anniversary.app.data.cloud.CloudBaseBackupRepository
import com.anniversary.app.data.database.AnniversaryDatabase
import com.anniversary.app.data.repository.AnniversaryRepository
import com.anniversary.app.databinding.ActivityProfileBinding
import com.anniversary.app.notification.ReminderScheduler
import com.anniversary.app.notification.ReminderSettings
import com.anniversary.app.ui.login.AuthManager
import com.anniversary.app.ui.login.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private var isBackupRunning = false
    private var isRestoreRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupUserInfo()
        setupDarkMode()
        setupReminderTime()
        setupDataBackup()
        setupLogout()
    }

    override fun onResume() {
        super.onResume()
        // Refresh status when returning from settings changes
        updateDarkModeStatus()
        updateReminderTimeStatus()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupUserInfo() {
        val isLoggedIn = AuthManager.isLoggedIn(this)
        if (isLoggedIn) {
            val phone = AuthManager.getLoggedInPhone(this)
            binding.tvPhone.text = phone
            binding.tvAvatar.text = phone.take(1)
            binding.tvLoginLabel.text = getString(R.string.profile_logged_in)
        } else {
            binding.tvPhone.text = getString(R.string.not_logged_in)
            binding.tvAvatar.text = "?"
            binding.tvLoginLabel.text = getString(R.string.skip_login)
        }
    }

    private fun setupDarkMode() {
        updateDarkModeStatus()
        binding.cardDarkMode.setOnClickListener {
            toggleDarkMode()
        }
    }

    private fun updateDarkModeStatus() {
        val isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        binding.tvDarkModeStatus.text = if (isDark) {
            getString(R.string.light_mode)
        } else {
            getString(R.string.dark_mode)
        }
    }

    private fun toggleDarkMode() {
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        val newMode = if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }
        getSharedPreferences("app_settings", MODE_PRIVATE).edit()
            .putInt("night_mode", newMode)
            .apply()
        AppCompatDelegate.setDefaultNightMode(newMode)
    }

    private fun setupReminderTime() {
        updateReminderTimeStatus()
        binding.cardReminderTime.setOnClickListener {
            showReminderTimePicker()
        }
    }

    private fun updateReminderTimeStatus() {
        binding.tvReminderTime.text = ReminderSettings.getReminderTimeDisplay(this)
    }

    private fun showReminderTimePicker() {
        val currentHour = ReminderSettings.getReminderHour(this)
        val currentMinute = ReminderSettings.getReminderMinute(this)
        val dialog = android.app.TimePickerDialog(
            this,
            { _, hour, minute ->
                ReminderSettings.setReminderTime(this, hour, minute)
                updateReminderTimeStatus()
                // Reschedule all reminders with new time
                rescheduleAllReminders()
            },
            currentHour,
            currentMinute,
            true
        )
        dialog.show()
    }

    private fun rescheduleAllReminders() {
        val database = AnniversaryDatabase.getDatabase(this)
        val repository = AnniversaryRepository(database.anniversaryDao())
        val username = AuthManager.getLoggedInPhone(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val anniversaries = repository.getAnniversariesWithReminder(username)
            anniversaries.forEach { anniversary ->
                if (anniversary.reminderDays > 0) {
                    ReminderScheduler.scheduleReminder(
                        this@ProfileActivity,
                        anniversary.name,
                        anniversary.date,
                        anniversary.reminderDays
                    )
                }
            }
        }
    }

    private fun setupDataBackup() {
        val isLoggedIn = AuthManager.isLoggedIn(this)

        // Disable backup/restore for non-logged-in users
        if (!isLoggedIn) {
            binding.cardBackup.isEnabled = false
            binding.cardBackup.alpha = 0.5f
            binding.cardRestore.isEnabled = false
            binding.cardRestore.alpha = 0.5f
            binding.tvBackupStatus.text = getString(R.string.cloud_feature_requires_login)
            binding.tvBackupStatus.visibility = View.VISIBLE
            binding.tvRestoreStatus.text = getString(R.string.cloud_feature_requires_login)
            binding.tvRestoreStatus.visibility = View.VISIBLE
        }

        binding.cardBackup.setOnClickListener {
            if (isBackupRunning || isRestoreRunning) return@setOnClickListener
            if (!AuthManager.isLoggedIn(this)) {
                Toast.makeText(this, R.string.cloud_not_logged_in, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performBackup()
        }

        binding.cardRestore.setOnClickListener {
            if (isBackupRunning || isRestoreRunning) return@setOnClickListener
            if (!AuthManager.isLoggedIn(this)) {
                Toast.makeText(this, R.string.cloud_not_logged_in, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm)
                .setMessage(R.string.restore_confirm_message)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    performRestore()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun performBackup() {
        isBackupRunning = true
        binding.progressBackup.visibility = View.VISIBLE
        binding.cardBackup.isClickable = false

        lifecycleScope.launch(Dispatchers.IO) {
            // Check if there is data to backup
            val database = AnniversaryDatabase.getDatabase(this@ProfileActivity)
            val username = AuthManager.getLoggedInPhone(this@ProfileActivity)
            val count = database.anniversaryDao().getCount(username)
            if (count == 0) {
                launch(Dispatchers.Main) {
                    isBackupRunning = false
                    binding.progressBackup.visibility = View.GONE
                    binding.cardBackup.isClickable = true
                    Toast.makeText(this@ProfileActivity, R.string.backup_empty, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val result = CloudBaseBackupRepository.backupToCloud(this@ProfileActivity)
            launch(Dispatchers.Main) {
                isBackupRunning = false
                binding.progressBackup.visibility = View.GONE
                binding.cardBackup.isClickable = true
                if (result >= 0) {
                    Toast.makeText(
                        this@ProfileActivity,
                        getString(R.string.backup_success, result),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this@ProfileActivity, R.string.backup_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun performRestore() {
        isRestoreRunning = true
        binding.progressRestore.visibility = View.VISIBLE
        binding.cardRestore.isClickable = false

        lifecycleScope.launch(Dispatchers.IO) {
            val result = CloudBaseBackupRepository.restoreFromCloud(this@ProfileActivity)
            launch(Dispatchers.Main) {
                isRestoreRunning = false
                binding.progressRestore.visibility = View.GONE
                binding.cardRestore.isClickable = true
                if (result >= 0) {
                    Toast.makeText(
                        this@ProfileActivity,
                        getString(R.string.restore_success, result),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this@ProfileActivity, R.string.restore_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupLogout() {
        val isLoggedIn = AuthManager.isLoggedIn(this)
        if (isLoggedIn) {
            binding.btnLogout.text = getString(R.string.logout)
            binding.btnLogout.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(R.string.logout_confirm_title)
                    .setMessage(R.string.logout_confirm_message)
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        AuthManager.logout(this)
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        } else {
            binding.btnLogout.text = getString(R.string.login)
            binding.btnLogout.setOnClickListener {
                // Clear skip login so LoginActivity doesn't auto-redirect
                AuthManager.clearSkipLogin(this)
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }
}
