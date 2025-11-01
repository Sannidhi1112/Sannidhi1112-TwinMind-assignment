package com.twinmind.voicerecorder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val startTime: Long,
    val endTime: Long? = null,
    val duration: Long = 0,
    val status: RecordingStatus = RecordingStatus.RECORDING,
    val transcriptionStatus: TranscriptionStatus = TranscriptionStatus.PENDING,
    val transcript: String? = null,
    val summary: String? = null,
    val summaryTitle: String? = null,
    val summaryActionItems: String? = null,
    val summaryKeyPoints: String? = null,
    val totalChunks: Int = 0,
    val transcribedChunks: Int = 0,
    val pauseReason: String? = null,
    val errorMessage: String? = null
)

enum class RecordingStatus {
    RECORDING,
    PAUSED,
    STOPPED,
    TRANSCRIBING,
    TRANSCRIPTION_COMPLETE,
    TRANSCRIPTION_FAILED,
    GENERATING_SUMMARY,
    SUMMARY_COMPLETE,
    SUMMARY_FAILED,
    COMPLETED
}

enum class TranscriptionStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
