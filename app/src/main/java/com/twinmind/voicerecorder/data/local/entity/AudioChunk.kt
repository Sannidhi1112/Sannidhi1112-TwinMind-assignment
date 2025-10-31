package com.twinmind.voicerecorder.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_chunks",
    foreignKeys = [
        ForeignKey(
            entity = Recording::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("recordingId"), Index("chunkIndex")]
)
data class AudioChunk(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordingId: Long,
    val chunkIndex: Int,
    val filePath: String,
    val duration: Long, // in milliseconds
    val fileSize: Long, // in bytes
    val startTime: Long,
    val endTime: Long,
    val transcriptionStatus: TranscriptionStatus = TranscriptionStatus.PENDING,
    val transcriptionText: String? = null,
    val transcriptionRetries: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class TranscriptionStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
