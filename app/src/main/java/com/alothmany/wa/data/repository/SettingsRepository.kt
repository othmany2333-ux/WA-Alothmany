package com.alothmany.wa.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.alothmany.wa.core.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "wa_al_othmany_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val language = stringPreferencesKey("language")
        val theme = stringPreferencesKey("theme")
        val performance = stringPreferencesKey("performance")
        val selectedSource = stringPreferencesKey("selected_source")
        val navigationSpeed = floatPreferencesKey("navigation_speed")
        val waitSeconds = floatPreferencesKey("wait_seconds")
        val superTurbo = booleanPreferencesKey("super_turbo")
        val skipNonEssential = booleanPreferencesKey("skip_non_essential")
        val smartLinkRead = booleanPreferencesKey("smart_link_read")
        val autoResume = booleanPreferencesKey("auto_resume")
        val notifications = booleanPreferencesKey("notifications")
        val syncArchived = booleanPreferencesKey("sync_archived")
        val syncCommunities = booleanPreferencesKey("sync_communities")
        val saveProgress = booleanPreferencesKey("save_progress")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { p ->
        AppPreferences(
            language = p[Keys.language].toEnumOr(AppLanguage.SYSTEM),
            theme = p[Keys.theme].toEnumOr(AppTheme.DARK),
            performanceMode = p[Keys.performance].toEnumOr(PerformanceMode.BALANCED),
            selectedSource = p[Keys.selectedSource].toEnumOr(WhatsAppSourceType.MAIN),
            navigationSpeed = p[Keys.navigationSpeed] ?: 0.70f,
            waitSeconds = p[Keys.waitSeconds] ?: 3f,
            superTurbo = p[Keys.superTurbo] ?: true,
            skipNonEssential = p[Keys.skipNonEssential] ?: true,
            smartLinkRead = p[Keys.smartLinkRead] ?: true,
            autoResume = p[Keys.autoResume] ?: true,
            notifications = p[Keys.notifications] ?: true,
            syncArchived = p[Keys.syncArchived] ?: true,
            syncCommunities = p[Keys.syncCommunities] ?: true,
            saveProgress = p[Keys.saveProgress] ?: true,
        )
    }

    suspend fun update(transform: (MutablePreferences) -> Unit) {
        context.dataStore.edit(transform)
    }

    suspend fun setLanguage(value: AppLanguage) = update { it[Keys.language] = value.name }
    suspend fun setTheme(value: AppTheme) = update { it[Keys.theme] = value.name }
    suspend fun setPerformance(value: PerformanceMode) = update { it[Keys.performance] = value.name }
    suspend fun setSource(value: WhatsAppSourceType) = update { it[Keys.selectedSource] = value.name }
    suspend fun setNavigationSpeed(value: Float) = update { it[Keys.navigationSpeed] = value }
    suspend fun setWaitSeconds(value: Float) = update { it[Keys.waitSeconds] = value }
    suspend fun setSuperTurbo(value: Boolean) = update { it[Keys.superTurbo] = value }
    suspend fun setSkipNonEssential(value: Boolean) = update { it[Keys.skipNonEssential] = value }
    suspend fun setSmartLinkRead(value: Boolean) = update { it[Keys.smartLinkRead] = value }
    suspend fun setAutoResume(value: Boolean) = update { it[Keys.autoResume] = value }
    suspend fun setNotifications(value: Boolean) = update { it[Keys.notifications] = value }
    suspend fun setSyncArchived(value: Boolean) = update { it[Keys.syncArchived] = value }
    suspend fun setSyncCommunities(value: Boolean) = update { it[Keys.syncCommunities] = value }
    suspend fun setSaveProgress(value: Boolean) = update { it[Keys.saveProgress] = value }
}

private inline fun <reified T : Enum<T>> String?.toEnumOr(default: T): T =
    runCatching { if (this == null) default else enumValueOf<T>(this) }.getOrDefault(default)
