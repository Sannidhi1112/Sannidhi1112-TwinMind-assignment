# 🔐 Secure API Key Setup Guide

This guide shows you how to configure API keys **securely** without committing them to Git.

---

## ✅ Quick Setup (3 Steps)

### Step 1: Get Your API Key

**For OpenAI (Recommended):**
1. Go to https://platform.openai.com/api-keys
2. Sign up or log in
3. Click **"Create new secret key"**
4. **Important**: Copy the key immediately (starts with `sk-proj-...` or `sk-...`)
5. Add $5-$10 credits at https://platform.openai.com/settings/organization/billing

### Step 2: Create `local.properties` File

**On your MacBook:**

1. Open Terminal
2. Navigate to project:
   ```bash
   cd ~/Sannidhi1112-TwinMind-assignment
   ```

3. Create `local.properties` file:
   ```bash
   cp local.properties.example local.properties
   ```

4. Edit the file:
   ```bash
   nano local.properties
   ```

   Or open in your editor and add:
   ```properties
   OPENAI_API_KEY=sk-YOUR-ACTUAL-KEY-HERE
   GEMINI_API_KEY=
   ```

5. Save and close

### Step 3: Enable OpenAI Provider

1. Open in Android Studio:
   ```
   app/src/main/java/com/twinmind/voicerecorder/util/ApiConfig.kt
   ```

2. Change this line:
   ```kotlin
   var CURRENT_PROVIDER = Provider.OPENAI  // Change from MOCK to OPENAI
   ```

3. **Save the file**

---

## 🚀 Build and Test

```bash
# Clean and rebuild
./gradlew clean assembleDebug

# Install on device/emulator
adb install app/build/outputs/apk/debug/app-debug.apk

# Or just run from Android Studio
```

**Test it:**
1. Open app
2. Start recording
3. Say: *"Testing the voice recording app with OpenAI Whisper transcription"*
4. Stop recording
5. Wait ~5 seconds
6. See your **actual words transcribed**! 🎉

---

## 🔒 Security Checklist

✅ **`local.properties` is in `.gitignore`** - Your key won't be committed
✅ **BuildConfig generates at build time** - Key never in source code
✅ **No hardcoded keys** - All keys from local file

**Before every commit:**
```bash
# Make sure local.properties is not tracked
git status

# You should see it listed under "Untracked files" (if new)
# or not see it at all (because it's ignored)
```

---

## 🛠️ File Structure

```
YourProject/
├── local.properties           ← YOUR API KEYS (git-ignored) ✅
├── local.properties.example   ← Template (safe to commit)
├── .gitignore                 ← Contains "local.properties"
├── app/
│   ├── build.gradle.kts       ← Reads from local.properties
│   └── src/main/java/.../
│       └── util/
│           └── ApiConfig.kt   ← Uses BuildConfig.OPENAI_API_KEY
```

---

## 💰 Cost Tracking

**OpenAI Usage:**
- Monitor at: https://platform.openai.com/usage
- Set spending limits: https://platform.openai.com/settings/organization/limits

**Typical Costs:**
- 15-second test: ~$0.002
- 1-minute recording: ~$0.01
- 5-minute recording: ~$0.03

---

## 🐛 Troubleshooting

### "OpenAI API Key Missing" message

**Check:**
```bash
# 1. Verify local.properties exists
ls -la local.properties

# 2. Check the content
cat local.properties

# 3. Make sure the key format is correct
# Should be: OPENAI_API_KEY=sk-proj-...
# NOT: OPENAI_API_KEY="sk-proj-..."  (no quotes!)
```

### API key not working in app

**Solution:**
1. Clean and rebuild:
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug
   ```

2. Check `ApiConfig.kt`:
   ```kotlin
   var CURRENT_PROVIDER = Provider.OPENAI  // Must be OPENAI, not MOCK
   ```

3. Verify in logs:
   ```bash
   adb logcat | grep "API"
   ```

### "401 Unauthorized" error

Your API key is invalid or expired:
1. Go to https://platform.openai.com/api-keys
2. Revoke old key
3. Create new key
4. Update `local.properties`
5. Rebuild app

---

## 📱 Different Configurations

### Development (Mock - No Cost)
```kotlin
// ApiConfig.kt
var CURRENT_PROVIDER = Provider.MOCK
```

### Testing (OpenAI - Small Cost)
```kotlin
// ApiConfig.kt
var CURRENT_PROVIDER = Provider.OPENAI
const val GPT_MODEL = "gpt-3.5-turbo"  // Cheaper than GPT-4
```

### Production (OpenAI - Best Quality)
```kotlin
// ApiConfig.kt
var CURRENT_PROVIDER = Provider.OPENAI
const val GPT_MODEL = "gpt-4-turbo-preview"  // Best quality
```

---

## ✅ Verification Steps

After setup, verify everything works:

1. **Check API key loads:**
   ```bash
   ./gradlew assembleDebug
   # No errors = ✅
   ```

2. **Check provider is set:**
   - Open app
   - (You can add a debug message to show provider status)

3. **Test transcription:**
   - Record 10 seconds of speech
   - Stop and wait
   - Check if you see your actual words

4. **Check costs:**
   - Go to https://platform.openai.com/usage
   - Verify charges match your usage

---

## 🎯 Summary

| File | Purpose | Commit to Git? |
|------|---------|----------------|
| `local.properties` | **Your API keys** | ❌ NO (git-ignored) |
| `local.properties.example` | Template | ✅ YES |
| `ApiConfig.kt` | Config code | ✅ YES (no keys) |
| `build.gradle.kts` | Reads keys | ✅ YES |

**The setup is SECURE:**
- ✅ API keys stay on your machine only
- ✅ Never committed to Git
- ✅ Safe to share code with team
- ✅ Each developer uses their own keys

---

**Need help?** Check:
- OpenAI Docs: https://platform.openai.com/docs
- Troubleshooting: See above
- Cost monitoring: https://platform.openai.com/usage

**Happy coding!** 🚀
