package com.anniversary.app.ui.detail

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.anniversary.app.AnniversaryApplication
import com.anniversary.app.R
import com.anniversary.app.data.entity.Anniversary
import com.anniversary.app.data.entity.AnniversaryType
import com.anniversary.app.databinding.ActivityDetailBinding
import com.anniversary.app.ui.add.AddEditActivity
import com.anniversary.app.ui.main.MainViewModel
import com.anniversary.app.ui.main.MainViewModelFactory
import com.anniversary.app.util.DateUtils
import com.anniversary.app.util.LunarCalendar
import kotlinx.coroutines.launch

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var viewModel: MainViewModel
    private var anniversary: Anniversary? = null

    companion object {
        const val EXTRA_ANNIVERSARY = "extra_anniversary"
        const val EXTRA_ANNIVERSARY_ID = "extra_anniversary_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as AnniversaryApplication
        viewModel = ViewModelProvider(
            this, MainViewModelFactory(app.repository)
        )[MainViewModel::class.java]

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        @Suppress("DEPRECATION")
        anniversary = intent.getSerializableExtra(EXTRA_ANNIVERSARY) as? Anniversary

        if (anniversary != null) {
            displayAnniversary(anniversary!!)
        } else {
            // Load by ID (e.g., from widget click)
            val id = intent.getLongExtra(EXTRA_ANNIVERSARY_ID, -1L)
            if (id != -1L) {
                loadAnniversaryById(id)
            }
        }

        setupButtons()
    }

    private fun loadAnniversaryById(id: Long) {
        val app = application as AnniversaryApplication
        lifecycleScope.launch {
            try {
                val loaded = app.database.anniversaryDao().getAnniversaryById(id)
                if (loaded != null) {
                    anniversary = loaded
                    displayAnniversary(loaded)
                } else {
                    finish()
                }
            } catch (e: Exception) {
                Log.e("DetailActivity", "Error loading anniversary id=$id", e)
                finish()
            }
        }
    }

    private fun displayAnniversary(anniversary: Anniversary) {
        binding.tvName.text = anniversary.name
        binding.chipType.text = anniversary.type.displayName

        // 显示日期
        if (anniversary.isLunar) {
            // 农历模式：显示原始阳历日期
            binding.tvDate.text = DateUtils.formatDate(anniversary.date)
            // 显示农历日期
            binding.tvLunarDate.visibility = View.VISIBLE
            binding.tvLunarDate.text = LunarCalendar.formatLunarDateFull(
                LunarCalendar.solarToLunar(anniversary.date).lunarYear,
                anniversary.lunarMonth,
                anniversary.lunarDay,
                anniversary.lunarIsLeapMonth
            )
        } else {
            binding.tvDate.text = DateUtils.formatDate(anniversary.date)
            binding.tvLunarDate.visibility = View.GONE
        }

        // Set type chip color
        val chipColor = when (anniversary.type) {
            AnniversaryType.BIRTHDAY -> R.color.type_birthday
            AnniversaryType.ANNIVERSARY -> R.color.type_anniversary
            AnniversaryType.FESTIVAL -> R.color.type_festival
            AnniversaryType.CUSTOM -> R.color.type_custom
        }
        binding.chipType.setChipBackgroundColorResource(chipColor)

        // Countdown
        binding.tvCountdown.text = DateUtils.getCountdownText(anniversary)

        // Years since
        val years = if (anniversary.isLunar) {
            DateUtils.getLunarYearsSince(
                anniversary.date,
                anniversary.lunarMonth,
                anniversary.lunarDay,
                anniversary.lunarIsLeapMonth
            )
        } else {
            DateUtils.getYearsSince(anniversary.date)
        }
        if (years > 0) {
            binding.tvYears.text = "第${years}年"
            binding.tvYears.visibility = View.VISIBLE
        } else {
            binding.tvYears.visibility = View.GONE
        }

        // Total days
        val totalDays = DateUtils.getDaysSince(anniversary.date)
        binding.tvTotalDays.text = if (totalDays >= 0) "${totalDays} 天" else "未到"

        // 农历信息
        if (anniversary.isLunar) {
            binding.layoutLunarInfo.visibility = View.VISIBLE
            binding.tvLunarDateInfo.text = LunarCalendar.formatLunarDate(
                anniversary.lunarMonth,
                anniversary.lunarDay,
                anniversary.lunarIsLeapMonth
            )
            // 显示今年对应的阳历日期
            val thisYearSolar = DateUtils.getNextLunarOccurrence(
                anniversary.lunarMonth,
                anniversary.lunarDay,
                anniversary.lunarIsLeapMonth
            )
            binding.tvThisYearSolarDate.text = DateUtils.formatDate(thisYearSolar)
        } else {
            binding.layoutLunarInfo.visibility = View.GONE
        }

        // Reminder
        binding.tvReminder.text = when (anniversary.reminderDays) {
            1 -> "提前1天"
            3 -> "提前3天"
            7 -> "提前7天"
            15 -> "提前15天"
            30 -> "提前30天"
            else -> "不提醒"
        }

        // Note
        if (anniversary.note.isNotBlank()) {
            binding.tvNote.text = anniversary.note
            binding.layoutNote.visibility = View.VISIBLE
        } else {
            binding.layoutNote.visibility = View.GONE
        }
    }

    private fun setupButtons() {
        binding.btnEdit.setOnClickListener {
            anniversary?.let { ann ->
                val intent = Intent(this, AddEditActivity::class.java).apply {
                    putExtra(AddEditActivity.EXTRA_ANNIVERSARY, ann)
                }
                startActivity(intent)
                finish()
            }
        }

        binding.btnShare.setOnClickListener {
            anniversary?.let { ann ->
                shareAnniversary(ann)
            }
        }

        binding.btnDelete.setOnClickListener {
            showDeleteConfirmDialog()
        }
    }

    private fun shareAnniversary(anniversary: Anniversary) {
        val countdown = DateUtils.getCountdownText(anniversary)
        val dateStr = if (anniversary.isLunar) {
            DateUtils.formatDate(anniversary)
        } else {
            DateUtils.formatDate(anniversary.date)
        }
        val shareText = getString(
            R.string.share_text,
            anniversary.name,
            dateStr,
            countdown
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, "分享纪念日"))
    }

    private fun showDeleteConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_message)
            .setPositiveButton(R.string.confirm) { _, _ ->
                anniversary?.let { viewModel.delete(it) }
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
