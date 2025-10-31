package com.twinmind.voicerecorder.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

data class SummaryResponse(
    val title: String,
    val summary: String,
    val actionItems: List<String>,
    val keyPoints: List<String>
)

/**
 * Mock summary generation service that simulates LLM API with streaming
 * In production, replace with actual OpenAI/Gemini API implementation
 */
@Singleton
class SummaryService @Inject constructor() {

    /**
     * Generate summary from transcript with streaming
     * @param transcript The full transcript text
     * @return Flow emitting partial summary updates
     */
    fun generateSummary(transcript: String): Flow<SummaryResponse> = flow {
        try {
            // Simulate streaming response
            delay(1000)

            // Generate title first
            val title = generateTitle(transcript)
            emit(SummaryResponse(title, "", emptyList(), emptyList()))
            delay(500)

            // Generate summary
            val summary = generateSummaryText(transcript)
            emit(SummaryResponse(title, summary, emptyList(), emptyList()))
            delay(500)

            // Generate action items
            val actionItems = generateActionItems(transcript)
            emit(SummaryResponse(title, summary, actionItems, emptyList()))
            delay(500)

            // Generate key points
            val keyPoints = generateKeyPoints(transcript)
            emit(SummaryResponse(title, summary, actionItems, keyPoints))

        } catch (e: Exception) {
            throw e
        }
    }

    private fun generateTitle(transcript: String): String {
        val keywords = listOf(
            "Project", "Team", "Meeting", "Discussion", "Review",
            "Planning", "Quarterly", "Product", "Strategy", "Update"
        )
        val keyword = keywords.random()
        return "$keyword Discussion - ${System.currentTimeMillis() / 86400000 % 100} Days"
    }

    private fun generateSummaryText(transcript: String): String {
        return buildString {
            append("This meeting covered several important topics. ")
            append("The team discussed current project status, upcoming deliverables, and strategic initiatives. ")
            append("Key stakeholders provided updates on their respective areas. ")
            append("The discussion focused on optimizing workflows and improving team collaboration. ")
            append("Several decisions were made regarding resource allocation and timeline adjustments. ")
            append("Overall, the meeting was productive and resulted in clear next steps for the team.")
        }
    }

    private fun generateActionItems(transcript: String): List<String> {
        return listOf(
            "Schedule follow-up meeting for next week",
            "Review and update project documentation",
            "Implement feedback from stakeholders",
            "Prepare quarterly performance report",
            "Coordinate with cross-functional teams on deliverables"
        )
    }

    private fun generateKeyPoints(transcript: String): List<String> {
        return listOf(
            "Project is on track to meet Q4 deadlines",
            "User engagement metrics have improved significantly",
            "Technical infrastructure scaling is a priority",
            "Customer feedback has been overwhelmingly positive",
            "Team velocity has increased by 25% this quarter"
        )
    }
}
