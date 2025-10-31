# Voice Recording App - TwinMind Assignment

A robust Android voice recording application with automatic transcription and AI-powered summary generation. Built with modern Android development practices and comprehensive edge case handling.

## Features

### 🎙️ Recording
- **Foreground Service Recording**: Reliable background recording with persistent notification
- **30-Second Chunking**: Audio split into 30-second chunks with 2-second overlap for continuity
- **Real-time Status Updates**: Live recording timer and status on lock screen
- **Smart Pause/Resume**: Automatic handling of interruptions

### 🛡️ Edge Case Handling

#### Phone Call Management
- Automatically pauses recording when incoming/outgoing call starts
- Shows status: "Paused - Phone call"
- Resumes recording when call ends

#### Audio Focus Management
- Pauses when other apps take audio focus
- Shows persistent notification: "Paused - Audio focus lost"
- Notification includes Resume/Stop actions
- Auto-resumes when audio focus returns

#### Device Compatibility
- **Bluetooth Headset**: Seamlessly continues recording when BT headset connects/disconnects
- **Wired Headset**: Handles wired headset plug/unplug events
- Shows notification when audio source changes

#### Storage & Audio Quality
- **Low Storage Detection**: Checks available storage before recording
- Stops gracefully if storage runs out (< 100 MB)
- Shows error: "Recording stopped - Low storage"
- **Silent Audio Detection**: Detects no audio input after 10 seconds
- Warning: "No audio detected - Check microphone"

#### Process Recovery
- **Process Death Recovery**: Persists recording state in Room database
- WorkManager ensures transcription continues even if app is killed
- Automatic recovery and resumption of pending tasks

### 📝 Transcription
- Automatic transcription of audio chunks as they're recorded
- Mock transcription service (can be replaced with OpenAI Whisper or Google Gemini)
- Retry logic with exponential backoff (max 3 retries)
- Chunks transcribed in correct order
- Room database as single source of truth

### 📊 Summary Generation
- AI-powered summary generation from complete transcript
- **Streaming UI Updates**: Summary appears progressively as it's generated
- 4 structured sections:
  - **Title**: Auto-generated meeting title
  - **Summary**: Comprehensive meeting summary
  - **Action Items**: Bulleted list of action items
  - **Key Points**: Important discussion points
- **Background Processing**: Summary generation continues even if app is killed
- Error handling with retry functionality

## Tech Stack

### Architecture & Patterns
- **MVVM Architecture**: Clean separation of concerns
- **Repository Pattern**: Single source of truth for data
- **Dependency Injection**: Hilt for DI
- **Reactive Programming**: Kotlin Coroutines & Flow

### Android Technologies
- **Jetpack Compose**: Modern declarative UI (100%)
- **Room Database**: Local data persistence
- **WorkManager**: Background task scheduling
- **Foreground Service**: Reliable audio recording
- **Navigation Component**: Type-safe navigation

### Libraries
- **Kotlin**: 1.9.20
- **Jetpack Compose**: BOM 2023.10.01
- **Hilt**: 2.48
- **Room**: 2.6.1
- **Retrofit**: 2.9.0 (for future API integration)
- **Coroutines**: 1.7.3
- **WorkManager**: 2.9.0
- **Accompanist Permissions**: 0.32.0

## Project Structure

```
app/src/main/java/com/twinmind/voicerecorder/
├── data/
│   ├── local/
│   │   ├── entity/          # Room entities (Recording, AudioChunk)
│   │   ├── dao/             # Data Access Objects
│   │   ├── VoiceRecorderDatabase.kt
│   │   └── Converters.kt
│   ├── remote/
│   │   ├── TranscriptionService.kt
│   │   └── SummaryService.kt
│   ├── repository/
│   │   └── RecordingRepository.kt
│   ├── service/
│   │   └── RecordingService.kt  # Foreground recording service
│   └── worker/
│       ├── TranscriptionWorker.kt
│       └── SummaryGenerationWorker.kt
├── di/                      # Hilt modules
│   ├── AppModule.kt
│   └── DatabaseModule.kt
├── presentation/
│   ├── dashboard/           # Meeting list screen
│   ├── recording/           # Recording screen
│   ├── summary/             # Summary screen
│   ├── navigation/
│   └── theme/
└── VoiceRecorderApp.kt      # Application class
```

## Setup Instructions

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 34
- Minimum SDK: API 24 (Android 7.0)
- Target SDK: API 34
- JDK 17

