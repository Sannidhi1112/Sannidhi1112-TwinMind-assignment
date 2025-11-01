package com.twinmind.voicerecorder.presentation.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.twinmind.voicerecorder.data.local.entity.Recording
import com.twinmind.voicerecorder.data.local.entity.RecordingStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToRecording: () -> Unit,
    onNavigateToSummary: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val recordings by viewModel.recordings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Recordings") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToRecording) {
                Icon(Icons.Default.Add, contentDescription = "New Recording")
            }
        }
    ) { padding ->
        if (recordings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No recordings yet\nTap + to start recording",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recordings, key = { it.id }) { recording ->
                    RecordingCard(
                        recording = recording,
                        onClick = { onNavigateToSummary(recording.id) },
                        onDelete = { viewModel.deleteRecording(recording.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingCard(
    recording: Recording,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recording.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDate(recording.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDuration(recording.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                RecordingStatusChip(status = recording.status)
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun RecordingStatusChip(status: RecordingStatus) {
    val (text, color) = when (status) {
        RecordingStatus.RECORDING -> "Recording" to MaterialTheme.colorScheme.primary
        RecordingStatus.PAUSED -> "Paused" to MaterialTheme.colorScheme.tertiary
        RecordingStatus.STOPPED -> "Stopped" to MaterialTheme.colorScheme.secondary
        RecordingStatus.TRANSCRIBING -> "Transcribing..." to MaterialTheme.colorScheme.primary
        RecordingStatus.TRANSCRIPTION_COMPLETE -> "Transcribed" to MaterialTheme.colorScheme.primary
        RecordingStatus.TRANSCRIPTION_FAILED -> "Transcription Failed" to MaterialTheme.colorScheme.error
        RecordingStatus.GENERATING_SUMMARY -> "Generating Summary..." to MaterialTheme.colorScheme.primary
        RecordingStatus.SUMMARY_COMPLETE -> "Summary Ready" to MaterialTheme.colorScheme.primary
        RecordingStatus.SUMMARY_FAILED -> "Summary Failed" to MaterialTheme.colorScheme.error
        RecordingStatus.COMPLETED -> "Completed" to MaterialTheme.colorScheme.primary
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000) % 60
    val minutes = (durationMs / (1000 * 60)) % 60
    val hours = (durationMs / (1000 * 60 * 60))

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
