# API Integration Guide

This voice recording app supports **real API integration** for transcription and summary generation.

## 🚀 Supported APIs

### Option 1: OpenAI (Recommended)
- **Transcription**: Whisper API (best accuracy)
- **Summary**: GPT-4 Turbo or GPT-3.5 Turbo
- **Cost**: ~$0.006/minute for Whisper, ~$0.01-$0.03 per summary

### Option 2: Google Gemini
- **Summary**: Gemini 1.5 Flash or Pro
- **Cost**: Free tier available
- **Note**: Gemini doesn't have direct audio transcription, so use OpenAI Whisper for transcription

### Option 3: Mock (Default)
- **No API key needed**
- **Generates realistic mock data** based on transcript analysis
- **Perfect for demo/testing**

---

## 📝 Setup Instructions

### Step 1: Get API Keys

#### For OpenAI:
1. Go to https://platform.openai.com/api-keys
2. Sign up or log in
3. Click "Create new secret key"
4. Copy your API key (starts with `sk-...`)

#### For Google Gemini:
1. Go to https://makersuite.google.com/app/apikey
2. Sign in with Google account
3. Click "Create API key"
4. Copy your API key

---

### Step 2: Configure the App

Open `/app/src/main/java/com/twinmind/voicerecorder/util/ApiConfig.kt`

#### Option A: Use OpenAI
```kotlin
object ApiConfig {
    // Set provider to OPENAI
    var CURRENT_PROVIDER = Provider.OPENAI

    // Add your OpenAI API key
    const val OPENAI_API_KEY = "sk-YOUR-ACTUAL-API-KEY-HERE"

    // Choose model (gpt-3.5-turbo is cheaper)
    const val GPT_MODEL = "gpt-3.5-turbo"  // or "gpt-4-turbo-preview"
}
```

#### Option B: Use Gemini for Summary (OpenAI for Transcription)
```kotlin
object ApiConfig {
    var CURRENT_PROVIDER = Provider.OPENAI  // For transcription

    const val OPENAI_API_KEY = "sk-YOUR-OPENAI-KEY"  // For Whisper
    const val GEMINI_API_KEY = "YOUR-GEMINI-KEY"      // For summary

    // You can manually switch to Gemini in SummaryService if needed
}
```

#### Option C: Stay with Mock (No Setup)
```kotlin
object ApiConfig {
    var CURRENT_PROVIDER = Provider.MOCK  // Default, no API keys needed
}
```

---

### Step 3: Build and Run

```bash
./gradlew clean assembleDebug
```

The app will automatically:
- ✅ Record audio in 30-second chunks
- ✅ Convert PCM to WAV format
- ✅ Upload to Whisper API for transcription
- ✅ Send transcript to GPT/Gemini for summary
- ✅ Stream summary to UI in real-time

---

## 💰 Cost Estimates

### OpenAI Pricing (as of 2024)
- **Whisper API**: $0.006 per minute of audio
- **GPT-3.5 Turbo**: ~$0.001 per summary
- **GPT-4 Turbo**: ~$0.03 per summary

### Example Costs:
- 5-minute recording transcription: ~$0.03
- Summary generation (GPT-3.5): ~$0.001
- **Total per recording: ~$0.03**

For 100 recordings/month: **~$3**

---

## 🔧 How It Works

### Transcription Flow:
```
1. Audio chunks (PCM) → 2. Convert to WAV → 3. Upload to Whisper API
→ 4. Receive transcript → 5. Save to database
```

### Summary Flow:
```
1. Complete transcript → 2. Send to GPT/Gemini with structured prompt
→ 3. Stream response → 4. Parse into Title/Summary/Actions/Points
→ 5. Update UI
```

---

## 🐛 Troubleshooting

### "API key not configured" error
- Make sure you replaced `"sk-your-..."` with your actual API key
- Check that `CURRENT_PROVIDER` is set correctly
- Verify `isConfigured()` returns true

### "API Error: 401"
- Your API key is invalid or expired
- Regenerate key from provider dashboard

### "API Error: 429"
- You've hit rate limits
- Wait a few minutes or upgrade plan

### Transcription not working
- Ensure audio files are being created (check `/data/data/.../files/recordings/`)
- Verify PCM to WAV conversion is working
- Check network connectivity

---

## 🎯 Testing

### Test with Mock (Recommended First)
1. Keep `CURRENT_PROVIDER = Provider.MOCK`
2. Record a test voice note
3. Check that mock transcript appears
4. Verify summary is generated based on transcript content

### Test with Real API
1. Set API keys in `ApiConfig.kt`
2. Change to `Provider.OPENAI` or `Provider.GEMINI`
3. Record a short test (15-30 seconds to minimize cost)
4. Verify real transcription appears
5. Check summary is based on actual transcript

---

## 📊 Smart Mock Mode

Even without API keys, the Mock mode now:
- ✅ Analyzes transcript content
- ✅ Detects keywords (meeting, project, action items)
- ✅ Generates contextual summaries
- ✅ Provides realistic word counts
- ✅ Varies responses based on content

Perfect for demonstration purposes!

---

## 🔐 Security Note

**⚠️ Never commit API keys to Git!**

Before committing:
```bash
# Make sure your API keys are still "sk-your-..." placeholders
git diff app/src/main/java/com/twinmind/voicerecorder/util/ApiConfig.kt
```

For production, use:
- Environment variables
- Secrets management (Android Keystore)
- Build config fields
- Remote config (Firebase)

---

## 📞 Support

- OpenAI Docs: https://platform.openai.com/docs
- Gemini Docs: https://ai.google.dev/docs
- Whisper API: https://platform.openai.com/docs/guides/speech-to-text

---

**Happy coding!** 🎉
