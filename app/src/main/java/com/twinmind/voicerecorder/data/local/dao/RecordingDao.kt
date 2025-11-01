package com.twinmind.voicerecorder.data.local.dao

import androidx.room.*
import com.twinmind.voicerecorder.data.local.entity.Recording
import com.twinmind.voicerecorder.data.local.entity.RecordingStatus
import com.twinmind.voicerecorder.data.local.entity.TranscriptionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings ORDER BY startTime DESC")
    fun getAllRecordings(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE id = :id")
    fun getRecordingById(id: Long): Flow<Recording?>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingByIdSync(id: Long): Recording?

    @Query("SELECT * FROM recordings WHERE status = :status LIMIT 1")
    suspend fun getActiveRecording(status: RecordingStatus = RecordingStatus.RECORDING): Recording?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: Recording): Long

    @Update
    suspend fun updateRecording(recording: Recording)

    @Query("UPDATE recordings SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: RecordingStatus)

    @Query("UPDATE recordings SET pauseReason = :reason WHERE id = :id")
    suspend fun updatePauseReason(id: Long, reason: String?)

    @Query("UPDATE recordings SET endTime = :endTime, duration = :duration, status = :status WHERE id = :id")
    suspend fun stopRecording(id: Long, endTime: Long, duration: Long, status: RecordingStatus)

    @Query("UPDATE recordings SET transcript = :transcript, transcribedChunks = :transcribedChunks, transcriptionStatus = :transcriptionStatus WHERE id = :id")
    suspend fun updateTranscript(id: Long, transcript: String, transcribedChunks: Int, transcriptionStatus: TranscriptionStatus)

    @Query("UPDATE recordings SET summary = :summary, summaryTitle = :title, summaryActionItems = :actionItems, summaryKeyPoints = :keyPoints, status = :status WHERE id = :id")
    suspend fun updateSummary(id: Long, summary: String, title: String?, actionItems: String?, keyPoints: String?, status: RecordingStatus)

    @Query("UPDATE recordings SET errorMessage = :error WHERE id = :id")
    suspend fun updateError(id: Long, error: String)

    @Delete
    suspend fun deleteRecording(recording: Recording)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Long)
}
