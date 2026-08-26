package com.alothmany.wa.di

import android.content.Context
import androidx.room.Room
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
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "wa_al_othmany.db")
            .build()

    @Provides fun provideLogDao(db: AppDatabase) = db.logDao()
    @Provides fun provideTaskDao(db: AppDatabase) = db.taskDao()
    @Provides fun provideSourceDao(db: AppDatabase) = db.sourceDao()
    @Provides fun provideGroupDao(db: AppDatabase) = db.groupDao()
    @Provides fun provideLinkDao(db: AppDatabase) = db.linkDao()
}
