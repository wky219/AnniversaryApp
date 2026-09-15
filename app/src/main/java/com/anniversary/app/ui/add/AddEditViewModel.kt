package com.anniversary.app.ui.add

import androidx.lifecycle.*
import com.anniversary.app.data.entity.Anniversary
import com.anniversary.app.data.entity.AnniversaryType
import com.anniversary.app.data.repository.AnniversaryRepository
import kotlinx.coroutines.launch

class AddEditViewModel(private val repository: AnniversaryRepository) : ViewModel() {

    private val _anniversary = MutableLiveData<Anniversary?>()
    val anniversary: LiveData<Anniversary?> = _anniversary

    private val _saveResult = MutableLiveData<Boolean>()
    val saveResult: LiveData<Boolean> = _saveResult

    fun loadAnniversary(id: Long) {
        viewModelScope.launch {
            _anniversary.value = repository.getAnniversaryById(id)
        }
    }

    fun save(
        name: String,
        date: Long,
        type: AnniversaryType,
        note: String,
        isRepeatYearly: Boolean,
        reminderDays: Int,
        isLunar: Boolean = false,
        lunarMonth: Int = 0,
        lunarDay: Int = 0,
        lunarIsLeapMonth: Boolean = false
    ) {
        if (name.isBlank()) {
            _saveResult.value = false
            return
        }

        viewModelScope.launch {
            val existing = _anniversary.value
            if (existing != null) {
                val updated = existing.copy(
                    name = name,
                    date = date,
                    type = type,
                    note = note,
                    isRepeatYearly = isRepeatYearly,
                    reminderDays = reminderDays,
                    isLunar = isLunar,
                    lunarMonth = lunarMonth,
                    lunarDay = lunarDay,
                    lunarIsLeapMonth = lunarIsLeapMonth,
                    updatedAt = System.currentTimeMillis()
                )
                repository.update(updated)
            } else {
                val newAnniversary = Anniversary(
                    name = name,
                    date = date,
                    type = type,
                    note = note,
                    isRepeatYearly = isRepeatYearly,
                    reminderDays = reminderDays,
                    isLunar = isLunar,
                    lunarMonth = lunarMonth,
                    lunarDay = lunarDay,
                    lunarIsLeapMonth = lunarIsLeapMonth
                )
                repository.insert(newAnniversary)
            }
            _saveResult.value = true
        }
    }
}

class AddEditViewModelFactory(private val repository: AnniversaryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddEditViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddEditViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
