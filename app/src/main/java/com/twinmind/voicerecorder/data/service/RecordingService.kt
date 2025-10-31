package com.twinmind.voicerecorder.data.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.StatFs
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.twinmind.voicerecorder.R
import com.twinmind.voicerecorder.VoiceRecorderApp
import com.twinmind.voicerecorder.data.local.entity.AudioChunk
import com.twinmind.voicerecorder.data.local.entity.Recording
import com.twinmind.voicerecorder.data.local.entity.RecordingStatus
import com.twinmind.voicerecorder.data.repository.RecordingRepository
import com.twinmind.voicerecorder.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.sqrt

@AndroidEntryPoint
class RecordingService : Service() {

    @Inject
    lateinit var repository: RecordingRepository

    @Inject
    lateinit var audioManager: AudioManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private var currentRecordingId: Long = -1
    private var chunkIndex = 0
    private var isRecording = false
    private var isPaused = false
    private var pauseReason: RecordingStatus? = null

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val chunkDurationMs = 30_000L // 30 seconds
    private val overlapDurationMs = 2_000L // 2 seconds overlap

    private var telephonyManager: TelephonyManager? = null
    private var phoneStateListener: Any? = null
    private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null
    private var headsetReceiver: BroadcastReceiver? = null

    private lateinit var recordingsDir: File
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val ACTION_START_RECORDING = "ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "ACTION_STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "ACTION_PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "ACTION_RESUME_RECORDING"

        const val NOTIFICATION_ID = 1001
        const val MIN_STORAGE_BYTES = 100 * 1024 * 1024L // 100 MB
        const val SILENT_AMPLITUDE_THRESHOLD = 500
        const val SILENT_DURATION_THRESHOLD_MS = 10_000L
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        recordingsDir = File(filesDir, "recordings").apply { mkdirs() }

        setupPhoneStateListener()
        setupAudioFocusListener()
        setupHeadsetListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> startRecording()
            ACTION_STOP_RECORDING -> stopRecording()
            ACTION_PAUSE_RECORDING -> pauseRecording(RecordingStatus.PAUSED_AUDIO_FOCUS)
            ACTION_RESUME_RECORDING -> resumeRecording()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRecording() {
        if (isRecording) return

        // Check storage
        if (!hasEnoughStorage()) {
            showError(getString(R.string.error_low_storage))
            stopSelf()
            return
        }

        serviceScope.launch {
            // Create new recording in database
            val recording = Recording(
                title = "Recording ${System.currentTimeMillis()}",
                startTime = System.currentTimeMillis(),
                status = RecordingStatus.RECORDING
            )
            currentRecordingId = repository.insertRecording(recording)

            withContext(Dispatchers.Main) {
                startForeground(NOTIFICATION_ID, buildNotification(RecordingStatus.RECORDING))
                isRecording = true
                chunkIndex = 0
                startAudioRecording()
            }
        }
    }

