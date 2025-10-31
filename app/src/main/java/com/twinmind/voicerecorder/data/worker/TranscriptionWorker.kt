package com.twinmind.voicerecorder.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Result
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.twinmind.voicerecorder.data.local.entity.RecordingStatus
import com.twinmind.voicerecorder.data.local.entity.TranscriptionStatus
import com.twinmind.voicerecorder.data.remote.TranscriptionService
import com.twinmind.voicerecorder.data.repository.RecordingRepository
import com.twinmind.voicerecorder.di.workerEntryPoint
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: RecordingRepository,
    private val transcriptionService: TranscriptionService
) : CoroutineWorker(context, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface TranscriptionWorkerEntryPoint {
        fun recordingRepository(): RecordingRepository
        fun transcriptionService(): TranscriptionService
    }

    private constructor(
        context: Context,
        workerParams: WorkerParameters,
        entryPoint: TranscriptionWorkerEntryPoint
    ) : this(
        context,
        workerParams,
        entryPoint.recordingRepository(),
        entryPoint.transcriptionService()
    )

    @Suppress("unused")
    constructor(context: Context, workerParams: WorkerParameters) : this(
        context,
        workerParams,
        context.workerEntryPoint<TranscriptionWorkerEntryPoint>()
    )

    companion object {
        const val KEY_RECORDING_ID = "recording_id"
        const val MAX_RETRIES = 3

        fun createWorkRequest(recordingId: Long): OneTimeWorkRequest {
            val data = Data.Builder()
                .putLong(KEY_RECORDING_ID, recordingId)
                .build()

            return OneTimeWorkRequestBuilder<TranscriptionWorker>()
                .setInputData(data)
                // No network constraints for mock service - will work offline
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
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
                return@withContext Result.failure()
            }

            var successCount = 0
            var failureCount = 0

            // Transcribe each chunk
            for (chunk in chunks) {
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

            val transcribedCount = allChunks.count { it.transcriptionStatus == TranscriptionStatus.COMPLETED }

            // Update recording with transcript
            if (completeTranscript.isNotEmpty()) {
                repository.updateTranscript(
                    recordingId,
                    completeTranscript,
                    transcribedCount,
                    if (failureCount == 0) RecordingStatus.TRANSCRIPTION_COMPLETE
                    else RecordingStatus.TRANSCRIPTION_FAILED
                )

                // Start summary generation if transcription is complete
                if (failureCount == 0) {
                    startSummaryGeneration(recordingId)
                }

                return@withContext Result.success()
            }

            // Retry if there were failures
            if (failureCount > 0 && runAttemptCount < MAX_RETRIES) {
                return@withContext Result.retry()
            }

            repository.updateRecordingStatus(recordingId, RecordingStatus.TRANSCRIPTION_FAILED)
            Result.failure()

        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                repository.updateRecordingStatus(recordingId, RecordingStatus.TRANSCRIPTION_FAILED)
                Result.failure()
            }
        }
    }

    private suspend fun startSummaryGeneration(recordingId: Long) {
        val workRequest = SummaryGenerationWorker.createWorkRequest(recordingId)
        WorkManager.getInstance(applicationContext).enqueue(workRequest)
    }
}
