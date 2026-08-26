package com.alothmany.wa.data.local.dao

import androidx.room.*
import com.alothmany.wa.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<LogEntity>>

    @Insert
    suspend fun insert(log: LogEntity)

    @Query("DELETE FROM logs")
    suspend fun clear()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM whatsapp_sources ORDER BY displayName")
    fun observeAll(): Flow<List<WhatsAppSourceEntity>>

    @Query("SELECT COUNT(*) FROM whatsapp_sources")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WhatsAppSourceEntity>)
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY displayName")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT COUNT(*) FROM groups WHERE isCommunity = 0")
    fun observeGroupCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM groups WHERE isCommunity = 1")
    fun observeCommunityCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<GroupEntity>)
}

@Dao
interface LinkDao {
    @Query("SELECT * FROM links ORDER BY lastSeenAt DESC")
    fun observeAll(): Flow<List<LinkEntity>>

    @Query("SELECT COUNT(*) FROM links")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LinkEntity>)
}
