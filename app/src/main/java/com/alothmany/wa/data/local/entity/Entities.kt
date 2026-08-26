package com.alothmany.wa.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "logs", indices = [Index("timestamp")])
data class LogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: String,
    val module: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
)

@Entity(tableName = "tasks", indices = [Index("status"), Index("updatedAt")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val type: String,
    val title: String,
    val status: String,
    val progress: Float = 0f,
    val processed: Long = 0,
    val total: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "whatsapp_sources")
data class WhatsAppSourceEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val userId: Int,
    val profileType: String,
    val displayName: String,
    val status: String = "NOT_CONFIGURED",
    val lastCheckedAt: Long = 0,
)

@Entity(
    tableName = "groups",
    indices = [Index("sourceId"), Index("archived"), Index("isCommunity")]
)
data class GroupEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val displayName: String,
    val archived: Boolean = false,
    val isCommunity: Boolean = false,
    val memberCount: Int? = null,
    val status: String = "UNKNOWN",
    val fingerprint: String = "",
    val lastSyncedAt: Long = 0,
)

@Entity(
    tableName = "links",
    indices = [Index("groupId"), Index(value = ["normalizedUrl"], unique = false)]
)
data class LinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: String,
    val sourceId: String,
    val url: String,
    val normalizedUrl: String,
    val category: String,
    val occurrenceCount: Int = 1,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),
)
