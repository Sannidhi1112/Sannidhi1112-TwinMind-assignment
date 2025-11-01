package com.twinmind.voicerecorder.presentation.recording

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.twinmind.voicerecorder.data.service.RecordingService

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun RecordingScreen(
    onNavigateBack: () -> Unit,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val recordingState by viewModel.recordingState.collectAsState()
    val elapsedTime by viewModel.elapsedTime.collectAsState()

    // Request permissions
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    )

    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recording") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Status
                Text(
                    text = when (recordingState) {
                        is RecordingService.RecordingState.Idle -> "Ready to record"
                        is RecordingService.RecordingState.Recording -> "Recording..."
                        is RecordingService.RecordingState.Paused -> "Paused - ${(recordingState as RecordingService.RecordingState.Paused).reason}"
                        is RecordingService.RecordingState.Stopped -> "Recording stopped"
                        is RecordingService.RecordingState.Error -> "Error: ${(recordingState as RecordingService.RecordingState.Error).message}"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = when (recordingState) {
                        is RecordingService.RecordingState.Error -> MaterialTheme.colorScheme.error
                        is RecordingService.RecordingState.Paused -> MaterialTheme.colorScheme.tertiary
                        is RecordingService.RecordingState.Recording -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                // Timer
                Text(
                    text = formatTime(elapsedTime),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                // Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (recordingState) {
                        is RecordingService.RecordingState.Idle -> {
                            // Start button
                            FloatingActionButton(
                                onClick = {
                                    if (permissionsState.allPermissionsGranted) {
                                        viewModel.startRecording()
                                    } else {
                                        permissionsState.launchMultiplePermissionRequest()
                                    }
                                },
                                modifier = Modifier.size(72.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Start Recording",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        is RecordingService.RecordingState.Recording -> {
                            // Pause button
                            FloatingActionButton(
                                onClick = { viewModel.pauseRecording() },
                                containerColor = MaterialTheme.colorScheme.tertiary
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause")
                            }

                            // Stop button
                            FloatingActionButton(
                                onClick = {
                                    viewModel.stopRecording()
                                    onNavigateBack()
                                },
                                containerColor = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Stop Recording",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        is RecordingService.RecordingState.Paused -> {
                            // Resume button
                            FloatingActionButton(
                                onClick = { viewModel.resumeRecording() }
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                            }

                            // Stop button
                            FloatingActionButton(
                                onClick = {
                                    viewModel.stopRecording()
                                    onNavigateBack()
                                },
                                containerColor = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Stop Recording",
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        is RecordingService.RecordingState.Stopped,
                        is RecordingService.RecordingState.Error -> {
                            Button(onClick = onNavigateBack) {
                                Text("Back to Dashboard")
                            }
                        }
                    }
                }

                // Permission warning
                if (!permissionsState.allPermissionsGranted) {
                    Card(
                        modifier = Modifier.padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "Microphone permission is required to record audio",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000) % 60
    val minutes = (milliseconds / (1000 * 60)) % 60
    val hours = (milliseconds / (1000 * 60 * 60))

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
