package com.anniversary.app.ui.profile

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.anniversary.app.R
import com.anniversary.app.databinding.ActivityProfileBinding
import com.anniversary.app.notification.ReminderSettings
import com.anniversary.app.ui.login.AuthManager
import com.anniversary.app.ui.login.LoginActivity
import com.anniversary.app.ui.main.MainActivity
import com.anniversary.app.util.DataBackupUtils
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.anniversary.app.data.database.AnniversaryDatabase
import com.anniversary.app.data.repository.AnniversaryRepository
import com.anniversary.app.notification.ReminderScheduler
import com.anniversary.app.ui.widget.AnniversaryWidgetProvider
import android.net.Uri
import android.widget.Toast

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    companion object {
        private const val REQUEST_EXPORT = 3001
        private const val REQUEST_IMPORT = 3002
    }

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
        val phone = AuthManager.getLoggedInPhone(this)
        binding.tvPhone.text = phone
        // Show first character of phone as avatar text
        binding.tvAvatar.text = phone.take(1)
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
        lifecycleScope.launch(Dispatchers.IO) {
            val anniversaries = repository.getAnniversariesWithReminder()
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
        binding.cardExport.setOnClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "anniversary_backup_${System.currentTimeMillis()}.json")
            }
            startActivityForResult(intent, REQUEST_EXPORT)
        }

        binding.cardImport.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.confirm)
                .setMessage(R.string.import_confirm_message)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                    }
                    startActivityForResult(intent, REQUEST_IMPORT)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    @Deprecated("Use Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        when (requestCode) {
            REQUEST_EXPORT -> performExport(data.data!!)
            REQUEST_IMPORT -> performImport(data.data!!)
        }
    }

    private fun performExport(uri: Uri) {
        val database = AnniversaryDatabase.getDatabase(this)
        lifecycleScope.launch(Dispatchers.IO) {
            val anniversaries = database.anniversaryDao().getAllAnniversariesStatic()
            if (anniversaries.isEmpty()) {
                runOnUiThread {
                    Toast.makeText(
                        this@ProfileActivity,
                        R.string.export_empty,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }
            val json = DataBackupUtils.toJson(anniversaries)
            try {
                contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(json.toByteArray(Charsets.UTF_8))
                }
                runOnUiThread {
                    Toast.makeText(
                        this@ProfileActivity,
                        getString(R.string.export_success, anniversaries.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@ProfileActivity,
                        R.string.export_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun performImport(uri: Uri) {
        val database = AnniversaryDatabase.getDatabase(this)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = contentResolver.openInputStream(uri)?.use { `is` ->
                    `is`.bufferedReader().readText()
                } ?: throw IllegalArgumentException("Cannot read file")

                val anniversaries = DataBackupUtils.fromJson(json) ?: throw IllegalArgumentException("Invalid format")
                var importedCount = 0
                anniversaries.forEach { anniversary ->
                    val id = database.anniversaryDao().insert(anniversary.copy(id = 0))
                    if (id > 0) {
                        importedCount++
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
                AnniversaryWidgetProvider.notifyDataChanged(this@ProfileActivity)
                runOnUiThread {
                    Toast.makeText(
                        this@ProfileActivity,
                        getString(R.string.import_success, importedCount),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@ProfileActivity,
                        R.string.import_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun setupLogout() {
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
    }
}
