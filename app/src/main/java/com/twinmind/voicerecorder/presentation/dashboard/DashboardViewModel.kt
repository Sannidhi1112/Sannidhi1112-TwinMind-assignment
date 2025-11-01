package com.twinmind.voicerecorder.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinmind.voicerecorder.data.local.entity.Recording
import com.twinmind.voicerecorder.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: RecordingRepository
) : ViewModel() {

    val recordings: StateFlow<List<Recording>> = repository.getAllRecordings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteRecording(id: Long) {
        viewModelScope.launch {
            repository.deleteRecording(id)
        }
    }
}
