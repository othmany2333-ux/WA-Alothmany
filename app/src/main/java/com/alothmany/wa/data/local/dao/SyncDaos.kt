package com.alothmany.wa.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.alothmany.wa.data.local.entity.GroupSyncMetaEntity
import com.alothmany.wa.data.local.entity.SyncCheckpointEntity
import com.alothmany.wa.data.local.entity.SyncRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupSyncMetaDao {
    @Query("SELECT * FROM group_sync_meta")
    fun observeAll(): Flow<List<GroupSyncMetaEntity>>

    @Query("SELECT * FROM group_sync_meta WHERE sourceId = :sourceId")
    suspend fun getForSource(sourceId: String): List<GroupSyncMetaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<GroupSyncMetaEntity>)

    @Query(
        """
        UPDATE group_sync_meta
        SET isActive = 0,
            missingStreak = missingStreak + 1
        WHERE sourceId = :sourceId
          AND (lastSeenRunId IS NULL OR lastSeenRunId != :runId)
        """
    )
    suspend fun markMissingAfterSuccessfulRun(sourceId: String, runId: String)

    @Query(
        """
        UPDATE group_sync_meta
        SET isDeleted = 1
        WHERE sourceId = :sourceId
          AND missingStreak >= 2
        """
    )
    suspend fun promoteVerifiedMissingToDeleted(sourceId: String)
}

@Dao
interface SyncRunDao {
    @Query("SELECT * FROM sync_runs ORDER BY updatedAt DESC LIMIT 1")
    fun observeLatest(): Flow<SyncRunEntity?>

    @Query("SELECT * FROM sync_runs WHERE id = :runId LIMIT 1")
    suspend fun get(runId: String): SyncRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(run: SyncRunEntity)
}

@Dao
interface SyncCheckpointDao {
    @Query("SELECT * FROM sync_checkpoints WHERE runId = :runId LIMIT 1")
    suspend fun get(runId: String): SyncCheckpointEntity?

    @Query("SELECT * FROM sync_checkpoints ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatest(): SyncCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkpoint: SyncCheckpointEntity)

    @Query("DELETE FROM sync_checkpoints WHERE runId = :runId")
    suspend fun delete(runId: String)
}