### Building the Project

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd Sannidhi1112-TwinMind-assignment
   ```

2. **Configure Android SDK**
   - Copy `local.properties.template` to `local.properties`
   - Update `sdk.dir` with your Android SDK path
   ```
   sdk.dir=/path/to/android/sdk
   ```

3. **Open in Android Studio**
   - File → Open → Select project directory
   - Wait for Gradle sync to complete

4. **Build Debug APK**
   ```bash
   ./gradlew assembleDebug
   ```
   APK location: `app/build/outputs/apk/debug/app-debug.apk`

5. **Install on Device**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### Permissions Required
The app requests the following permissions:
- `RECORD_AUDIO`: Audio recording
- `POST_NOTIFICATIONS`: Recording status notifications
- `READ_PHONE_STATE`: Call detection for auto-pause
- `FOREGROUND_SERVICE_MICROPHONE`: Foreground service
- `BLUETOOTH_CONNECT`: Bluetooth headset detection

## How to Use

### Recording a Meeting
1. Launch the app
2. Tap the **+** button on the dashboard
3. Grant required permissions when prompted
4. Tap the **Microphone** button to start recording
5. Recording status and timer will be displayed
6. Recording continues in background (check notification)
7. Tap **Stop** button to end recording

### Edge Case Testing
- **Phone Call**: Call your device while recording to test auto-pause
- **Bluetooth**: Connect/disconnect Bluetooth headset during recording
- **Audio Focus**: Play music in another app to test audio focus loss
- **Low Storage**: Test on device with low available storage
- **Process Death**: Force stop app during recording (recovery via WorkManager)

### Viewing Summary
1. Wait for transcription and summary generation to complete
2. Meeting appears as "Complete" on dashboard
3. Tap the meeting card to view summary
4. Summary sections load progressively
5. Retry button available if generation fails

## Implementation Highlights

### Audio Recording
- **AudioRecord API**: Low-level audio capture
- **WAV Format**: Standard PCM 16-bit mono at 44.1kHz
- **Chunk Management**: 30s chunks with 2s overlap for speech continuity
- **File Storage**: Local app storage (`files/recordings/`)

### Database Design
```kotlin
Recording (id, title, startTime, endTime, duration, status, totalChunks,
          transcript, summaryTitle, summaryContent, actionItems, keyPoints)

AudioChunk (id, recordingId, chunkIndex, filePath, duration,
           transcriptionStatus, transcriptionText, transcriptionRetries)
```

### Service Lifecycle
```
Start Recording → Create Recording in DB → Start Foreground Service
→ Record Audio Chunks → Save to Storage → Update DB
→ Enqueue Transcription Worker → Stop Recording → Finalize in DB
→ Transcription Complete → Enqueue Summary Worker → Summary Complete
```

### Worker Chain
```
RecordingService
    ↓
TranscriptionWorker (per recording)
    ↓ (on success)
SummaryGenerationWorker
    ↓
Update UI via Room Flow
```

## API Integration

### Mock vs Real APIs

The app currently uses **mock implementations** for demonstration:

**To integrate real APIs:**

1. **Transcription (OpenAI Whisper)**
   - Update `TranscriptionService.kt`
   - Add API key to `local.properties`
   - Implement multipart file upload
   ```kotlin
   @Multipart
   @POST("v1/audio/transcriptions")
   suspend fun transcribe(
       @Part file: MultipartBody.Part,
       @Part("model") model: RequestBody
   ): TranscriptionResponse
   ```

2. **Summary (OpenAI GPT or Gemini)**
   - Update `SummaryService.kt`
   - Implement streaming API call
   - Parse JSON stream for progressive updates

## Testing

### Manual Testing Checklist
- [ ] Start/stop recording
- [ ] Recording continues in background
- [ ] Notification shows correct status
- [ ] Phone call auto-pause/resume
- [ ] Audio focus loss handling
- [ ] Bluetooth headset connect/disconnect
- [ ] Wired headset plug/unplug
- [ ] Low storage detection
- [ ] Silent audio warning
- [ ] App force-stop recovery
- [ ] Transcription completes successfully
- [ ] Summary generates with all 4 sections
- [ ] Summary streams to UI progressively
- [ ] Retry on failure works

## Known Limitations

1. **Mock APIs**: Transcription and summary use mock data
2. **Android 16 Live Updates**: Requires Android 16+ for lock screen live updates
3. **Language Support**: English only (mock transcription)
4. **File Size**: Large recordings may impact storage

## Future Enhancements

- [ ] Real API integration (Whisper, Gemini)
- [ ] Multi-language support
- [ ] Cloud sync
- [ ] Meeting sharing
- [ ] Speaker diarization
- [ ] Custom summary templates
- [ ] Export to PDF/TXT
- [ ] Search functionality
- [ ] Recording analytics

## Architecture Decisions

### Why MVVM?
- Clear separation of concerns
- Lifecycle-aware components
- Testability

### Why Room?
- Type-safe database access
- Migration support
- Compile-time verification
- Reactive queries with Flow

### Why WorkManager?
- Guaranteed execution
- Constraints support (network, battery)
- Survives app process death
- Built-in retry logic

### Why Jetpack Compose?
- Modern declarative UI
- Less boilerplate
- Better performance
- Reactive state management

## License

This project is created for the TwinMind Android Developer assignment.

## Developer

Sannidhi - Android Developer

---

**Note**: This is a demonstration project with mock APIs. For production use, integrate real transcription and summary generation services.
