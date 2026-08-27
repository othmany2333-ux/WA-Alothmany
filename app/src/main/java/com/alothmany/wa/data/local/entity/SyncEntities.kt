package com.alothmany.wa.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "group_sync_meta",
    indices = [
        Index("sourceId"),
        Index("isUnread"),
        Index("isActive"),
        Index("isLocked"),
        Index("isDeleted"),
        Index("isCommunity"),
        Index("lastSeenRunId"),
    ],
)
data class GroupSyncMetaEntity(
    @PrimaryKey val groupId: String,
    val sourceId: String,
    val isUnread: Boolean = false,
    val isActive: Boolean = true,
    val isLocked: Boolean = false,
    val isDeleted: Boolean = false,
    val isCommunity: Boolean = false,
    val confidence: String = "MEDIUM",
    val lastSeenRunId: String? = null,
    val missingStreak: Int = 0,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "sync_runs",
    indices = [Index("sourceId"), Index("status"), Index("updatedAt")],
)
data class SyncRunEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val status: String,
    val stage: String,
    val discoveredCount: Int = 0,
    val newCount: Int = 0,
    val processedScreens: Int = 0,
    val currentGroupName: String? = null,
    val errorMessage: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)

@Entity(tableName = "sync_checkpoints")
data class SyncCheckpointEntity(
    @PrimaryKey val runId: String,
    val sourceId: String,
    val stage: String,
    val lastAnchor: String? = null,
    val lastScreenFingerprint: String? = null,
    val consecutiveEndPasses: Int = 0,
    val processedScreens: Int = 0,
    val discoveredCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)
