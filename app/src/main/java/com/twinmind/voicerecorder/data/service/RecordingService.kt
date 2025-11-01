package com.twinmind.voicerecorder.data.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.StatFs
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.twinmind.voicerecorder.R
import com.twinmind.voicerecorder.VoiceRecorderApp
import com.twinmind.voicerecorder.data.local.entity.AudioChunk
import com.twinmind.voicerecorder.data.local.entity.Recording
import com.twinmind.voicerecorder.data.local.entity.RecordingStatus
import com.twinmind.voicerecorder.data.repository.RecordingRepository
import com.twinmind.voicerecorder.data.worker.TranscriptionWorker
import com.twinmind.voicerecorder.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class RecordingService : Service() {

    @Inject
    lateinit var repository: RecordingRepository

    private val binder = RecordingBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var currentRecordingId: Long? = null

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState

    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime: StateFlow<Long> = _elapsedTime

    private var chunkIndex = 0
    private var recordingStartTime = 0L
    private var totalDuration = 0L

    // Audio configuration
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    // Chunk configuration (30 seconds with 2-second overlap)
    private val chunkDurationMs = 30000L
    private val overlapDurationMs = 2000L

    // Receivers
    private var phoneStateReceiver: BroadcastReceiver? = null
    private var audioManager: AudioManager? = null
    private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null

    companion object {
        const val ACTION_START_RECORDING = "ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "ACTION_STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "ACTION_PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "ACTION_RESUME_RECORDING"
        const val NOTIFICATION_ID = 1001
        const val MIN_STORAGE_MB = 100
    }

    inner class RecordingBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setupPhoneStateListener()
        setupAudioFocusListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> startRecording()
            ACTION_STOP_RECORDING -> stopRecording()
            ACTION_PAUSE_RECORDING -> pauseRecording("Manual pause")
            ACTION_RESUME_RECORDING -> resumeRecording()
        }
        return START_STICKY
    }

    private fun startRecording() {
        serviceScope.launch {
            // Check storage
            if (!hasEnoughStorage()) {
                _recordingState.value = RecordingState.Error("Low storage - Need at least ${MIN_STORAGE_MB}MB")
                stopSelf()
                return@launch
            }

            // Check permission
            if (ActivityCompat.checkSelfPermission(
                    this@RecordingService,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                _recordingState.value = RecordingState.Error("Microphone permission denied")
                stopSelf()
                return@launch
            }

            // Create recording in database
            val recording = Recording(
                title = "Recording ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())}",
                startTime = System.currentTimeMillis(),
                status = RecordingStatus.RECORDING
            )
            currentRecordingId = repository.insertRecording(recording)
            recordingStartTime = System.currentTimeMillis()
            chunkIndex = 0

            // Start foreground service
            startForeground()

            // Request audio focus
            requestAudioFocus()

            // Start recording
            _recordingState.value = RecordingState.Recording
            startRecordingAudio()
        }
    }

    private fun startRecordingAudio() {
        recordingJob = serviceScope.launch(Dispatchers.IO) {
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize * 4
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    withContext(Dispatchers.Main) {
                        _recordingState.value = RecordingState.Error("Failed to initialize audio recorder")
                    }
                    stopRecording()
                    return@launch
                }

                audioRecord?.startRecording()

                var chunkStartTime = System.currentTimeMillis()
                var chunkFile = createChunkFile()
                var chunkOutputStream = FileOutputStream(chunkFile)
                val buffer = ByteArray(bufferSize)
                var silenceDetectionStart = 0L
                var lastSoundDetected = System.currentTimeMillis()

                // Timer for elapsed time
                val timerJob = launch {
                    while (isActive) {
                        _elapsedTime.value = System.currentTimeMillis() - recordingStartTime
                        delay(1000)
                    }
                }

                while (_recordingState.value is RecordingState.Recording && isActive) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        chunkOutputStream.write(buffer, 0, read)

                        // Silent audio detection
                        if (isSilent(buffer, read)) {
                            if (silenceDetectionStart == 0L) {
                                silenceDetectionStart = System.currentTimeMillis()
                            } else if (System.currentTimeMillis() - silenceDetectionStart > 10000) {
                                // 10 seconds of silence
                                withContext(Dispatchers.Main) {
                                    updateNotification("No audio detected - Check microphone")
                                }
                            }
                        } else {
                            silenceDetectionStart = 0L
                            lastSoundDetected = System.currentTimeMillis()
                        }

                        // Check if chunk duration reached
                        val chunkDuration = System.currentTimeMillis() - chunkStartTime
                        if (chunkDuration >= chunkDurationMs) {
                            // Save current chunk
                            chunkOutputStream.close()
                            saveChunk(chunkFile, chunkStartTime)

                            // Start new chunk with overlap
                            chunkIndex++
                            chunkStartTime = System.currentTimeMillis() - overlapDurationMs
                            chunkFile = createChunkFile()
                            chunkOutputStream = FileOutputStream(chunkFile)

                            // Check storage periodically
                            if (!hasEnoughStorage()) {
                                withContext(Dispatchers.Main) {
                                    _recordingState.value = RecordingState.Error("Recording stopped - Low storage")
                                    stopRecording()
                                }
                                break
                            }
                        }
                    }
                }

                // Save final chunk
                chunkOutputStream.close()
                if (chunkFile.length() > 0) {
                    saveChunk(chunkFile, chunkStartTime)
                }

                timerJob.cancel()

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _recordingState.value = RecordingState.Error("Recording error: ${e.message}")
                }
            } finally {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
            }
        }
    }

    private fun pauseRecording(reason: String) {
        serviceScope.launch {
            if (_recordingState.value is RecordingState.Recording) {
                recordingJob?.cancel()
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null

                currentRecordingId?.let {
                    repository.updateRecordingStatus(it, RecordingStatus.PAUSED)
                    repository.updatePauseReason(it, reason)
                }

                _recordingState.value = RecordingState.Paused(reason)
                updateNotification("Paused - $reason")
            }
        }
    }

    private fun resumeRecording() {
        serviceScope.launch {
            if (_recordingState.value is RecordingState.Paused) {
                currentRecordingId?.let {
                    repository.updateRecordingStatus(it, RecordingStatus.RECORDING)
                    repository.updatePauseReason(it, null)
                }

                _recordingState.value = RecordingState.Recording
                startRecordingAudio()
                updateNotification("Recording...")
            }
        }
    }

    private fun stopRecording() {
        serviceScope.launch {
            recordingJob?.cancel()
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            val endTime = System.currentTimeMillis()
            totalDuration = endTime - recordingStartTime

            currentRecordingId?.let { id ->
                repository.stopRecording(id, endTime, totalDuration, RecordingStatus.STOPPED)

                // Update total chunks
                val chunkCount = repository.getChunkCount(id)
                val recording = repository.getRecordingByIdSync(id)
                recording?.let {
                    repository.updateRecording(it.copy(totalChunks = chunkCount))
                }

                // Start transcription
                val workRequest = TranscriptionWorker.createWorkRequest(id)
                WorkManager.getInstance(applicationContext).enqueue(workRequest)
            }

            _recordingState.value = RecordingState.Stopped
            _elapsedTime.value = 0L
            abandonAudioFocus()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun saveChunk(file: File, startTime: Long) {
        currentRecordingId?.let { recordingId ->
            val chunk = AudioChunk(
                recordingId = recordingId,
                chunkIndex = chunkIndex,
                filePath = file.absolutePath,
                startTime = startTime,
                endTime = System.currentTimeMillis(),
                duration = System.currentTimeMillis() - startTime,
                fileSize = file.length()
            )
            repository.insertChunk(chunk)
        }
    }

    private fun createChunkFile(): File {
        val dir = File(filesDir, "recordings/${currentRecordingId}")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "chunk_${chunkIndex}_${System.currentTimeMillis()}.pcm")
    }

    private fun isSilent(buffer: ByteArray, size: Int): Boolean {
        var sum = 0L
        for (i in 0 until size step 2) {
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
            sum += Math.abs(sample)
        }
        val average = sum / (size / 2)
        return average < 500 // Threshold for silence
    }

    private fun hasEnoughStorage(): Boolean {
        val stat = StatFs(filesDir.path)
        val availableBytes = stat.availableBytes
        val availableMB = availableBytes / (1024 * 1024)
        return availableMB >= MIN_STORAGE_MB
    }

    private fun setupPhoneStateListener() {
        phoneStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val state = intent?.getStringExtra(TelephonyManager.EXTRA_STATE)
                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING,
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        pauseRecording("Phone call")
                    }
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        if (_recordingState.value is RecordingState.Paused) {
                            resumeRecording()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        registerReceiver(phoneStateReceiver, filter)
    }

    private fun setupAudioFocusListener() {
        audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    pauseRecording("Audio focus lost")
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (_recordingState.value is RecordingState.Paused) {
                        resumeRecording()
                    }
                }
            }
        }
    }

    private fun requestAudioFocus() {
        audioFocusChangeListener?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(it)
                    .build()
                audioManager?.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(it, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
        }
    }

    private fun abandonAudioFocus() {
        audioFocusChangeListener?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(it)
                    .build()
                audioManager?.abandonAudioFocusRequest(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(it)
            }
        }
    }

    private fun startForeground() {
        val notification = createNotification("Recording...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(status: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VoiceRecorderApp.RECORDING_CHANNEL_ID)
            .setContentTitle("Voice Recording")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val notification = createNotification(status)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        phoneStateReceiver?.let { unregisterReceiver(it) }
    }

    sealed class RecordingState {
        object Idle : RecordingState()
        object Recording : RecordingState()
        data class Paused(val reason: String) : RecordingState()
        object Stopped : RecordingState()
        data class Error(val message: String) : RecordingState()
    }
}
