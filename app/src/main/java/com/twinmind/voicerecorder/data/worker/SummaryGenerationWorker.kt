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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class SummaryGenerationWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val workerParams: WorkerParameters,
    private val repository: RecordingRepository,
    private val summaryService: SummaryService
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_RECORDING_ID = "recording_id"
        const val MAX_RETRIES = 3

        fun createWorkRequest(recordingId: Long): OneTimeWorkRequest {
            val data = Data.Builder()
                .putLong(KEY_RECORDING_ID, recordingId)
                .build()

            return OneTimeWorkRequestBuilder<SummaryGenerationWorker>()
                .setInputData(data)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.SECONDS
                )
                .addTag("summary_$recordingId")
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
            repository.updateRecordingStatus(recordingId, RecordingStatus.GENERATING_SUMMARY)

            // Get the recording
            val recording = repository.getRecordingByIdSync(recordingId)
            if (recording == null) {
                return@withContext Result.failure()
            }

            val transcript = recording.transcript
            if (transcript.isNullOrEmpty()) {
                repository.updateError(recordingId, "No transcript available for summary generation")
                repository.updateRecordingStatus(recordingId, RecordingStatus.SUMMARY_FAILED)
                return@withContext Result.failure()
            }

            // Generate summary with streaming
            val fullSummaryBuilder = StringBuilder()

            summaryService.generateSummary(transcript).collect { chunk ->
                fullSummaryBuilder.append(chunk)
                // Update summary in database as it streams
                repository.updateSummary(
                    recordingId,
                    fullSummaryBuilder.toString(),
                    null,
                    null,
                    null,
                    RecordingStatus.GENERATING_SUMMARY
                )
            }

            val fullSummary = fullSummaryBuilder.toString()

            // Parse the summary
            val parsedSummary = summaryService.parseSummaryResponse(fullSummary)

            // Update with final parsed summary
            repository.updateSummary(
                recordingId,
                parsedSummary.summary,
                parsedSummary.title,
                parsedSummary.actionItems.joinToString("\n"),
                parsedSummary.keyPoints.joinToString("\n"),
                RecordingStatus.SUMMARY_COMPLETE
            )

            // Mark as completed
            repository.updateRecordingStatus(recordingId, RecordingStatus.COMPLETED)

            Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                repository.updateRecordingStatus(recordingId, RecordingStatus.SUMMARY_FAILED)
                repository.updateError(recordingId, e.message ?: "Summary generation error")
                Result.failure()
            }
        }
    }
}
