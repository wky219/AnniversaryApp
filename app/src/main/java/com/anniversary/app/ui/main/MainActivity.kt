package com.anniversary.app.ui.main

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.anniversary.app.AnniversaryApplication
import com.anniversary.app.R
import com.anniversary.app.data.entity.Anniversary
import com.anniversary.app.data.entity.AnniversaryType
import com.anniversary.app.databinding.ActivityMainBinding
import com.anniversary.app.notification.ReminderScheduler
import com.anniversary.app.notification.ReminderSettings
import com.anniversary.app.ui.adapter.AnniversaryAdapter
import com.anniversary.app.ui.add.AddEditActivity
import com.anniversary.app.ui.detail.DetailActivity
import com.anniversary.app.ui.widget.AnniversaryWidgetProvider
import com.anniversary.app.util.DataBackupUtils
import com.anniversary.app.util.DateUtils
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: AnniversaryAdapter

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 1001
        private const val REQUEST_EXPORT = 2001
        private const val REQUEST_IMPORT = 2002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val app = application as AnniversaryApplication
        viewModel = ViewModelProvider(
            this, MainViewModelFactory(app.repository)
        )[MainViewModel::class.java]

        setupRecyclerView()
        setupTabs()
        setupFab()
        setupSwipeRefresh()
        observeData()
        requestNotificationPermission()
    }

    private fun setupRecyclerView() {
        adapter = AnniversaryAdapter(
            onItemClick = { anniversary -> openDetail(anniversary) },
            onItemLongClick = { _ -> viewModel.toggleSelectionMode() },
            onSelectionChanged = { id, _ -> viewModel.toggleSelection(id) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.all_types))
        AnniversaryType.entries.forEach { type ->
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(type.displayName))
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val position = tab?.position ?: 0
                if (position == 0) {
                    viewModel.setFilterType(null)
                } else {
                    viewModel.setFilterType(AnniversaryType.entries[position - 1])
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            val intent = Intent(this, AddEditActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun observeData() {
        viewModel.anniversaries.observe(this) { list ->
            adapter.submitList(list)
            binding.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.isSelectionMode.observe(this) { isSelection ->
            adapter.isSelectionMode = isSelection
            if (isSelection) {
                binding.toolbar.setTitle(R.string.select_all)
                binding.toolbar.setNavigationIcon(R.drawable.ic_close)
                binding.toolbar.setNavigationOnClickListener { viewModel.exitSelectionMode() }
                binding.fabAdd.hide()
            } else {
                binding.toolbar.setTitle(R.string.app_name)
                binding.toolbar.navigationIcon = null
                binding.toolbar.setNavigationOnClickListener(null)
                binding.fabAdd.show()
            }
            invalidateOptionsMenu()
        }

        viewModel.selectedIds.observe(this) { ids ->
            adapter.selectedIds = ids
            if (viewModel.isSelectionMode.value == true) {
                binding.toolbar.title = "已选择 ${ids.size} 项"
            }
        }
    }

    private fun openDetail(anniversary: Anniversary) {
        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_ANNIVERSARY, anniversary)
        }
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText ?: "")
                return true
            }
        })

        // Show/hide items based on selection mode
        val isSelection = viewModel.isSelectionMode.value ?: false
        menu.findItem(R.id.action_search).isVisible = !isSelection
        menu.findItem(R.id.action_select).isVisible = isSelection

        // Update dark/light mode label based on current mode
        val isDarkMode = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        menu.findItem(R.id.action_dark_mode).title =
            if (isDarkMode) getString(R.string.light_mode) else getString(R.string.dark_mode)

        if (isSelection) {
            menu.findItem(R.id.action_select).title = "删除选中"
            menu.findItem(R.id.action_select).setOnMenuItemClickListener {
                showDeleteConfirmDialog()
                true
            }
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_dark_mode -> {
                toggleDarkMode()
                true
            }
            R.id.action_reminder_time -> {
                showReminderTimePicker()
                true
            }
            R.id.action_export -> {
                exportData()
                true
            }
            R.id.action_import -> {
                confirmAndImport()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_POST_NOTIFICATIONS
                )
            }
        }
    }

    private fun toggleDarkMode() {
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        val newMode = if (currentMode == AppCompatDelegate.MODE_NIGHT_YES) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }
        // Persist the mode preference
        getSharedPreferences("app_settings", MODE_PRIVATE).edit()
            .putInt("night_mode", newMode)
            .apply()
        AppCompatDelegate.setDefaultNightMode(newMode)
    }

    private fun showReminderTimePicker() {
        val currentHour = ReminderSettings.getReminderHour(this)
        val currentMinute = ReminderSettings.getReminderMinute(this)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.reminder_time)
            .setMessage(getString(R.string.reminder_time_summary) + ReminderSettings.getReminderTimeDisplay(this))
            .setPositiveButton(R.string.confirm, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        val timePicker = android.widget.TimePicker(this).apply {
            setIs24HourView(true)
            hour = currentHour
            minute = currentMinute
        }

        dialog.setView(timePicker)
        dialog.show()

        // Override the positive button to get the selected time
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val hour = timePicker.hour
            val minute = timePicker.minute
            ReminderSettings.setReminderTime(this, hour, minute)
            rescheduleAllReminders()
            Toast.makeText(
                this,
                getString(R.string.reminder_time_summary) + ReminderSettings.getReminderTimeDisplay(this),
                Toast.LENGTH_SHORT
            ).show()
            dialog.dismiss()
        }
    }

    private fun rescheduleAllReminders() {
        val app = application as AnniversaryApplication
        lifecycleScope.launch(Dispatchers.IO) {
            val anniversaries = app.database.anniversaryDao().getAnniversariesWithReminder()
            for (anniversary in anniversaries) {
                if (anniversary.reminderDays > 0) {
                    // Cancel old reminder and reschedule with new time
                    ReminderScheduler.cancelReminder(
                        this@MainActivity,
                        anniversary.name,
                        anniversary.date
                    )
                    // Use next occurrence for yearly repeating events
                    val reminderDate = if (anniversary.isRepeatYearly) {
                        if (anniversary.isLunar) {
                            com.anniversary.app.util.DateUtils.getNextLunarOccurrence(
                                anniversary.lunarMonth,
                                anniversary.lunarDay,
                                anniversary.lunarIsLeapMonth
                            )
                        } else {
                            com.anniversary.app.util.DateUtils.getNextOccurrence(anniversary.date)
                        }
                    } else {
                        anniversary.date
                    }
                    ReminderScheduler.scheduleReminder(
                        this@MainActivity,
                        anniversary.name,
                        reminderDate,
                        anniversary.reminderDays
                    )
                }
            }
        }
    }

    // ==================== Data Export / Import ====================

    private fun exportData() {
        val app = application as AnniversaryApplication
        lifecycleScope.launch {
            val anniversaries = withContext(Dispatchers.IO) {
                app.database.anniversaryDao().getAllAnniversariesStatic()
            }
            if (anniversaries.isEmpty()) {
                Toast.makeText(this@MainActivity, R.string.export_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            // Use SAF to let user pick save location
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "anniversary_backup_${dateFormat.format(Date())}.json"
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "application/json"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_TITLE, fileName)
            }
            startActivityForResult(intent, REQUEST_EXPORT)
        }
    }

    private fun confirmAndImport() {
        AlertDialog.Builder(this)
            .setTitle(R.string.import_data)
            .setMessage(R.string.import_confirm_message)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "application/json"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(intent, REQUEST_IMPORT)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    @Deprecated("Use Activity Result API in future")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return

        when (requestCode) {
            REQUEST_EXPORT -> {
                data.data?.let { uri ->
                    performExport(uri)
                }
            }
            REQUEST_IMPORT -> {
                data.data?.let { uri ->
                    performImport(uri)
                }
            }
        }
    }

    private fun performExport(uri: Uri) {
        val app = application as AnniversaryApplication
        lifecycleScope.launch {
            try {
                val anniversaries = withContext(Dispatchers.IO) {
                    app.database.anniversaryDao().getAllAnniversariesStatic()
                }
                val json = DataBackupUtils.toJson(anniversaries)

                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                    }
                }

                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.export_success, anniversaries.size),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performImport(uri: Uri) {
        val app = application as AnniversaryApplication
        lifecycleScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: throw IllegalArgumentException("Cannot read file")

                val importedList = DataBackupUtils.fromJson(json)
                    ?: throw IllegalArgumentException("Invalid format")

                // Insert all (id=0 so Room auto-generates new IDs to avoid conflicts)
                withContext(Dispatchers.IO) {
                    for (ann in importedList) {
                        val newAnn = ann.copy(id = 0) // Let Room auto-generate ID
                        val insertedId = app.database.anniversaryDao().insert(newAnn)
                        // Schedule reminder if needed
                        if (newAnn.reminderDays > 0) {
                            val reminderDate = if (newAnn.isRepeatYearly) {
                                if (newAnn.isLunar) {
                                    DateUtils.getNextLunarOccurrence(
                                        newAnn.lunarMonth, newAnn.lunarDay, newAnn.lunarIsLeapMonth
                                    )
                                } else {
                                    DateUtils.getNextOccurrence(newAnn.date)
                                }
                            } else {
                                newAnn.date
                            }
                            ReminderScheduler.scheduleReminder(
                                this@MainActivity,
                                newAnn.name,
                                reminderDate,
                                newAnn.reminderDays
                            )
                        }
                    }
                }

                // Refresh widget
                AnniversaryWidgetProvider.notifyDataChanged(this@MainActivity)

                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.import_success, importedList.size),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, R.string.import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== End Data Export / Import ====================

    private fun showDeleteConfirmDialog() {
        val count = viewModel.selectedIds.value?.size ?: 0
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(getString(R.string.delete_batch_confirm_message, count))
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.deleteSelected()
                AnniversaryWidgetProvider.notifyDataChanged(this)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
