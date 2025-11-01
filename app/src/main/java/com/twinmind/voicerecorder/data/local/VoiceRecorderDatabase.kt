package com.twinmind.voicerecorder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.twinmind.voicerecorder.data.local.dao.AudioChunkDao
import com.twinmind.voicerecorder.data.local.dao.RecordingDao
import com.twinmind.voicerecorder.data.local.entity.AudioChunk
import com.twinmind.voicerecorder.data.local.entity.Recording

@Database(
    entities = [Recording::class, AudioChunk::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class VoiceRecorderDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun audioChunkDao(): AudioChunkDao

    companion object {
        const val DATABASE_NAME = "voice_recorder_db"
    }
}
