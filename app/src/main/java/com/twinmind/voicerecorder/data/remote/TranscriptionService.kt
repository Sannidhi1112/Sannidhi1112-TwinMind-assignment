package com.twinmind.voicerecorder.data.remote

import kotlinx.coroutines.delay
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Mock transcription service that simulates OpenAI Whisper or Google Gemini API
 * In production, replace with actual API implementation
 */
@Singleton
class TranscriptionService @Inject constructor() {

    /**
     * Transcribe an audio file to text
     * @param audioFile The audio file to transcribe
     * @return The transcribed text
     */
    suspend fun transcribeAudio(audioFile: File): Result<String> {
        return try {
            // Simulate API call delay (2-4 seconds)
            delay(Random.nextLong(2000, 4000))

            // Simulate occasional failures (10% failure rate)
            if (Random.nextFloat() < 0.1f) {
                return Result.failure(Exception("Transcription API error"))
            }

            // Generate mock transcription based on file name/index
            val transcription = generateMockTranscription(audioFile.name)
            Result.success(transcription)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateMockTranscription(fileName: String): String {
        // Extract chunk index from filename if present
        val chunkIndex = fileName.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0

        // Mock sentences pool
        val sentences = listOf(
            "Let's discuss the quarterly results for this project.",
            "I think we should focus on improving user engagement metrics.",
            "The team has made significant progress on the new features.",
            "We need to address the technical debt in the codebase.",
            "Customer feedback has been overwhelmingly positive.",
            "Let's schedule a follow-up meeting next week.",
            "The deployment went smoothly without any major issues.",
            "We should prioritize bug fixes for the next sprint.",
            "Marketing campaign results exceeded our expectations.",
            "The new design mockups look great and align with our brand.",
            "We need to allocate more resources to this initiative.",
            "Testing coverage has improved significantly this quarter.",
            "Let's brainstorm ideas for the upcoming product launch.",
            "The integration with the third-party API is complete.",
            "We should consider scaling our infrastructure soon.",
            "User retention rates have increased by twenty percent.",
            "The mobile app performance has been optimized.",
            "Let's review the action items from our last meeting.",
            "The security audit identified a few minor vulnerabilities.",
            "Overall, the project is on track to meet the deadline."
        )

        // Generate 3-5 sentences per chunk
        val sentenceCount = Random.nextInt(3, 6)
        val startIndex = (chunkIndex * 3) % sentences.size
        val selectedSentences = (0 until sentenceCount).map { i ->
            sentences[(startIndex + i) % sentences.size]
        }

        return selectedSentences.joinToString(" ")
    }
}
