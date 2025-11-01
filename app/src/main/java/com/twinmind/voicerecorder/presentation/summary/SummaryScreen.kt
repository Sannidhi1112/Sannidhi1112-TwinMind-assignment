package com.twinmind.voicerecorder.presentation.summary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinmind.voicerecorder.data.local.entity.RecordingStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    recordingId: Long,
    onNavigateBack: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel()
) {
    val recording by viewModel.recording.collectAsState()

    LaunchedEffect(recordingId) {
        viewModel.loadRecording(recordingId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Summary") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        recording?.let { rec ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = rec.summaryTitle ?: rec.title,
                    style = MaterialTheme.typography.headlineMedium
                )

                // Status
                when (rec.status) {
                    RecordingStatus.TRANSCRIBING -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Transcribing audio...")
                    }
                    RecordingStatus.GENERATING_SUMMARY -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Generating summary...")
                    }
                    RecordingStatus.TRANSCRIPTION_FAILED,
                    RecordingStatus.SUMMARY_FAILED -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = rec.errorMessage ?: "An error occurred",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    else -> {}
                }

                // Transcript
                if (!rec.transcript.isNullOrEmpty()) {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Transcript",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = rec.transcript ?: "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Summary
                if (!rec.summary.isNullOrEmpty()) {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Summary",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = rec.summary ?: "",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Action Items
                if (!rec.summaryActionItems.isNullOrEmpty()) {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Action Items",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            rec.summaryActionItems?.split("\n")?.forEach { item ->
                                if (item.isNotBlank()) {
                                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text("• ", style = MaterialTheme.typography.bodyMedium)
                                        Text(item, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                // Key Points
                if (!rec.summaryKeyPoints.isNullOrEmpty()) {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Key Points",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            rec.summaryKeyPoints?.split("\n")?.forEach { point ->
                                if (point.isNotBlank()) {
                                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Text("• ", style = MaterialTheme.typography.bodyMedium)
                                        Text(point, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
