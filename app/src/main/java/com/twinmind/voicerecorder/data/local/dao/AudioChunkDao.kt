package com.twinmind.voicerecorder.data.local.dao

import androidx.room.*
import com.twinmind.voicerecorder.data.local.entity.AudioChunk
import com.twinmind.voicerecorder.data.local.entity.TranscriptionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioChunkDao {

    @Query("SELECT * FROM audio_chunks WHERE recordingId = :recordingId ORDER BY chunkIndex ASC")
    fun getChunksByRecordingId(recordingId: Long): Flow<List<AudioChunk>>

    @Query("SELECT * FROM audio_chunks WHERE recordingId = :recordingId ORDER BY chunkIndex ASC")
    suspend fun getChunksByRecordingIdSync(recordingId: Long): List<AudioChunk>

    @Query("SELECT * FROM audio_chunks WHERE id = :id")
    suspend fun getChunkById(id: Long): AudioChunk?

    @Query("SELECT * FROM audio_chunks WHERE recordingId = :recordingId AND transcriptionStatus = :status ORDER BY chunkIndex ASC")
    suspend fun getChunksByStatus(recordingId: Long, status: TranscriptionStatus): List<AudioChunk>

    @Query("SELECT * FROM audio_chunks WHERE transcriptionStatus = :status ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPendingChunks(status: TranscriptionStatus = TranscriptionStatus.PENDING, limit: Int = 10): List<AudioChunk>

    @Query("SELECT COUNT(*) FROM audio_chunks WHERE recordingId = :recordingId")
    suspend fun getChunkCountByRecordingId(recordingId: Long): Int

    @Query("SELECT COUNT(*) FROM audio_chunks WHERE recordingId = :recordingId AND transcriptionStatus = :status")
    suspend fun getChunkCountByStatus(recordingId: Long, status: TranscriptionStatus): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: AudioChunk): Long

    @Update
    suspend fun updateChunk(chunk: AudioChunk)

    @Query("UPDATE audio_chunks SET transcriptionStatus = :status, transcriptionText = :text WHERE id = :id")
    suspend fun updateChunkTranscription(id: Long, status: TranscriptionStatus, text: String?)

    @Query("UPDATE audio_chunks SET transcriptionRetries = transcriptionRetries + 1 WHERE id = :id")
    suspend fun incrementRetries(id: Long)

    @Delete
    suspend fun deleteChunk(chunk: AudioChunk)

    @Query("DELETE FROM audio_chunks WHERE recordingId = :recordingId")
    suspend fun deleteChunksByRecordingId(recordingId: Long)
}
