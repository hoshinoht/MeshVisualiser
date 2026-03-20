package com.meshvisualiser.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.ar.core.ArCoreApk
import com.meshvisualiser.navigation.MeshNavHost
import com.meshvisualiser.navigation.Routes
import com.meshvisualiser.ui.theme.MeshVisualiserTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val snackbarHostState = SnackbarHostState()

    private fun showSnackbar(message: String) {
        lifecycleScope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Track whether camera permission has been granted — exposed to Compose
    private val _cameraPermissionGranted = androidx.compose.runtime.mutableStateOf(false)

    // Permission launcher
    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            _cameraPermissionGranted.value = granted
            if (!granted) {
                showSnackbar("Camera permission is required for AR")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check camera permission immediately on start
        _cameraPermissionGranted.value =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED

        setContent {
            MeshVisualiserTheme {
                val onboardingCompleted by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
                val cameraPermissionGranted by _cameraPermissionGranted

                val startDest = if (onboardingCompleted) Routes.CONNECTION else Routes.ONBOARDING

                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    containerColor = Color.Transparent
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        MeshNavHost(
                            viewModel = viewModel,
                            startDestination = startDest,
                            cameraPermissionGranted = cameraPermissionGranted,
                            onRequestCameraPermission = {
                                requestCameraPermission.launch(Manifest.permission.CAMERA)
                            },
                            onCheckArCoreAvailable = { onAvailable ->
                                checkArCoreAvailability(onAvailable)
                            }
                        )
                    }
                }
            }
        }
    }

    /**
     * Check whether ARCore is installed and up to date.
     * Calls [onAvailable] with true only if AR is usable.
     */
    private fun checkArCoreAvailability(onAvailable: (Boolean) -> Unit) {
        when (ArCoreApk.getInstance().checkAvailability(this)) {
            ArCoreApk.Availability.SUPPORTED_INSTALLED -> onAvailable(true)
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                // Prompt user to install/update ARCore
                try {
                    ArCoreApk.getInstance().requestInstall(this, true)
                    onAvailable(false) // will re-check after install
                } catch (e: Exception) {
                    showSnackbar("ARCore install failed: ${e.message}")
                    onAvailable(false)
                }
            }
            else -> {
                showSnackbar("AR is not supported on this device")
                onAvailable(false)
            }
        }
    }
}
