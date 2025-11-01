package com.twinmind.voicerecorder.data.local

import androidx.room.TypeConverter
import com.twinmind.voicerecorder.data.local.entity.RecordingStatus
import com.twinmind.voicerecorder.data.local.entity.TranscriptionStatus

class Converters {

    @TypeConverter
    fun fromRecordingStatus(value: RecordingStatus): String {
        return value.name
    }

    @TypeConverter
    fun toRecordingStatus(value: String): RecordingStatus {
        return RecordingStatus.valueOf(value)
    }

    @TypeConverter
    fun fromTranscriptionStatus(value: TranscriptionStatus): String {
        return value.name
    }

    @TypeConverter
    fun toTranscriptionStatus(value: String): TranscriptionStatus {
        return TranscriptionStatus.valueOf(value)
    }
}
