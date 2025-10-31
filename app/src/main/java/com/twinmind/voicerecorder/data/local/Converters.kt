package com.twinmind.voicerecorder.data.local

import androidx.room.TypeConverter
import com.twinmind.voicerecorder.data.local.entity.RecordingStatus
import com.twinmind.voicerecorder.data.local.entity.TranscriptionStatus

class Converters {

    @TypeConverter
    fun fromRecordingStatus(status: RecordingStatus): String {
        return status.name
    }

    @TypeConverter
    fun toRecordingStatus(status: String): RecordingStatus {
        return RecordingStatus.valueOf(status)
    }

    @TypeConverter
    fun fromTranscriptionStatus(status: TranscriptionStatus): String {
        return status.name
    }

    @TypeConverter
    fun toTranscriptionStatus(status: String): TranscriptionStatus {
        return TranscriptionStatus.valueOf(status)
    }
}
