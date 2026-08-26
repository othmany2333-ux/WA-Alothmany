package com.alothmany.wa.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alothmany.wa.core.model.AppPreferences
import com.alothmany.wa.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppRootViewModel @Inject constructor(
    settings: SettingsRepository,
) : ViewModel() {
    val preferences = settings.preferences.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppPreferences(),
    )
}
