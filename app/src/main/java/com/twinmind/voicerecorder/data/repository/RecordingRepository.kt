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
    fun getAllRecordings(): Flow<List<Recording>> {
        return recordingDao.getAllRecordings()
    }

    fun getRecordingById(id: Long): Flow<Recording?> {
        return recordingDao.getRecordingById(id)
    }

    suspend fun getRecordingByIdSync(id: Long): Recording? {
        return recordingDao.getRecordingByIdSync(id)
    }

    suspend fun getRecordingByStatus(status: RecordingStatus): Recording? {
        return recordingDao.getRecordingByStatus(status)
    }

    suspend fun insertRecording(recording: Recording): Long {
        return recordingDao.insertRecording(recording)
    }

    suspend fun updateRecording(recording: Recording) {
        recordingDao.updateRecording(recording)
    }

    suspend fun updateRecordingStatus(id: Long, status: RecordingStatus) {
        recordingDao.updateRecordingStatus(id, status)
    }

    suspend fun finalizeRecording(
        id: Long,
        endTime: Long,
        duration: Long,
        totalChunks: Int,
        status: RecordingStatus
    ) {
        recordingDao.finalizeRecording(id, endTime, duration, totalChunks, status)
    }

    suspend fun updateTranscript(
        id: Long,
        transcript: String,
        transcribedChunks: Int,
        status: RecordingStatus
    ) {
        recordingDao.updateTranscript(id, transcript, transcribedChunks, status)
    }

    suspend fun updateSummary(
        id: Long,
        title: String,
        summary: String,
        actionItems: String,
        keyPoints: String,
        status: RecordingStatus
    ) {
        recordingDao.updateSummary(id, title, summary, actionItems, keyPoints, status)
    }

    suspend fun deleteRecording(recording: Recording) {
        recordingDao.deleteRecording(recording)
    }

    suspend fun deleteRecordingById(id: Long) {
        recordingDao.deleteRecordingById(id)
    }

    // Audio chunk operations
    fun getChunksByRecordingId(recordingId: Long): Flow<List<AudioChunk>> {
        return audioChunkDao.getChunksByRecordingId(recordingId)
    }

    suspend fun getChunksByRecordingIdSync(recordingId: Long): List<AudioChunk> {
        return audioChunkDao.getChunksByRecordingIdSync(recordingId)
    }

    suspend fun getChunkById(id: Long): AudioChunk? {
        return audioChunkDao.getChunkById(id)
    }

    suspend fun getChunksByStatus(recordingId: Long, status: TranscriptionStatus): List<AudioChunk> {
        return audioChunkDao.getChunksByStatus(recordingId, status)
    }

    suspend fun getPendingChunks(limit: Int = 10): List<AudioChunk> {
        return audioChunkDao.getPendingChunks(TranscriptionStatus.PENDING, limit)
    }

    suspend fun getChunkCountByRecordingId(recordingId: Long): Int {
        return audioChunkDao.getChunkCountByRecordingId(recordingId)
    }

    suspend fun getChunkCountByStatus(recordingId: Long, status: TranscriptionStatus): Int {
        return audioChunkDao.getChunkCountByStatus(recordingId, status)
    }

    suspend fun insertChunk(chunk: AudioChunk): Long {
        return audioChunkDao.insertChunk(chunk)
    }

    suspend fun updateChunk(chunk: AudioChunk) {
        audioChunkDao.updateChunk(chunk)
    }

    suspend fun updateChunkTranscription(id: Long, status: TranscriptionStatus, text: String?) {
        audioChunkDao.updateChunkTranscription(id, status, text)
    }

    suspend fun incrementChunkRetries(id: Long) {
        audioChunkDao.incrementRetries(id)
    }

    suspend fun deleteChunk(chunk: AudioChunk) {
        audioChunkDao.deleteChunk(chunk)
    }

    suspend fun deleteChunksByRecordingId(recordingId: Long) {
        audioChunkDao.deleteChunksByRecordingId(recordingId)
    }
}
