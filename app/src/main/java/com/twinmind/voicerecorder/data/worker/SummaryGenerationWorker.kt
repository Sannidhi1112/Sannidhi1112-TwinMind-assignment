package com.twinmind.voicerecorder.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.twinmind.voicerecorder.data.local.entity.RecordingStatus
import com.twinmind.voicerecorder.data.remote.SummaryService
import com.twinmind.voicerecorder.data.repository.RecordingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class SummaryGenerationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: RecordingRepository,
    private val summaryService: SummaryService
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_RECORDING_ID = "recording_id"
        const val MAX_RETRIES = 3

        fun createWorkRequest(recordingId: Long): OneTimeWorkRequest {
            val data = Data.Builder()
                .putLong(KEY_RECORDING_ID, recordingId)
                .build()

            return OneTimeWorkRequestBuilder<SummaryGenerationWorker>()
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
            // Get recording
            val recording = repository.getRecordingByIdSync(recordingId)
            if (recording == null || recording.transcript.isNullOrEmpty()) {
                return@withContext Result.failure()
            }

            // Update status
            repository.updateRecordingStatus(recordingId, RecordingStatus.GENERATING_SUMMARY)

            // Generate summary with streaming
            var finalTitle = ""
            var finalSummary = ""
            var finalActionItems = ""
            var finalKeyPoints = ""

            summaryService.generateSummary(recording.transcript)
                .onEach { response ->
                    // Update partial results in database
                    finalTitle = response.title
                    finalSummary = response.summary
                    finalActionItems = response.actionItems.joinToString("\n")
                    finalKeyPoints = response.keyPoints.joinToString("\n")

                    // Save progress
                    if (response.title.isNotEmpty() &&
                        response.summary.isNotEmpty() &&
                        response.actionItems.isNotEmpty() &&
                        response.keyPoints.isNotEmpty()
                    ) {
                        repository.updateSummary(
                            recordingId,
                            finalTitle,
                            finalSummary,
                            finalActionItems,
                            finalKeyPoints,
                            RecordingStatus.SUMMARY_COMPLETE
                        )
                    }
                }
                .catch { e ->
                    e.printStackTrace()
                    throw e
                }
                .collect()

            Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                repository.updateRecordingStatus(recordingId, RecordingStatus.SUMMARY_FAILED)
                Result.failure()
            }
        }
    }
}