    private fun stopRecording() {
        isRecording = false
        isPaused = false
        stopAudioRecording()

        serviceScope.launch {
            if (currentRecordingId != -1L) {
                val recording = repository.getRecordingByIdSync(currentRecordingId)
                recording?.let {
                    val duration = System.currentTimeMillis() - it.startTime
                    repository.finalizeRecording(
                        id = currentRecordingId,
                        endTime = System.currentTimeMillis(),
                        duration = duration,
                        totalChunks = chunkIndex,
                        status = RecordingStatus.STOPPED
                    )

                    // Start transcription worker
                    startTranscriptionWorker(currentRecordingId)
                }
            }

            withContext(Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun pauseRecording(reason: RecordingStatus) {
        if (!isRecording || isPaused) return

        isPaused = true
        pauseReason = reason
        stopAudioRecording()

        serviceScope.launch {
            if (currentRecordingId != -1L) {
                repository.updateRecordingStatus(currentRecordingId, reason)
            }
        }

        updateNotification(reason)
    }

    private fun resumeRecording() {
        if (!isRecording || !isPaused) return

        isPaused = false
        pauseReason = null

        serviceScope.launch {
            if (currentRecordingId != -1L) {
                repository.updateRecordingStatus(currentRecordingId, RecordingStatus.RECORDING)
            }
        }

        updateNotification(RecordingStatus.RECORDING)
        startAudioRecording()
    }

    private fun startAudioRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopRecording()
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize * 4
        )

        audioRecord?.startRecording()

        recordingJob = serviceScope.launch {
            recordAudioChunks()
        }
    }

    private fun stopAudioRecording() {
        recordingJob?.cancel()
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
    }

    private suspend fun recordAudioChunks() {
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val buffer = ShortArray(bufferSize)
        val bytesPerSample = 2 // 16-bit audio
        val samplesPerChunk = (sampleRate * chunkDurationMs / 1000).toInt()
        val overlapSamples = (sampleRate * overlapDurationMs / 1000).toInt()

        var chunkBuffer = mutableListOf<Short>()
        var silentStartTime: Long? = null
        var lastAmplitudeCheckTime = System.currentTimeMillis()

        while (isActive && isRecording) {
            if (isPaused) {
                delay(100)
                continue
            }

            // Check storage before each chunk
            if (!hasEnoughStorage()) {
                showError(getString(R.string.error_low_storage))
                withContext(Dispatchers.Main) {
                    stopRecording()
                }
                break
            }

            val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
            if (read > 0) {
                chunkBuffer.addAll(buffer.take(read))

                // Check for silent audio every second
                val now = System.currentTimeMillis()
                if (now - lastAmplitudeCheckTime >= 1000) {
                    val amplitude = calculateAmplitude(buffer, read)
                    if (amplitude < SILENT_AMPLITUDE_THRESHOLD) {
                        if (silentStartTime == null) {
                            silentStartTime = now
                        } else if (now - silentStartTime!! >= SILENT_DURATION_THRESHOLD_MS) {
                            showWarning(getString(R.string.error_no_audio))
                            silentStartTime = now // Reset to avoid repeated warnings
                        }
                    } else {
                        silentStartTime = null
                    }
                    lastAmplitudeCheckTime = now
                }

                // Save chunk when we have enough samples
                if (chunkBuffer.size >= samplesPerChunk) {
                    saveChunk(chunkBuffer.toList())

                    // Keep overlap samples for next chunk
                    chunkBuffer = chunkBuffer.takeLast(overlapSamples).toMutableList()
                    chunkIndex++
                }
            }
        }

        // Save remaining buffer as final chunk
        if (chunkBuffer.isNotEmpty()) {
            saveChunk(chunkBuffer)
        }
    }

    private suspend fun saveChunk(samples: List<Short>) {
        val recording = repository.getRecordingByIdSync(currentRecordingId) ?: return

        val chunkFile = File(recordingsDir, "${currentRecordingId}_chunk_$chunkIndex.wav")
        val duration = (samples.size.toLong() * 1000) / sampleRate

        // Write WAV file
        writeWavFile(chunkFile, samples)

        // Save chunk to database
        val chunk = AudioChunk(
            recordingId = currentRecordingId,
            chunkIndex = chunkIndex,
            filePath = chunkFile.absolutePath,
            duration = duration,
            fileSize = chunkFile.length(),
            startTime = recording.startTime + (chunkIndex * chunkDurationMs),
            endTime = recording.startTime + (chunkIndex * chunkDurationMs) + duration
        )

        repository.insertChunk(chunk)

        // Trigger transcription for this chunk
        startChunkTranscriptionWorker(chunk.recordingId, chunkIndex)
    }

    private fun writeWavFile(file: File, samples: List<Short>) {
        FileOutputStream(file).use { fos ->
            val byteRate = sampleRate * 2 // 16-bit mono
            val dataSize = samples.size * 2

            // WAV header
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // PCM format chunk size
            header.putShort(1) // Audio format (PCM)
            header.putShort(1) // Number of channels
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(2) // Block align
            header.putShort(16) // Bits per sample
            header.put("data".toByteArray())
            header.putInt(dataSize)

            fos.write(header.array())

            // Write audio data
            val audioData = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach { audioData.putShort(it) }
            fos.write(audioData.array())
        }
    }

    private fun calculateAmplitude(buffer: ShortArray, size: Int): Double {
        var sum = 0.0
        for (i in 0 until size) {
            sum += abs(buffer[i].toDouble())
        }
        return sum / size
    }

    private fun hasEnoughStorage(): Boolean {
        val stat = StatFs(filesDir.absolutePath)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        return availableBytes > MIN_STORAGE_BYTES
    }

    private fun setupPhoneStateListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallStateChange(state)
                }
            }
            phoneStateListener = callback
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                telephonyManager?.registerTelephonyCallback(mainExecutor, callback)
            }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallStateChange(state)
                }
            }
            phoneStateListener = listener
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                @Suppress("DEPRECATION")
                telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        }
    }

    private fun handleCallStateChange(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING,
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                pauseRecording(RecordingStatus.PAUSED_CALL)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (pauseReason == RecordingStatus.PAUSED_CALL) {
                    resumeRecording()
                }
            }
        }
    }

    private fun setupAudioFocusListener() {
        audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    pauseRecording(RecordingStatus.PAUSED_AUDIO_FOCUS)
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    if (pauseReason == RecordingStatus.PAUSED_AUDIO_FOCUS) {
                        resumeRecording()
                    }
                }
            }
        }

        audioFocusChangeListener?.let { listener ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(listener)
                    .build()
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    listener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }
    }

    private fun setupHeadsetListener() {
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }

        headsetReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    AudioManager.ACTION_HEADSET_PLUG -> {
                        val state = intent.getIntExtra("state", -1)
                        val name = intent.getStringExtra("name") ?: "headset"
                        if (state == 1) {
                            showInfo("Recording continues with $name")
                        } else if (state == 0) {
                            showInfo("Recording continues with device microphone")
                        }
                    }
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            showInfo("Recording continues with Bluetooth headset")
                        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                            showInfo("Recording continues with device microphone")
                        }
                    }
                }
            }
        }

        registerReceiver(headsetReceiver, filter)
    }

    private fun buildNotification(status: RecordingStatus): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val statusText = when (status) {
            RecordingStatus.RECORDING -> getString(R.string.status_recording)
            RecordingStatus.PAUSED_CALL -> getString(R.string.status_paused_call)
            RecordingStatus.PAUSED_AUDIO_FOCUS -> getString(R.string.status_paused_audio_focus)
            else -> getString(R.string.status_stopped)
        }

        val builder = NotificationCompat.Builder(this, VoiceRecorderApp.RECORDING_CHANNEL_ID)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        // Add stop action
        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.addAction(0, getString(R.string.action_stop), stopPendingIntent)

        // Add pause/resume action if paused
        if (status == RecordingStatus.PAUSED_AUDIO_FOCUS) {
            val resumeIntent = Intent(this, RecordingService::class.java).apply {
                action = ACTION_RESUME_RECORDING
            }
            val resumePendingIntent = PendingIntent.getService(
                this, 2, resumeIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, getString(R.string.action_resume), resumePendingIntent)
        }

        return builder.build()
    }

    private fun updateNotification(status: RecordingStatus) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun showError(message: String) {
        // TODO: Send broadcast or use StateFlow to update UI
    }

    private fun showWarning(message: String) {
        // TODO: Send broadcast or use StateFlow to update UI
    }

    private fun showInfo(message: String) {
        // TODO: Send broadcast or use StateFlow to update UI
    }

    private fun startTranscriptionWorker(recordingId: Long) {
        val workRequest = com.twinmind.voicerecorder.data.worker.TranscriptionWorker.createWorkRequest(recordingId)
        androidx.work.WorkManager.getInstance(applicationContext).enqueue(workRequest)
    }

    private fun startChunkTranscriptionWorker(recordingId: Long, chunkIndex: Int) {
        // For individual chunks, we can use the same transcription worker
        // It will process all pending chunks for the recording
        val workRequest = com.twinmind.voicerecorder.data.worker.TranscriptionWorker.createWorkRequest(recordingId)
        androidx.work.WorkManager.getInstance(applicationContext).enqueue(workRequest)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Cleanup listeners
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (phoneStateListener as? TelephonyCallback)?.let {
                telephonyManager?.unregisterTelephonyCallback(it)
            }
        } else {
            @Suppress("DEPRECATION")
            (phoneStateListener as? PhoneStateListener)?.let {
                telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }

        headsetReceiver?.let { unregisterReceiver(it) }

        stopAudioRecording()
        serviceScope.cancel()
    }
}
