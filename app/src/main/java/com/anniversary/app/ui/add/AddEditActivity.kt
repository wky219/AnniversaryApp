package com.anniversary.app.ui.add

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.anniversary.app.AnniversaryApplication
import com.anniversary.app.R
import com.anniversary.app.data.entity.Anniversary
import com.anniversary.app.data.entity.AnniversaryType
import com.anniversary.app.databinding.ActivityAddEditBinding
import com.anniversary.app.notification.ReminderScheduler
import com.anniversary.app.ui.login.AuthManager
import com.anniversary.app.ui.widget.AnniversaryWidgetProvider
import com.anniversary.app.util.DateUtils
import com.anniversary.app.util.LunarCalendar
import java.util.*

class AddEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditBinding
    private lateinit var viewModel: AddEditViewModel

    private var selectedDate: Long = 0L
    private var editingAnniversary: Anniversary? = null
    private var isLunarMode = false
    private var lunarMonth = 0
    private var lunarDay = 0
    private var lunarIsLeapMonth = false

    companion object {
        const val EXTRA_ANNIVERSARY = "extra_anniversary"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as AnniversaryApplication
        val username = AuthManager.getLoggedInPhone(this)
        viewModel = ViewModelProvider(
            this, AddEditViewModelFactory(app.repository, username)
        )[AddEditViewModel::class.java]

        setupToolbar()
        setupDatePicker()
        setupLunarSwitch()
        setupTypeChips()
        setupReminderChips()
        setupSaveButton()
        observeData()

        // Check if editing
        @Suppress("DEPRECATION")
        editingAnniversary = intent.getSerializableExtra(EXTRA_ANNIVERSARY) as? Anniversary
        editingAnniversary?.let { loadAnniversary(it) }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = if (editingAnniversary != null) {
                getString(R.string.edit_anniversary)
            } else {
                getString(R.string.add_anniversary)
            }
        }
    }

    private fun setupDatePicker() {
        binding.etDate.setOnClickListener {
            if (isLunarMode) {
                showLunarDatePicker()
            } else {
                showSolarDatePicker()
            }
        }
    }

    private fun setupLunarSwitch() {
        binding.switchLunar.setOnCheckedChangeListener { _, isChecked ->
            isLunarMode = isChecked
            binding.tvLunarDate.visibility = if (isChecked) View.VISIBLE else View.GONE

            if (isChecked && selectedDate > 0) {
                // 切换到农历模式，将当前阳历日期转为农历显示
                val lunar = LunarCalendar.solarToLunar(selectedDate)
                lunarMonth = lunar.lunarMonth
                lunarDay = lunar.lunarDay
                lunarIsLeapMonth = lunar.isLeapMonth
                updateLunarDateDisplay()
            } else if (!isChecked && lunarMonth > 0) {
                // 切换到阳历模式，将当前农历日期转为阳历
                val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
                val lunarYear = LunarCalendar.solarToLunar(selectedDate).lunarYear
                val solarTimestamp = LunarCalendar.lunarToSolarTimestamp(
                    lunarYear, lunarMonth, lunarDay, lunarIsLeapMonth
                )
                selectedDate = solarTimestamp
                binding.etDate.setText(DateUtils.formatDate(selectedDate))
            }
        }
    }

    private fun showSolarDatePicker() {
        val calendar = Calendar.getInstance()
        if (selectedDate > 0) {
            calendar.timeInMillis = selectedDate
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth, 0, 0, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                selectedDate = calendar.timeInMillis
                binding.etDate.setText(DateUtils.formatDate(selectedDate))

                // 如果农历模式开启，同步更新农历显示
                if (isLunarMode) {
                    val lunar = LunarCalendar.solarToLunar(selectedDate)
                    lunarMonth = lunar.lunarMonth
                    lunarDay = lunar.lunarDay
                    lunarIsLeapMonth = lunar.isLeapMonth
                    updateLunarDateDisplay()
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showLunarDatePicker() {
        // 创建农历日期选择对话框
        val dialogView = layoutInflater.inflate(R.layout.dialog_lunar_date_picker, null)
        val yearPicker = dialogView.findViewById<NumberPicker>(R.id.pickerLunarYear)
        val monthPicker = dialogView.findViewById<NumberPicker>(R.id.pickerLunarMonth)
        val dayPicker = dialogView.findViewById<NumberPicker>(R.id.pickerLunarDay)

        // 初始化农历年份选择器
        val currentLunar = LunarCalendar.solarToLunar(System.currentTimeMillis())
        val selectedLunarYear = if (lunarMonth > 0 && selectedDate > 0) {
            LunarCalendar.solarToLunar(selectedDate).lunarYear
        } else {
            currentLunar.lunarYear
        }

        yearPicker.minValue = 1900
        yearPicker.maxValue = 2100
        yearPicker.value = selectedLunarYear
        yearPicker.wrapSelectorWheel = false

        fun updateDayRange(year: Int, monthPair: Pair<Int, Boolean>) {
            val (month, isLeap) = monthPair
            val dayCount = LunarCalendar.getLunarMonthDayCount(year, month, isLeap)
            dayPicker.minValue = 1
            dayPicker.maxValue = dayCount
            dayPicker.displayedValues = (1..dayCount).map {
                LunarCalendar.formatLunarDate(month, it, isLeap).substringAfter("月")
            }.toTypedArray()

            // 保持当前选中日期
            val targetDay = if (lunarDay > 0) lunarDay else currentLunar.lunarDay
            dayPicker.value = targetDay.coerceIn(1, dayCount)
        }

        // 年份变化时更新月份和日期范围
        fun updateMonthAndDayRange() {
            val year = yearPicker.value
            val months = LunarCalendar.getLunarMonths(year)
            val monthNames = months.map { (m, isLeap) ->
                if (isLeap) "闰${LunarCalendar.formatLunarDate(m, 1, true).substringBefore("月")}月"
                else "${LunarCalendar.formatLunarDate(m, 1, false).substringBefore("月")}月"
            }

            monthPicker.minValue = 0
            monthPicker.maxValue = months.size - 1
            monthPicker.displayedValues = monthNames.toTypedArray()

            // 保持当前选中月份
            val targetMonth = if (lunarMonth > 0) lunarMonth else currentLunar.lunarMonth
            val targetIsLeap = lunarIsLeapMonth
            var selectedIndex = months.indexOfFirst { it.first == targetMonth && it.second == targetIsLeap }
            if (selectedIndex < 0) selectedIndex = 0
            monthPicker.value = selectedIndex

            updateDayRange(year, months[monthPicker.value])
        }

        yearPicker.setOnValueChangedListener { _, _, _ ->
            updateMonthAndDayRange()
        }

        monthPicker.setOnValueChangedListener { _, _, _ ->
            val year = yearPicker.value
            val months = LunarCalendar.getLunarMonths(year)
            updateDayRange(year, months[monthPicker.value])
        }

        // 初始化
        updateMonthAndDayRange()

        AlertDialog.Builder(this)
            .setTitle(R.string.select_lunar_date)
            .setView(dialogView)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val year = yearPicker.value
                val months = LunarCalendar.getLunarMonths(year)
                val (month, isLeap) = months[monthPicker.value]
                val day = dayPicker.value

                lunarMonth = month
                lunarDay = day
                lunarIsLeapMonth = isLeap

                // 转换为阳历时间戳存储
                val solarTimestamp = LunarCalendar.lunarToSolarTimestamp(year, month, day, isLeap)
                selectedDate = solarTimestamp

                binding.etDate.setText(DateUtils.formatDate(selectedDate))
                updateLunarDateDisplay()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateLunarDateDisplay() {
        if (lunarMonth > 0) {
            binding.tvLunarDate.text = LunarCalendar.formatLunarDate(lunarMonth, lunarDay, lunarIsLeapMonth)
        }
    }

    private fun setupTypeChips() {
        binding.chipGroupType.check(R.id.chipCustom)
    }

    private fun setupReminderChips() {
        binding.chipGroupReminder.check(R.id.chipNoReminder)
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            save()
        }
    }

    private fun observeData() {
        viewModel.saveResult.observe(this) { success ->
            if (success) {
                Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
                // Schedule reminder if needed
                scheduleReminder()
                // Notify widget to refresh
                AnniversaryWidgetProvider.notifyDataChanged(this)
                finish()
            } else {
                Toast.makeText(this, R.string.name_required, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadAnniversary(anniversary: Anniversary) {
        binding.etName.setText(anniversary.name)
        selectedDate = anniversary.date
        binding.etDate.setText(DateUtils.formatDate(anniversary.date))
        binding.etNote.setText(anniversary.note)
        binding.switchRepeat.isChecked = anniversary.isRepeatYearly

        // 加载农历设置
        isLunarMode = anniversary.isLunar
        binding.switchLunar.isChecked = anniversary.isLunar
        if (anniversary.isLunar) {
            lunarMonth = anniversary.lunarMonth
            lunarDay = anniversary.lunarDay
            lunarIsLeapMonth = anniversary.lunarIsLeapMonth
            binding.tvLunarDate.visibility = View.VISIBLE
            updateLunarDateDisplay()
        }

        // Set type chip
        val typeChipId = when (anniversary.type) {
            AnniversaryType.BIRTHDAY -> R.id.chipBirthday
            AnniversaryType.ANNIVERSARY -> R.id.chipAnniversary
            AnniversaryType.FESTIVAL -> R.id.chipFestival
            AnniversaryType.CUSTOM -> R.id.chipCustom
        }
        binding.chipGroupType.check(typeChipId)

        // Set reminder chip
        val reminderChipId = when (anniversary.reminderDays) {
            1 -> R.id.chipReminder1
            3 -> R.id.chipReminder3
            7 -> R.id.chipReminder7
            15 -> R.id.chipReminder15
            30 -> R.id.chipReminder30
            else -> R.id.chipNoReminder
        }
        binding.chipGroupReminder.check(reminderChipId)

        viewModel.loadAnniversary(anniversary.id)
    }

    private fun save() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            binding.etName.error = getString(R.string.name_required)
            return
        }
        if (selectedDate == 0L) {
            binding.etDate.error = getString(R.string.date_required)
            return
        }

        val type = when (binding.chipGroupType.checkedChipId) {
            R.id.chipBirthday -> AnniversaryType.BIRTHDAY
            R.id.chipAnniversary -> AnniversaryType.ANNIVERSARY
            R.id.chipFestival -> AnniversaryType.FESTIVAL
            else -> AnniversaryType.CUSTOM
        }

        val reminderDays = when (binding.chipGroupReminder.checkedChipId) {
            R.id.chipReminder1 -> 1
            R.id.chipReminder3 -> 3
            R.id.chipReminder7 -> 7
            R.id.chipReminder15 -> 15
            R.id.chipReminder30 -> 30
            else -> -1
        }

        val note = binding.etNote.text.toString().trim()
        val isRepeatYearly = binding.switchRepeat.isChecked

        viewModel.save(
            name, selectedDate, type, note, isRepeatYearly, reminderDays,
            isLunarMode, lunarMonth, lunarDay, lunarIsLeapMonth
        )
    }

    private fun scheduleReminder() {
        val reminderDays = when (binding.chipGroupReminder.checkedChipId) {
            R.id.chipReminder1 -> 1
            R.id.chipReminder3 -> 3
            R.id.chipReminder7 -> 7
            R.id.chipReminder15 -> 15
            R.id.chipReminder30 -> 30
            else -> -1
        }

        if (reminderDays > 0 && selectedDate > 0) {
            // 对于农历重复事件，使用下次出现的阳历日期来设置提醒
            val reminderDate = if (isLunarMode && binding.switchRepeat.isChecked) {
                DateUtils.getNextLunarOccurrence(lunarMonth, lunarDay, lunarIsLeapMonth)
            } else if (binding.switchRepeat.isChecked) {
                DateUtils.getNextOccurrence(selectedDate)
            } else {
                selectedDate
            }

            ReminderScheduler.scheduleReminder(
                this,
                binding.etName.text.toString().trim(),
                reminderDate,
                reminderDays
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
