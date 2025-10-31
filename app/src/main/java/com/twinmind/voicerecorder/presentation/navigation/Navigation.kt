package com.twinmind.voicerecorder.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.twinmind.voicerecorder.presentation.dashboard.DashboardScreen
import com.twinmind.voicerecorder.presentation.recording.RecordingScreen
import com.twinmind.voicerecorder.presentation.summary.SummaryScreen

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Recording : Screen("recording")
    object Summary : Screen("summary/{recordingId}") {
        fun createRoute(recordingId: Long) = "summary/$recordingId"
    }
}

@Composable
fun VoiceRecorderNavHost(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToRecording = {
                    navController.navigate(Screen.Recording.route)
                },
                onNavigateToSummary = { recordingId ->
                    navController.navigate(Screen.Summary.createRoute(recordingId))
                }
            )
        }

        composable(Screen.Recording.route) {
            RecordingScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Summary.route,
            arguments = listOf(
                navArgument("recordingId") {
                    type = NavType.LongType
                }
            )
        ) {
            SummaryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
