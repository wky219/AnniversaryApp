package com.anniversary.app.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.anniversary.app.AnniversaryApplication
import com.anniversary.app.R
import com.anniversary.app.data.entity.Anniversary
import com.anniversary.app.data.entity.AnniversaryType
import com.anniversary.app.databinding.ActivityMainBinding
import com.anniversary.app.ui.adapter.AnniversaryAdapter
import com.anniversary.app.ui.add.AddEditActivity
import com.anniversary.app.ui.detail.DetailActivity
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: AnniversaryAdapter

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 1001
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
        AppCompatDelegate.setDefaultNightMode(newMode)
    }

    private fun showDeleteConfirmDialog() {
        val count = viewModel.selectedIds.value?.size ?: 0
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(getString(R.string.delete_batch_confirm_message, count))
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.deleteSelected()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
