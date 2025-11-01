package com.twinmind.voicerecorder.data.remote

import android.content.Context
import com.twinmind.voicerecorder.data.remote.api.*
import com.twinmind.voicerecorder.util.ApiConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject

/**
 * Summary service that supports multiple providers:
 * - OpenAI GPT (real AI summary generation)
 * - Google Gemini (AI summary generation)
 * - Mock (for testing without API keys)
 */
class SummaryService @Inject constructor(
    private val context: Context
) {
    data class SummaryResponse(
        val title: String,
        val summary: String,
        val actionItems: List<String>,
        val keyPoints: List<String>
    )

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

    suspend fun generateSummary(transcript: String): Flow<String> = flow {
        when (ApiConfig.CURRENT_PROVIDER) {
            ApiConfig.Provider.OPENAI -> {
                generateSummaryWithOpenAI(transcript).collect { emit(it) }
            }
            ApiConfig.Provider.GEMINI -> {
                generateSummaryWithGemini(transcript).collect { emit(it) }
            }
            ApiConfig.Provider.MOCK -> {
                generateSummaryWithMock(transcript).collect { emit(it) }
            }
        }
    }

    private suspend fun generateSummaryWithOpenAI(transcript: String): Flow<String> = flow {
        if (!ApiConfig.isConfigured()) {
            emit("Error: OpenAI API key not configured\n")
            return@flow
        }

        try {
            val request = ChatCompletionRequest(
                model = ApiConfig.GPT_MODEL,
                messages = listOf(
                    ChatMessage(
                        role = "system",
                        content = """You are an AI assistant that creates structured meeting summaries.
                            |Given a transcript, create a summary in the following markdown format:
                            |
                            |## Title
                            |[Create a concise title for the recording]
                            |
                            |## Summary
                            |[Write a brief 2-3 sentence summary of the main points]
                            |
                            |## Action Items
                            |[List any tasks or action items mentioned, each on a new line starting with '-']
                            |
                            |## Key Points
                            |[List the main points discussed, each on a new line starting with '-']
                        """.trimMargin()
                    ),
                    ChatMessage(
                        role = "user",
                        content = "Please summarize this transcript:\n\n$transcript"
                    )
                ),
                stream = false
            )

            val response = openAIApi.generateCompletion(
                authorization = "Bearer ${ApiConfig.OPENAI_API_KEY}",
                request = request
            )

            if (response.isSuccessful) {
                val summary = response.body()?.choices?.firstOrNull()?.message?.content
                if (!summary.isNullOrEmpty()) {
                    // Simulate streaming by emitting chunks
                    val words = summary.split(" ")
                    for (i in words.indices step 5) {
                        val chunk = words.slice(i until minOf(i + 5, words.size)).joinToString(" ") + " "
                        emit(chunk)
                        delay(100) // Simulate streaming delay
                    }
                } else {
                    emit("Error: Empty response from OpenAI\n")
                }
            } else {
                emit("Error: OpenAI API ${response.code()} - ${response.message()}\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emit("Error: ${e.message}\n")
        }
    }

    private suspend fun generateSummaryWithGemini(transcript: String): Flow<String> = flow {
        if (!ApiConfig.isConfigured()) {
            emit("Error: Gemini API key not configured\n")
            return@flow
        }

        try {
            val prompt = """Create a structured summary of this transcript in markdown format with these sections:
                |
                |## Title
                |[Create a concise title]
                |
                |## Summary
                |[Write a brief 2-3 sentence summary]
                |
                |## Action Items
                |[List tasks/action items with '-' prefix]
                |
                |## Key Points
                |[List main points with '-' prefix]
                |
                |Transcript:
                |$transcript
            """.trimMargin()

            val request = GeminiRequest(
                contents = listOf(
                    Content(
                        parts = listOf(Part(text = prompt))
                    )
                )
            )

            val response = geminiApi.generateContent(
                model = ApiConfig.GEMINI_MODEL,
                apiKey = ApiConfig.GEMINI_API_KEY,
                request = request
            )

            if (response.isSuccessful) {
                val summary = response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!summary.isNullOrEmpty()) {
                    // Simulate streaming by emitting chunks
                    val words = summary.split(" ")
                    for (i in words.indices step 5) {
                        val chunk = words.slice(i until minOf(i + 5, words.size)).joinToString(" ") + " "
                        emit(chunk)
                        delay(100)
                    }
                } else {
                    emit("Error: Empty response from Gemini\n")
                }
            } else {
                emit("Error: Gemini API ${response.code()}\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emit("Error: ${e.message}\n")
        }
    }

    private suspend fun generateSummaryWithMock(transcript: String): Flow<String> = flow {
        // Analyze the transcript to create a smarter mock summary
        val wordCount = transcript.split("\\s+".toRegex()).size
        val hasActionWords = transcript.contains(Regex("(?i)(need to|should|must|will|plan to|going to)"))
        val hasTimeWords = transcript.contains(Regex("(?i)(tomorrow|today|next week|deadline|due|schedule)"))

        // Generate contextual title
        val title = when {
            transcript.contains(Regex("(?i)meeting")) -> "Meeting Summary"
            transcript.contains(Regex("(?i)project")) -> "Project Discussion"
            transcript.contains(Regex("(?i)demo|test|application")) -> "Application Demo"
            else -> "Voice Recording Summary"
        }

        val summaryParts = listOf(
            "# Voice Recording Summary\n\n",
            "## Title\n",
            "$title\n\n",
            "## Summary\n",
            "This recording contains approximately $wordCount words. ",
            "The speaker discussed various topics including ",
            if (transcript.length > 100) transcript.substring(0, 100).split(" ").takeLast(10).joinToString(" ") + "... "
            else transcript.split(" ").take(10).joinToString(" ") + ". ",
            "The main focus appears to be on ${if (hasActionWords) "action planning and tasks" else "information sharing"}.\n\n",
            "## Action Items\n",
            if (hasActionWords) {
                "- Follow up on discussed points\n- Complete mentioned tasks\n- Schedule next meeting\n"
            } else {
                "- Review the recording content\n- Share with relevant stakeholders\n"
            },
            "\n## Key Points\n",
            "- Recording duration: ${wordCount / 150} minutes (estimated)\n",
            if (hasTimeWords) "- Time-sensitive items mentioned\n" else "",
            "- Total word count: ~$wordCount words\n",
            if (transcript.contains("?")) "- Questions raised during the recording\n" else ""
        )

        for (part in summaryParts) {
            delay(150) // Simulate streaming delay
            emit(part)
        }
    }

    suspend fun parseSummaryResponse(fullText: String): SummaryResponse {
        // Parse markdown-formatted summary
        val lines = fullText.split("\n")

        var title = "Voice Recording"
        val summaryBuilder = StringBuilder()
        val actionItems = mutableListOf<String>()
        val keyPoints = mutableListOf<String>()

        var currentSection = ""

        for (line in lines) {
            when {
                line.startsWith("## Title") -> currentSection = "title"
                line.startsWith("## Summary") -> currentSection = "summary"
                line.startsWith("## Action Items") -> currentSection = "actions"
                line.startsWith("## Key Points") -> currentSection = "keypoints"
                line.startsWith("- ") -> {
                    when (currentSection) {
                        "actions" -> actionItems.add(line.substring(2))
                        "keypoints" -> keyPoints.add(line.substring(2))
                    }
                }
                line.isNotBlank() && !line.startsWith("#") -> {
                    when (currentSection) {
                        "title" -> title = line
                        "summary" -> summaryBuilder.append(line).append(" ")
                    }
                }
            }
        }

        return SummaryResponse(
            title = title,
            summary = summaryBuilder.toString().trim(),
            actionItems = actionItems,
            keyPoints = keyPoints
        )
    }
}
