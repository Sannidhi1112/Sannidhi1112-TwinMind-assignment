package com.twinmind.voicerecorder.presentation.recording

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.twinmind.voicerecorder.data.repository.RecordingRepository
import com.twinmind.voicerecorder.data.service.RecordingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingViewModel @Inject constructor(
    application: Application,
    private val repository: RecordingRepository
) : AndroidViewModel(application) {

    private var recordingService: RecordingService? = null
    private var isBound = false

    private val _recordingState = MutableStateFlow<RecordingService.RecordingState>(
        RecordingService.RecordingState.Idle
    )
    val recordingState: StateFlow<RecordingService.RecordingState> = _recordingState

    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime: StateFlow<Long> = _elapsedTime

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RecordingService.RecordingBinder
            recordingService = binder.getService()
            isBound = true

            // Observe service state
            viewModelScope.launch {
                recordingService?.recordingState?.collect { state ->
                    _recordingState.value = state
                }
            }

            viewModelScope.launch {
                recordingService?.elapsedTime?.collect { time ->
                    _elapsedTime.value = time
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            recordingService = null
            isBound = false
        }
    }

    fun startRecording() {
        val context = getApplication<Application>()
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START_RECORDING
        }
        context.startForegroundService(intent)
        context.bindService(intent, serviceConnection, 0)
    }

    fun stopRecording() {
        val context = getApplication<Application>()
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP_RECORDING
        }
        context.startService(intent)

        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }

    fun pauseRecording() {
        val context = getApplication<Application>()
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_PAUSE_RECORDING
        }
        context.startService(intent)
    }

    fun resumeRecording() {
        val context = getApplication<Application>()
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_RESUME_RECORDING
        }
        context.startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
    }
}
