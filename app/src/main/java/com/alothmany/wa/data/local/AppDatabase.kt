package com.alothmany.wa.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.alothmany.wa.data.local.dao.*
import com.alothmany.wa.data.local.entity.*

@Database(
    entities = [
        LogEntity::class,
        TaskEntity::class,
        WhatsAppSourceEntity::class,
        GroupEntity::class,
        LinkEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao
    abstract fun taskDao(): TaskDao
    abstract fun sourceDao(): SourceDao
    abstract fun groupDao(): GroupDao
    abstract fun linkDao(): LinkDao
}
