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
    val duration: Long = 0, // in milliseconds
    val status: RecordingStatus,
    val totalChunks: Int = 0,
    val transcribedChunks: Int = 0,
    val transcript: String? = null,
    val summary: String? = null,
    val summaryTitle: String? = null,
    val summaryContent: String? = null,
    val actionItems: String? = null,
    val keyPoints: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class RecordingStatus {
    RECORDING,
    PAUSED_CALL,
    PAUSED_AUDIO_FOCUS,
    STOPPED,
    TRANSCRIBING,
    TRANSCRIPTION_COMPLETE,
    TRANSCRIPTION_FAILED,
    GENERATING_SUMMARY,
    SUMMARY_COMPLETE,
    SUMMARY_FAILED
}
