package com.twinmind.voicerecorder.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.twinmind.voicerecorder.data.local.entity.RecordingStatus
import com.twinmind.voicerecorder.data.local.entity.TranscriptionStatus
import com.twinmind.voicerecorder.data.remote.TranscriptionService
import com.twinmind.voicerecorder.data.repository.RecordingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val repository: RecordingRepository,
    private val transcriptionService: TranscriptionService
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_RECORDING_ID = "recording_id"
        const val MAX_RETRIES = 3

        fun createWorkRequest(recordingId: Long): OneTimeWorkRequest {
            val data = Data.Builder()
                .putLong(KEY_RECORDING_ID, recordingId)
                .build()

            return OneTimeWorkRequestBuilder<TranscriptionWorker>()
                .setInputData(data)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .addTag("transcription_$recordingId")
                .build()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val recordingId = inputData.getLong(KEY_RECORDING_ID, -1L)
        if (recordingId == -1L) {
            return@withContext Result.failure()
        }

        try {
            // Update recording status
            repository.updateRecordingStatus(recordingId, RecordingStatus.TRANSCRIBING)

            // Get all chunks for this recording
            val chunks = repository.getChunksByRecordingIdSync(recordingId)
                .sortedBy { it.chunkIndex }

            if (chunks.isEmpty()) {
                repository.updateError(recordingId, "No audio chunks found")
                return@withContext Result.failure()
            }

            var successCount = 0
            var failureCount = 0

            // Transcribe each chunk
            for (chunk in chunks) {
                // Skip already transcribed chunks
                if (chunk.transcriptionStatus == TranscriptionStatus.COMPLETED) {
                    successCount++
                    continue
                }

                // Update chunk status
                repository.updateChunkTranscription(
                    chunk.id,
                    TranscriptionStatus.IN_PROGRESS,
                    null
                )

                // Transcribe the chunk
                val audioFile = File(chunk.filePath)
                if (!audioFile.exists()) {
                    failureCount++
                    repository.updateChunkTranscription(
                        chunk.id,
                        TranscriptionStatus.FAILED,
                        null
                    )
                    repository.updateChunkError(chunk.id, "Audio file not found")
                    continue
                }

                val result = transcriptionService.transcribeAudio(audioFile)
                result.fold(
                    onSuccess = { transcription ->
                        repository.updateChunkTranscription(
                            chunk.id,
                            TranscriptionStatus.COMPLETED,
                            transcription
                        )
                        successCount++
                    },
                    onFailure = { error ->
                        // Increment retry count
                        repository.incrementChunkRetries(chunk.id)

                        // Check if we should retry
                        val updatedChunk = repository.getChunkById(chunk.id)
                        if (updatedChunk != null && updatedChunk.transcriptionRetries < MAX_RETRIES) {
                            // Mark as pending for retry
                            repository.updateChunkTranscription(
                                chunk.id,
                                TranscriptionStatus.PENDING,
                                null
                            )
                        } else {
                            // Max retries reached, mark as failed
                            repository.updateChunkTranscription(
                                chunk.id,
                                TranscriptionStatus.FAILED,
                                null
                            )
                            repository.updateChunkError(chunk.id, error.message ?: "Transcription failed")
                            failureCount++
                        }
                    }
                )
            }

            // Build complete transcript from all chunks
            val allChunks = repository.getChunksByRecordingIdSync(recordingId)
                .sortedBy { it.chunkIndex }

            val completeTranscript = allChunks
                .mapNotNull { it.transcriptionText }
                .joinToString(" ")

            val transcribedCount = allChunks.count {
                it.transcriptionStatus == TranscriptionStatus.COMPLETED
            }

            // Update recording with transcript
            if (completeTranscript.isNotEmpty()) {
                val finalStatus = if (failureCount == 0) {
                    RecordingStatus.TRANSCRIPTION_COMPLETE
                } else {
                    RecordingStatus.TRANSCRIPTION_FAILED
                }

                repository.updateTranscript(
                    recordingId,
                    completeTranscript,
                    transcribedCount,
                    if (failureCount == 0) TranscriptionStatus.COMPLETED else TranscriptionStatus.FAILED
                )
                repository.updateRecordingStatus(recordingId, finalStatus)

                // Start summary generation if transcription is complete
                if (failureCount == 0) {
                    startSummaryGeneration(recordingId)
                }

                return@withContext Result.success()
            }

            // Retry if there were failures and we haven't exceeded max attempts
            if (failureCount > 0 && runAttemptCount < MAX_RETRIES) {
                return@withContext Result.retry()
            }

            repository.updateRecordingStatus(recordingId, RecordingStatus.TRANSCRIPTION_FAILED)
            repository.updateError(recordingId, "Transcription failed for all chunks")
            Result.failure()

        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                repository.updateRecordingStatus(recordingId, RecordingStatus.TRANSCRIPTION_FAILED)
                repository.updateError(recordingId, e.message ?: "Transcription error")
                Result.failure()
            }
        }
    }

    private fun startSummaryGeneration(recordingId: Long) {
        val workRequest = SummaryGenerationWorker.createWorkRequest(recordingId)
        WorkManager.getInstance(appContext).enqueue(workRequest)
    }
}
