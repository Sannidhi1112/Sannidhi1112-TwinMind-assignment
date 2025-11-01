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
    indices = [Index("recordingId")]
)
data class AudioChunk(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val recordingId: Long,
    val chunkIndex: Int,
    val filePath: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val fileSize: Long,
    val transcriptionStatus: TranscriptionStatus = TranscriptionStatus.PENDING,
    val transcriptionText: String? = null,
    val transcriptionRetries: Int = 0,
    val errorMessage: String? = null
)
