package com.alothmany.wa.core.model

enum class AppTheme { SYSTEM, DARK, LIGHT }
enum class AppLanguage { SYSTEM, ARABIC, ENGLISH }
enum class PerformanceMode { TURBO, BALANCED, SAFE }
enum class WhatsAppSourceType { MAIN, BUSINESS, DUAL, WORK, SECURE }
enum class LogLevel { INFO, SUCCESS, WARNING, ERROR, DEBUG }
enum class TaskStatus { WAITING, RUNNING, PAUSED, STOPPED, COMPLETED, FAILED, RECOVERING }
enum class TaskType { SYNC, JOIN, CHECK, EXTRACT, PUBLISH, DELETE }

data class AppPreferences(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val theme: AppTheme = AppTheme.DARK,
    val performanceMode: PerformanceMode = PerformanceMode.BALANCED,
    val selectedSource: WhatsAppSourceType = WhatsAppSourceType.MAIN,
    val navigationSpeed: Float = 0.70f,
    val waitSeconds: Float = 3f,
    val superTurbo: Boolean = true,
    val skipNonEssential: Boolean = true,
    val smartLinkRead: Boolean = true,
    val autoResume: Boolean = true,
    val notifications: Boolean = true,
    val syncArchived: Boolean = true,
    val syncCommunities: Boolean = true,
    val saveProgress: Boolean = true,
)
