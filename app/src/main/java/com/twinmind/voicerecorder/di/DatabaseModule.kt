package com.twinmind.voicerecorder.di

import android.content.Context
import androidx.room.Room
import com.twinmind.voicerecorder.data.local.VoiceRecorderDatabase
import com.twinmind.voicerecorder.data.local.dao.AudioChunkDao
import com.twinmind.voicerecorder.data.local.dao.RecordingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VoiceRecorderDatabase {
        return Room.databaseBuilder(
            context,
            VoiceRecorderDatabase::class.java,
            VoiceRecorderDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideRecordingDao(database: VoiceRecorderDatabase): RecordingDao {
        return database.recordingDao()
    }

    @Provides
    fun provideAudioChunkDao(database: VoiceRecorderDatabase): AudioChunkDao {
        return database.audioChunkDao()
    }
}
