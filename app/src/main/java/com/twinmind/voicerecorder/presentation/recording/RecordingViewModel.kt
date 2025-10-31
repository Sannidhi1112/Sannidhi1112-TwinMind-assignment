package com.twinmind.voicerecorder.presentation.recording

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twinmind.voicerecorder.data.local.entity.RecordingStatus
import com.twinmind.voicerecorder.data.repository.RecordingRepository
import com.twinmind.voicerecorder.data.service.RecordingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecordingUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val status: RecordingStatus = RecordingStatus.STOPPED,
    val duration: Long = 0L,
    val statusMessage: String = "Ready to record"
)

@HiltViewModel
class RecordingViewModel @Inject constructor(
    application: Application,
    private val repository: RecordingRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private var startTime: Long = 0L

    init {
        observeActiveRecording()
    }

    private fun observeActiveRecording() {
        viewModelScope.launch {
            repository.getRecordingByStatus(RecordingStatus.RECORDING)?.let { recording ->
                _uiState.update {
                    it.copy(
                        isRecording = true,
                        status = recording.status,
                        statusMessage = getStatusMessage(recording.status)
                    )
                }
                startTime = recording.startTime
            }
        }
    }

    fun startRecording() {
        val context = getApplication<Application>()
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START_RECORDING
        }
        ContextCompat.startForegroundService(context, intent)

        startTime = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                isRecording = true,
                status = RecordingStatus.RECORDING,
                statusMessage = "Recording..."
            )
        }
    }

    fun stopRecording() {
        val context = getApplication<Application>()
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP_RECORDING
        }
        context.startService(intent)

        _uiState.update {
            it.copy(
                isRecording = false,
                isPaused = false,
                status = RecordingStatus.STOPPED,
                duration = 0L,
                statusMessage = "Stopped"
            )
        }
    }

    fun updateDuration(duration: Long) {
        _uiState.update { it.copy(duration = duration) }
    }

    private fun getStatusMessage(status: RecordingStatus): String {
        return when (status) {
            RecordingStatus.RECORDING -> "Recording..."
            RecordingStatus.PAUSED_CALL -> "Paused - Phone call"
            RecordingStatus.PAUSED_AUDIO_FOCUS -> "Paused - Audio focus lost"
            RecordingStatus.STOPPED -> "Stopped"
            RecordingStatus.TRANSCRIBING -> "Transcribing..."
            RecordingStatus.TRANSCRIPTION_COMPLETE -> "Transcription complete"
            RecordingStatus.GENERATING_SUMMARY -> "Generating summary..."
            RecordingStatus.SUMMARY_COMPLETE -> "Complete"
            else -> "Ready to record"
        }
    }
}
