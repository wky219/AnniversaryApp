package com.anniversary.app.ui.profile

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.anniversary.app.R
import androidx.room.withTransaction
import com.anniversary.app.data.database.AnniversaryDatabase
import com.anniversary.app.data.entity.Anniversary
import com.anniversary.app.data.repository.AnniversaryRepository
import com.anniversary.app.databinding.ActivityProfileBinding
import com.anniversary.app.notification.ReminderScheduler
import com.anniversary.app.notification.ReminderSettings
import com.anniversary.app.ui.widget.AnniversaryWidgetProvider
import com.anniversary.app.util.DataBackupUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private var isExportRunning = false
    private var isImportRunning = false
    private val isTransferRunning: Boolean
        get() = isExportRunning || isImportRunning

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) performExport(uri)
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) performImport(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDarkMode()
        setupReminderTime()
        setupDataBackup()
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
            if (!isTransferRunning) requestExport()
        }
        binding.cardImport.setOnClickListener {
            if (isTransferRunning) return@setOnClickListener
            AlertDialog.Builder(this)
                .setTitle(R.string.import_data)
                .setMessage(R.string.import_confirm_message)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    try {
                        importLauncher.launch(arrayOf("*/*"))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(this, R.string.file_picker_unavailable, Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun updateTransferState() {
        binding.cardExport.isEnabled = !isTransferRunning
        binding.cardImport.isEnabled = !isTransferRunning
        binding.cardDarkMode.isEnabled = !isTransferRunning
        binding.progressExport.visibility = if (isExportRunning) View.VISIBLE else View.GONE
        binding.progressImport.visibility = if (isImportRunning) View.VISIBLE else View.GONE
    }

    private fun requestExport() {
        isExportRunning = true
        updateTransferState()
        lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    AnniversaryDatabase.getDatabase(this@ProfileActivity)
                        .anniversaryDao().getCount()
                }
                if (count == 0) {
                    Toast.makeText(this@ProfileActivity, R.string.export_empty, Toast.LENGTH_SHORT).show()
                } else {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date())
                    exportLauncher.launch("anniversary_$timestamp.json")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ProfileActivity", "无法开始导出", e)
                Toast.makeText(this@ProfileActivity, R.string.export_failed, Toast.LENGTH_SHORT).show()
            } finally {
                isExportRunning = false
                updateTransferState()
            }
        }
    }

    private fun performExport(uri: Uri) {
        if (isTransferRunning) return
        isExportRunning = true
        updateTransferState()
        lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val anniversaries = AnniversaryDatabase.getDatabase(this@ProfileActivity)
                        .anniversaryDao().getAllAnniversariesStatic()
                    val json = DataBackupUtils.toJson(anniversaries)
                    val stream = contentResolver.openOutputStream(uri, "wt")
                        ?: throw IOException("无法打开导出文件")
                    stream.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
                    anniversaries.size
                }
                Toast.makeText(
                    this@ProfileActivity, getString(R.string.export_success, count), Toast.LENGTH_SHORT
                ).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ProfileActivity", "导出文件失败", e)
                Toast.makeText(this@ProfileActivity, R.string.export_failed, Toast.LENGTH_SHORT).show()
            } finally {
                isExportRunning = false
                updateTransferState()
            }
        }
    }

    private fun performImport(uri: Uri) {
        if (isTransferRunning) return
        isImportRunning = true
        updateTransferState()
        lifecycleScope.launch {
            try {
                val (count, refreshSucceeded) = withContext(Dispatchers.IO) {
                    val stream = contentResolver.openInputStream(uri)
                        ?: throw IOException("无法打开导入文件")
                    val json = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val anniversaries = DataBackupUtils.fromJson(json)
                        ?: throw IllegalArgumentException("备份文件格式无效")
                    val database = AnniversaryDatabase.getDatabase(this@ProfileActivity)
                    // 整个文件校验通过后再追加，失败时回滚，不覆盖现有记录。
                    database.withTransaction {
                        anniversaries.forEach { anniversary ->
                            database.anniversaryDao().insert(anniversary.copy(id = 0, username = ""))
                        }
                    }
                    anniversaries.size to refreshImportedData(anniversaries)
                }
                val message = when {
                    count == 0 -> getString(R.string.import_empty)
                    !refreshSucceeded -> getString(R.string.import_refresh_failed, count)
                    else -> getString(R.string.import_success, count)
                }
                Toast.makeText(this@ProfileActivity, message, Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                Toast.makeText(this@ProfileActivity, R.string.import_failed, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.w("ProfileActivity", "导入文件失败", e)
                Toast.makeText(this@ProfileActivity, R.string.import_io_failed, Toast.LENGTH_SHORT).show()
            } finally {
                isImportRunning = false
                updateTransferState()
            }
        }
    }

    private fun refreshImportedData(anniversaries: List<Anniversary>): Boolean {
        var succeeded = true
        // 数据已提交，刷新失败需单独提示，避免用户误以为导入失败而重复追加。
        anniversaries.filter { it.reminderDays >= 0 }.forEach { anniversary ->
            try {
                ReminderScheduler.scheduleReminder(
                    applicationContext, anniversary.name, anniversary.date, anniversary.reminderDays
                )
            } catch (e: Exception) {
                succeeded = false
                Log.w("ProfileActivity", "导入后调度提醒失败", e)
            }
        }
        try {
            AnniversaryWidgetProvider.notifyDataChanged(applicationContext)
        } catch (e: Exception) {
            succeeded = false
            Log.w("ProfileActivity", "导入后刷新小部件失败", e)
        }
        return succeeded
    }
}
