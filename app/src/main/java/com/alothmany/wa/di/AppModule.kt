package com.alothmany.wa.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alothmany.wa.data.local.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `group_sync_meta` (
                    `groupId` TEXT NOT NULL,
                    `sourceId` TEXT NOT NULL,
                    `isUnread` INTEGER NOT NULL,
                    `isActive` INTEGER NOT NULL,
                    `isLocked` INTEGER NOT NULL,
                    `isDeleted` INTEGER NOT NULL,
                    `isCommunity` INTEGER NOT NULL,
                    `confidence` TEXT NOT NULL,
                    `lastSeenRunId` TEXT,
                    `missingStreak` INTEGER NOT NULL,
                    `firstSeenAt` INTEGER NOT NULL,
                    `lastSeenAt` INTEGER NOT NULL,
                    PRIMARY KEY(`groupId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_sync_meta_sourceId` ON `group_sync_meta` (`sourceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_sync_meta_isUnread` ON `group_sync_meta` (`isUnread`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_sync_meta_isActive` ON `group_sync_meta` (`isActive`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_sync_meta_isLocked` ON `group_sync_meta` (`isLocked`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_sync_meta_isDeleted` ON `group_sync_meta` (`isDeleted`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_sync_meta_isCommunity` ON `group_sync_meta` (`isCommunity`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_sync_meta_lastSeenRunId` ON `group_sync_meta` (`lastSeenRunId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sync_runs` (
                    `id` TEXT NOT NULL,
                    `sourceId` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `stage` TEXT NOT NULL,
                    `discoveredCount` INTEGER NOT NULL,
                    `newCount` INTEGER NOT NULL,
                    `processedScreens` INTEGER NOT NULL,
                    `currentGroupName` TEXT,
                    `errorMessage` TEXT,
                    `startedAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `completedAt` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_runs_sourceId` ON `sync_runs` (`sourceId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_runs_status` ON `sync_runs` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_runs_updatedAt` ON `sync_runs` (`updatedAt`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sync_checkpoints` (
                    `runId` TEXT NOT NULL,
                    `sourceId` TEXT NOT NULL,
                    `stage` TEXT NOT NULL,
                    `lastAnchor` TEXT,
                    `lastScreenFingerprint` TEXT,
                    `consecutiveEndPasses` INTEGER NOT NULL,
                    `processedScreens` INTEGER NOT NULL,
                    `discoveredCount` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`runId`)
                )
                """.trimIndent()
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "wa_al_othmany.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun provideLogDao(db: AppDatabase) = db.logDao()
    @Provides fun provideTaskDao(db: AppDatabase) = db.taskDao()
    @Provides fun provideSourceDao(db: AppDatabase) = db.sourceDao()
    @Provides fun provideGroupDao(db: AppDatabase) = db.groupDao()
    @Provides fun provideLinkDao(db: AppDatabase) = db.linkDao()
    @Provides fun provideGroupSyncMetaDao(db: AppDatabase) = db.groupSyncMetaDao()
    @Provides fun provideSyncRunDao(db: AppDatabase) = db.syncRunDao()
    @Provides fun provideSyncCheckpointDao(db: AppDatabase) = db.syncCheckpointDao()
}
