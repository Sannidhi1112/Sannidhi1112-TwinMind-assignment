package com.twinmind.voicerecorder.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Result
import androidx.work.WorkerParameters
import com.twinmind.voicerecorder.data.local.entity.RecordingStatus
import com.twinmind.voicerecorder.data.remote.SummaryService
import com.twinmind.voicerecorder.data.repository.RecordingRepository
import com.twinmind.voicerecorder.di.workerEntryPoint
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface SummaryWorkerEntryPoint {
        fun recordingRepository(): RecordingRepository
        fun summaryService(): SummaryService
    }

    private constructor(
        context: Context,
        workerParams: WorkerParameters,
        entryPoint: SummaryWorkerEntryPoint
    ) : this(
        context,
        workerParams,
        entryPoint.recordingRepository(),
        entryPoint.summaryService()
    )

    @Suppress("unused")
    constructor(context: Context, workerParams: WorkerParameters) : this(
        context,
        workerParams,
        context.workerEntryPoint<SummaryWorkerEntryPoint>()
    )

    companion object {
        const val KEY_RECORDING_ID = "recording_id"
        const val MAX_RETRIES = 3
        private const val SUMMARY_WORK_NAME_PREFIX = "summary_generation_"

        fun uniqueWorkName(recordingId: Long): String =
            SUMMARY_WORK_NAME_PREFIX + recordingId

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
            var summaryCompleted = false

            summaryService.generateSummary(recording.transcript)
                .onEach { response ->
                    finalTitle = response.title
                    finalSummary = response.summary
                    finalActionItems = response.actionItems.joinToString("\n")
                    finalKeyPoints = response.keyPoints.joinToString("\n")

                    val hasCompleteSummary = response.title.isNotBlank() &&
                        response.summary.isNotBlank() &&
                        response.actionItems.isNotEmpty() &&
                        response.keyPoints.isNotEmpty()

                    repository.updateSummary(
                        recordingId,
                        finalTitle,
                        finalSummary,
                        finalActionItems,
                        finalKeyPoints,
                        if (hasCompleteSummary) {
                            summaryCompleted = true
                            RecordingStatus.SUMMARY_COMPLETE
                        } else {
                            RecordingStatus.GENERATING_SUMMARY
                        }
                    )
                }
                .catch { e ->
                    e.printStackTrace()
                    throw e
                }
                .collect()

            if (summaryCompleted) {
                Result.success()
            } else {
                repository.updateRecordingStatus(recordingId, RecordingStatus.SUMMARY_FAILED)
                Result.failure()
            }

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
