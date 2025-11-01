package com.twinmind.voicerecorder.data.repository

import com.twinmind.voicerecorder.data.local.dao.AudioChunkDao
import com.twinmind.voicerecorder.data.local.dao.RecordingDao
import com.twinmind.voicerecorder.data.local.entity.AudioChunk
import com.twinmind.voicerecorder.data.local.entity.Recording
import com.twinmind.voicerecorder.data.local.entity.RecordingStatus
import com.twinmind.voicerecorder.data.local.entity.TranscriptionStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    private val recordingDao: RecordingDao,
    private val audioChunkDao: AudioChunkDao
) {
    // Recording operations
    fun getAllRecordings(): Flow<List<Recording>> = recordingDao.getAllRecordings()

    fun getRecordingById(id: Long): Flow<Recording?> = recordingDao.getRecordingById(id)

    suspend fun getRecordingByIdSync(id: Long): Recording? = recordingDao.getRecordingByIdSync(id)

    suspend fun getActiveRecording(): Recording? = recordingDao.getActiveRecording()

    suspend fun insertRecording(recording: Recording): Long = recordingDao.insertRecording(recording)

    suspend fun updateRecording(recording: Recording) = recordingDao.updateRecording(recording)

    suspend fun updateRecordingStatus(id: Long, status: RecordingStatus) {
        recordingDao.updateStatus(id, status)
    }

    suspend fun updatePauseReason(id: Long, reason: String?) {
        recordingDao.updatePauseReason(id, reason)
    }

    suspend fun stopRecording(id: Long, endTime: Long, duration: Long, status: RecordingStatus) {
        recordingDao.stopRecording(id, endTime, duration, status)
    }

    suspend fun updateTranscript(
        id: Long,
        transcript: String,
        transcribedChunks: Int,
        transcriptionStatus: TranscriptionStatus
    ) {
        recordingDao.updateTranscript(id, transcript, transcribedChunks, transcriptionStatus)
    }

    suspend fun updateSummary(
        id: Long,
        summary: String,
        title: String?,
        actionItems: String?,
        keyPoints: String?,
        status: RecordingStatus
    ) {
        recordingDao.updateSummary(id, summary, title, actionItems, keyPoints, status)
    }

    suspend fun updateError(id: Long, error: String) {
        recordingDao.updateError(id, error)
    }

    suspend fun deleteRecording(id: Long) {
        recordingDao.deleteRecordingById(id)
    }

    // Audio chunk operations
    fun getChunksByRecordingId(recordingId: Long): Flow<List<AudioChunk>> =
        audioChunkDao.getChunksByRecordingId(recordingId)

    suspend fun getChunksByRecordingIdSync(recordingId: Long): List<AudioChunk> =
        audioChunkDao.getChunksByRecordingIdSync(recordingId)

    suspend fun getChunkById(id: Long): AudioChunk? = audioChunkDao.getChunkById(id)

    suspend fun getChunksByStatus(recordingId: Long, status: TranscriptionStatus): List<AudioChunk> =
        audioChunkDao.getChunksByStatus(recordingId, status)

    suspend fun insertChunk(chunk: AudioChunk): Long = audioChunkDao.insertChunk(chunk)

    suspend fun updateChunk(chunk: AudioChunk) = audioChunkDao.updateChunk(chunk)

    suspend fun updateChunkTranscription(id: Long, status: TranscriptionStatus, text: String?) {
        audioChunkDao.updateTranscription(id, status, text)
    }

    suspend fun incrementChunkRetries(id: Long) {
        audioChunkDao.incrementRetries(id)
    }

    suspend fun updateChunkError(id: Long, error: String) {
        audioChunkDao.updateError(id, error)
    }

    suspend fun getChunkCount(recordingId: Long): Int = audioChunkDao.getChunkCount(recordingId)

    suspend fun deleteChunksByRecordingId(recordingId: Long) {
        audioChunkDao.deleteChunksByRecordingId(recordingId)
    }
}
