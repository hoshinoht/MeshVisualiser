package com.meshvisualiser.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.meshvisualiser.ui.screens.ArScreen
import com.meshvisualiser.ui.MainScreen
import com.meshvisualiser.ui.MainViewModel
import com.meshvisualiser.ui.screens.ConnectionScreen
import com.meshvisualiser.ui.screens.OnboardingScreen

@Composable
fun MeshNavHost(
    viewModel: MainViewModel,
    startDestination: String,
    cameraPermissionGranted: Boolean,
    onRequestCameraPermission: () -> Unit,
    onCheckArCoreAvailable: (onAvailable: (Boolean) -> Unit) -> Unit
) {
    val navController = rememberNavController()

    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val groupCode by viewModel.groupCode.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val lastGroupCode by viewModel.lastGroupCode.collectAsStateWithLifecycle()
    val groupCodeError by viewModel.groupCodeError.collectAsStateWithLifecycle()
    val meshState by viewModel.meshState.collectAsStateWithLifecycle()
    val nearbyIsDiscovering by viewModel.nearbyIsDiscovering.collectAsStateWithLifecycle()
    val nearbyIsAdvertising by viewModel.nearbyIsAdvertising.collectAsStateWithLifecycle()
    val nearbyError by viewModel.nearbyError.collectAsStateWithLifecycle()
    val hardwareIssues by viewModel.hardwareIssues.collectAsStateWithLifecycle()
    val discoveryTimeoutReached by viewModel.discoveryTimeoutReached.collectAsStateWithLifecycle()

    // Reactive navigation: when any peer (local or remote) triggers START_MESH,
    // the ViewModel emits on navigateToMesh and we navigate here.
    LaunchedEffect(Unit) {
        viewModel.navigateToMesh.collect {
            navController.navigate(Routes.MESH) {
                popUpTo(Routes.CONNECTION) { inclusive = true }
            }
        }
    }

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
                displayName = displayName,
                onDisplayNameChange = { viewModel.setDisplayName(it) },
                groupCode = groupCode,
                onGroupCodeChange = { viewModel.setGroupCode(it) },
                connectionState = connectionState,
                peers = peers,
                lastGroupCode = lastGroupCode,
                onJoinGroup = { viewModel.joinGroup() },
                onLeaveGroup = { viewModel.leaveGroup() },
                onStartMesh = { viewModel.startMeshFromLobby() },
                groupCodeError = groupCodeError,
                isDiscovering = nearbyIsDiscovering,
                isAdvertising = nearbyIsAdvertising,
                nearbyError = nearbyError,
                hardwareIssues = hardwareIssues,
                onEnableHardware = { /* Open system settings — handled by OS intents */ },
                discoveryTimeoutReached = discoveryTimeoutReached,
                onRetryDiscovery = { viewModel.retryDiscovery() }
            )
        }

        composable(Routes.MESH) {
            MainScreen(
                viewModel = viewModel,
                onNavigateToAr = {
                    if (!cameraPermissionGranted) {
                        onRequestCameraPermission()
                        return@MainScreen
                    }
                    onCheckArCoreAvailable { available ->
                        if (available) navController.navigate(Routes.AR)
                    }
                }
            )
        }

        composable("ar") {
           ArScreen(
               viewModel = viewModel,
               onBack = { navController.popBackStack() }
           )
        }

    }
}
