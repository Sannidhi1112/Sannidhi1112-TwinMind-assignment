package com.twinmind.voicerecorder.data.local.dao

import androidx.room.*
import com.twinmind.voicerecorder.data.local.entity.Recording
import com.twinmind.voicerecorder.data.local.entity.RecordingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun getAllRecordings(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun getRecordingById(id: Long): Flow<Recording?>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingByIdSync(id: Long): Recording?

    @Query("SELECT * FROM recordings WHERE status = :status LIMIT 1")
    suspend fun getRecordingByStatus(status: RecordingStatus): Recording?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: Recording): Long

    @Update
    suspend fun updateRecording(recording: Recording)

    @Query("UPDATE recordings SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateRecordingStatus(id: Long, status: RecordingStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE recordings SET endTime = :endTime, duration = :duration, totalChunks = :totalChunks, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun finalizeRecording(
        id: Long,
        endTime: Long,
        duration: Long,
        totalChunks: Int,
        status: RecordingStatus,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE recordings SET transcript = :transcript, transcribedChunks = :transcribedChunks, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTranscript(
        id: Long,
        transcript: String,
        transcribedChunks: Int,
        status: RecordingStatus,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE recordings SET summaryTitle = :title, summaryContent = :summary, actionItems = :actionItems, keyPoints = :keyPoints, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSummary(
        id: Long,
        title: String,
        summary: String,
        actionItems: String,
        keyPoints: String,
        status: RecordingStatus,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun deleteRecording(recording: Recording)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Long)
}
