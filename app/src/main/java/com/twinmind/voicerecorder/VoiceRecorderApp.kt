package com.twinmind.voicerecorder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import androidx.hilt.work.HiltWorkerFactory

@HiltAndroidApp
class VoiceRecorderApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val recordingChannel = NotificationChannel(
                RECORDING_CHANNEL_ID,
                "Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows recording status"
                setShowBadge(false)
            }

            val transcriptionChannel = NotificationChannel(
                TRANSCRIPTION_CHANNEL_ID,
                "Transcription Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows transcription progress"
                setShowBadge(false)
            }

            val summaryChannel = NotificationChannel(
                SUMMARY_CHANNEL_ID,
                "Summary Generation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows summary generation progress"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(recordingChannel)
            notificationManager.createNotificationChannel(transcriptionChannel)
            notificationManager.createNotificationChannel(summaryChannel)
        }
    }

    companion object {
        const val RECORDING_CHANNEL_ID = "recording_channel"
        const val TRANSCRIPTION_CHANNEL_ID = "transcription_channel"
        const val SUMMARY_CHANNEL_ID = "summary_channel"
    }
}
