package com.twinmind.voicerecorder.data.remote

import android.content.Context
import kotlinx.coroutines.delay
import java.io.File
import javax.inject.Inject

/**
 * Mock transcription service that simulates API calls
 * In production, this would call OpenAI Whisper or Google Gemini
 */
class TranscriptionService @Inject constructor(
    private val context: Context
) {
    suspend fun transcribeAudio(audioFile: File): Result<String> {
        return try {
            // Simulate API delay
            delay(1000)

            // Mock transcription based on file duration/size
            val mockText = generateMockTranscript(audioFile)

            Result.success(mockText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateMockTranscript(file: File): String {
        val sizeInKb = file.length() / 1024
        val estimatedDuration = sizeInKb / 8 // Rough estimate

        return buildString {
            append("This is a mock transcription for audio chunk. ")
            append("The file size is approximately $sizeInKb KB. ")
            append("Estimated duration is around $estimatedDuration seconds. ")
            append("In a production environment, this would contain the actual ")
            append("transcribed text from the audio using services like ")
            append("OpenAI Whisper or Google Gemini. ")
        }
    }
}
