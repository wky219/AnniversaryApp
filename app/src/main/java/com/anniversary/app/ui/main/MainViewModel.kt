package com.anniversary.app.ui.main

import androidx.lifecycle.*
import com.anniversary.app.data.entity.Anniversary
import com.anniversary.app.data.entity.AnniversaryType
import com.anniversary.app.data.repository.AnniversaryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(private val repository: AnniversaryRepository, private val username: String) : ViewModel() {

    private val _filterType = MutableLiveData<AnniversaryType?>(null)
    private val _searchQuery = MutableLiveData<String>("")
    private val _isSelectionMode = MutableLiveData(false)
    private val _selectedIds = MutableLiveData<Set<Long>>(emptySet())

    val isSelectionMode: LiveData<Boolean> = _isSelectionMode
    val selectedIds: LiveData<Set<Long>> = _selectedIds

    private var collectJob: Job? = null

    val anniversaries: LiveData<List<Anniversary>> = MediatorLiveData<List<Anniversary>>().apply {
        fun update() {
            val query = _searchQuery.value ?: ""
            val type = _filterType.value

            collectJob?.cancel()
            collectJob = viewModelScope.launch {
                val flow = when {
                    query.isNotBlank() -> repository.searchAnniversaries(username, query)
                    type != null -> repository.getAnniversariesByType(username, type)
                    else -> repository.getAllAnniversaries(username)
                }
                flow.collect { list ->
                    postValue(list)
                }
            }
        }

        addSource(_searchQuery) { update() }
        addSource(_filterType) { update() }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterType(type: AnniversaryType?) {
        _filterType.value = type
    }

    fun toggleSelectionMode() {
        val current = _isSelectionMode.value ?: false
        _isSelectionMode.value = !current
        if (current) {
            _selectedIds.value = emptySet()
        }
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedIds.value = emptySet()
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value ?: emptySet()
        _selectedIds.value = if (current.contains(id)) {
            current - id
        } else {
            current + id
        }
    }

    fun selectAll(ids: List<Long>) {
        _selectedIds.value = ids.toSet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value?.toList() ?: return
        viewModelScope.launch {
            repository.deleteByIds(ids)
            exitSelectionMode()
        }
    }

    fun delete(anniversary: Anniversary) {
        viewModelScope.launch {
            repository.delete(anniversary)
        }
    }
}

class MainViewModelFactory(private val repository: AnniversaryRepository, private val username: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, username) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
