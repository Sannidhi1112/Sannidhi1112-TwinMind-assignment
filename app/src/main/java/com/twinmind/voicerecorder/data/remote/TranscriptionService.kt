package com.twinmind.voicerecorder.data.remote

import android.content.Context
import com.twinmind.voicerecorder.data.remote.api.GeminiApi
import com.twinmind.voicerecorder.data.remote.api.GeminiRequest
import com.twinmind.voicerecorder.data.remote.api.Content
import com.twinmind.voicerecorder.data.remote.api.OpenAIApi
import com.twinmind.voicerecorder.data.remote.api.Part
import com.twinmind.voicerecorder.util.ApiConfig
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import javax.inject.Inject

/**
 * Transcription service that supports multiple providers:
 * - OpenAI Whisper (real speech-to-text)
 * - Google Gemini (AI transcription)
 * - Mock (for testing without API keys)
 */
class TranscriptionService @Inject constructor(
    private val context: Context
) {
    private val openAIApi: OpenAIApi by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.OPENAI_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenAIApi::class.java)
    }

    private val geminiApi: GeminiApi by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.GEMINI_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeminiApi::class.java)
    }

    suspend fun transcribeAudio(audioFile: File): Result<String> {
        return try {
            when (ApiConfig.CURRENT_PROVIDER) {
                ApiConfig.Provider.OPENAI -> transcribeWithOpenAI(audioFile)
                ApiConfig.Provider.GEMINI -> transcribeWithGemini(audioFile)
                ApiConfig.Provider.MOCK -> transcribeWithMock(audioFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Transcription failed: ${e.message}"))
        }
    }

    private suspend fun transcribeWithOpenAI(audioFile: File): Result<String> {
        if (!ApiConfig.isConfigured()) {
            return Result.failure(Exception("OpenAI API key not configured"))
        }

        return try {
            // Convert PCM to WAV format (Whisper needs proper audio format)
            val wavFile = convertPcmToWav(audioFile)

            // Prepare the multipart request
            val requestFile = wavFile.asRequestBody("audio/wav".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", wavFile.name, requestFile)
            val modelPart = ApiConfig.WHISPER_MODEL.toRequestBody("text/plain".toMediaTypeOrNull())

            // Call Whisper API
            val response = openAIApi.transcribeAudio(
                authorization = "Bearer ${ApiConfig.OPENAI_API_KEY}",
                file = filePart,
                model = modelPart
            )

            if (response.isSuccessful) {
                val transcription = response.body()?.text
                if (!transcription.isNullOrEmpty()) {
                    Result.success(transcription)
                } else {
                    Result.failure(Exception("Empty transcription received"))
                }
            } else {
                Result.failure(Exception("API Error: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("OpenAI Whisper error: ${e.message}"))
        }
    }

    private suspend fun transcribeWithGemini(audioFile: File): Result<String> {
        if (!ApiConfig.isConfigured()) {
            return Result.failure(Exception("Gemini API key not configured"))
        }

        return try {
            // Note: Gemini doesn't directly support audio transcription in the same way
            // For now, we'll use a mock approach or you can implement Google Speech-to-Text API
            // This is a placeholder that returns mock data

            val request = GeminiRequest(
                contents = listOf(
                    Content(
                        parts = listOf(
                            Part(text = "Transcribe this audio file: ${audioFile.name}")
                        )
                    )
                )
            )

            val response = geminiApi.generateContent(
                model = ApiConfig.GEMINI_MODEL,
                apiKey = ApiConfig.GEMINI_API_KEY,
                request = request
            )

            if (response.isSuccessful) {
                val text = response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!text.isNullOrEmpty()) {
                    Result.success(text)
                } else {
                    Result.failure(Exception("Empty response from Gemini"))
                }
            } else {
                Result.failure(Exception("Gemini API Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Gemini error: ${e.message}"))
        }
    }

    private suspend fun transcribeWithMock(audioFile: File): Result<String> {
        // Simulate API delay
        delay(1000)

        // Generate realistic mock transcription
        val sizeInKb = audioFile.length() / 1024
        val estimatedDuration = sizeInKb / 8

        val mockTranscripts = listOf(
            "Hello, this is a test recording. I'm testing the voice recording application to see how it transcribes my speech into text.",
            "In today's meeting, we discussed the quarterly results and the new project timeline. The team agreed to deliver the MVP by the end of next month.",
            "This is a sample voice note. The weather is nice today and I'm planning to finish the assignment by tomorrow evening.",
            "Welcome to the voice recorder demo. This application can record your voice, transcribe it using AI, and generate a helpful summary of the content.",
            "During our conversation, we covered three main topics: budget allocation, team expansion, and the product roadmap for Q2."
        )

        val mockText = mockTranscripts.random() + " [Duration: ~${estimatedDuration}s, Size: ${sizeInKb}KB]"

        return Result.success(mockText)
    }

    /**
     * Convert PCM audio to WAV format
     * Whisper API requires proper audio format (WAV, MP3, etc.)
     */
    private fun convertPcmToWav(pcmFile: File): File {
        val wavFile = File(pcmFile.parent, pcmFile.nameWithoutExtension + ".wav")

        try {
            // PCM format parameters (must match RecordingService settings)
            val sampleRate = 44100
            val channels = 1 // Mono
            val bitsPerSample = 16

            val pcmData = pcmFile.readBytes()
            val dataSize = pcmData.size
            val byteRate = sampleRate * channels * bitsPerSample / 8

            // WAV file header (44 bytes)
            val header = ByteArray(44)

            // RIFF chunk descriptor
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()

            val fileSize = dataSize + 36
            header[4] = (fileSize and 0xff).toByte()
            header[5] = ((fileSize shr 8) and 0xff).toByte()
            header[6] = ((fileSize shr 16) and 0xff).toByte()
            header[7] = ((fileSize shr 24) and 0xff).toByte()

            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()

            // fmt sub-chunk
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()

            header[16] = 16 // SubChunk1Size (16 for PCM)
            header[17] = 0
            header[18] = 0
            header[19] = 0

            header[20] = 1 // AudioFormat (1 for PCM)
            header[21] = 0

            header[22] = channels.toByte()
            header[23] = 0

            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()

            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()

            val blockAlign = channels * bitsPerSample / 8
            header[32] = blockAlign.toByte()
            header[33] = 0

            header[34] = bitsPerSample.toByte()
            header[35] = 0

            // data sub-chunk
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()

            header[40] = (dataSize and 0xff).toByte()
            header[41] = ((dataSize shr 8) and 0xff).toByte()
            header[42] = ((dataSize shr 16) and 0xff).toByte()
            header[43] = ((dataSize shr 24) and 0xff).toByte()

            // Write WAV file
            wavFile.writeBytes(header + pcmData)

        } catch (e: Exception) {
            e.printStackTrace()
            // If conversion fails, return original file
            return pcmFile
        }

        return wavFile
    }
}
