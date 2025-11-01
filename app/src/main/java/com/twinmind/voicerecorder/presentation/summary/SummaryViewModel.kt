package com.twinmind.voicerecorder.presentation.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinmind.voicerecorder.data.local.entity.Recording
import com.twinmind.voicerecorder.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val repository: RecordingRepository
) : ViewModel() {

    private val _recording = MutableStateFlow<Recording?>(null)
    val recording: StateFlow<Recording?> = _recording

    fun loadRecording(recordingId: Long) {
        viewModelScope.launch {
            repository.getRecordingById(recordingId).collectLatest { recording ->
                _recording.value = recording
            }
        }
    }
}
