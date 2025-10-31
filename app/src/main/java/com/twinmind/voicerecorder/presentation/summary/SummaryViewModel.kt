package com.twinmind.voicerecorder.presentation.summary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.twinmind.voicerecorder.data.local.entity.Recording
import com.twinmind.voicerecorder.data.local.entity.RecordingStatus
import com.twinmind.voicerecorder.data.repository.RecordingRepository
import com.twinmind.voicerecorder.data.worker.SummaryGenerationWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SummaryUiState(
    val recording: Recording? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val title: String = "",
    val summary: String = "",
    val actionItems: List<String> = emptyList(),
    val keyPoints: List<String> = emptyList()
)

@HiltViewModel
class SummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RecordingRepository
) : ViewModel() {

    private val recordingId: Long = savedStateHandle.get<Long>("recordingId") ?: -1L

    private val _uiState = MutableStateFlow(SummaryUiState(isLoading = true))
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    init {
        loadRecording()
    }

    private fun loadRecording() {
        viewModelScope.launch {
            repository.getRecordingById(recordingId)
                .collect { recording ->
                    recording?.let {
                        _uiState.update { state ->
                            state.copy(
                                recording = it,
                                isLoading = it.status == RecordingStatus.GENERATING_SUMMARY,
                                error = if (it.status == RecordingStatus.SUMMARY_FAILED)
                                    "Failed to generate summary" else null,
                                title = it.summaryTitle ?: "",
                                summary = it.summaryContent ?: "",
                                actionItems = it.actionItems?.split("\n")?.filter { item -> item.isNotBlank() }
                                    ?: emptyList(),
                                keyPoints = it.keyPoints?.split("\n")?.filter { item -> item.isNotBlank() }
                                    ?: emptyList()
                            )
                        }
                    }
                }
        }
    }

    fun retry() {
        viewModelScope.launch {
            val recording = repository.getRecordingByIdSync(recordingId)
            if (recording != null) {
                _uiState.update { it.copy(isLoading = true, error = null) }
                // Trigger summary generation again
                repository.updateRecordingStatus(recordingId, RecordingStatus.TRANSCRIPTION_COMPLETE)
                // TODO: Enqueue worker
            }
        }
    }
}
