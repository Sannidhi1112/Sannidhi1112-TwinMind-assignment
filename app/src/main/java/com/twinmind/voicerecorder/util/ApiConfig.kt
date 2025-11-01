package com.twinmind.voicerecorder.util

import com.twinmind.voicerecorder.BuildConfig

/**
 * API Configuration
 *
 * SECURE SETUP:
 * API keys are loaded from local.properties (which is git-ignored)
 * See local.properties.example for setup instructions
 *
 * Get API keys from:
 * - OpenAI: https://platform.openai.com/api-keys
 * - Google Gemini: https://makersuite.google.com/app/apikey
 */
object ApiConfig {

    // API Provider Selection
    enum class Provider {
        OPENAI,      // Uses Whisper for transcription, GPT for summary
        GEMINI,      // Uses Gemini for summary (still needs Whisper for transcription)
        MOCK         // Uses mock services (for testing without API keys)
    }

    // Choose your provider here
    var CURRENT_PROVIDER = Provider.MOCK // Change to OPENAI or GEMINI when configured

    // API Keys (loaded from local.properties)
    val OPENAI_API_KEY: String get() = BuildConfig.OPENAI_API_KEY
    val GEMINI_API_KEY: String get() = BuildConfig.GEMINI_API_KEY

    // OpenAI Configuration
    const val OPENAI_BASE_URL = "https://api.openai.com/"
    const val WHISPER_MODEL = "whisper-1"
    const val GPT_MODEL = "gpt-4-turbo-preview" // or "gpt-3.5-turbo" for cheaper option

    // Google Gemini Configuration
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"
    const val GEMINI_MODEL = "gemini-1.5-flash" // or "gemini-1.5-pro"

    // API Settings
    const val TRANSCRIPTION_TIMEOUT_SECONDS = 60L
    const val SUMMARY_TIMEOUT_SECONDS = 30L

    fun isConfigured(): Boolean {
        return when (CURRENT_PROVIDER) {
            Provider.OPENAI -> OPENAI_API_KEY.isNotEmpty() && OPENAI_API_KEY.startsWith("sk-")
            Provider.GEMINI -> GEMINI_API_KEY.isNotEmpty() && !GEMINI_API_KEY.contains("your-")
            Provider.MOCK -> true
        }
    }

    fun getProviderStatus(): String {
        return when (CURRENT_PROVIDER) {
            Provider.OPENAI -> if (isConfigured()) "OpenAI (Configured)" else "OpenAI (API Key Missing)"
            Provider.GEMINI -> if (isConfigured()) "Gemini (Configured)" else "Gemini (API Key Missing)"
            Provider.MOCK -> "Mock (No API Key Needed)"
        }
    }
}
