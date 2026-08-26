package com.alothmany.wa.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alothmany.wa.data.local.dao.GroupDao
import com.alothmany.wa.data.local.entity.GroupEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class GroupsViewModel @Inject constructor(dao: GroupDao) : ViewModel() {
    val groups = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected = _selected.asStateFlow()
    fun toggle(id: String) { _selected.update { if (id in it) it - id else it + id } }
    fun selectAll(items: List<GroupEntity>) { _selected.value = items.map { it.id }.toSet() }
    fun clear() { _selected.value = emptySet() }
    fun selectArchived(items: List<GroupEntity>) { _selected.value = items.filter { it.archived }.map { it.id }.toSet() }
    fun selectCommunities(items: List<GroupEntity>) { _selected.value = items.filter { it.isCommunity }.map { it.id }.toSet() }
}
