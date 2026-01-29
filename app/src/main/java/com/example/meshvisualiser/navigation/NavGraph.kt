package com.example.meshvisualiser.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.meshvisualiser.ui.MainScreen
import com.example.meshvisualiser.ui.MainViewModel
import com.example.meshvisualiser.ui.screens.ConnectionScreen
import com.example.meshvisualiser.ui.screens.OnboardingScreen

@Composable
fun MeshNavHost(
    viewModel: MainViewModel,
    startDestination: String
) {
    val navController = rememberNavController()

    val groupCode by viewModel.groupCode.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val lastGroupCode by viewModel.lastGroupCode.collectAsStateWithLifecycle()
    val groupCodeError by viewModel.groupCodeError.collectAsStateWithLifecycle()
    val meshState by viewModel.meshState.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    viewModel.completeOnboarding()
                    navController.navigate(Routes.CONNECTION) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CONNECTION) {
            ConnectionScreen(
                groupCode = groupCode,
                onGroupCodeChange = { viewModel.setGroupCode(it) },
                connectionState = connectionState,
                peers = peers,
                lastGroupCode = lastGroupCode,
                onJoinGroup = { viewModel.joinGroup() },
                onLeaveGroup = { viewModel.leaveGroup() },
                onStartMesh = {
                    viewModel.startMeshFromLobby()
                    navController.navigate(Routes.MESH) {
                        popUpTo(Routes.CONNECTION) { inclusive = true }
                    }
                },
                groupCodeError = groupCodeError
            )
        }

        composable(Routes.MESH) {
            MainScreen(viewModel = viewModel)
        }
    }
}
