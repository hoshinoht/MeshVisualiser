package com.meshvisualiser.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NetworkWifi
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meshvisualiser.ui.PermissionHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

    val activity = context as androidx.activity.ComponentActivity

    var permissionDenied by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var allGranted by remember {
        mutableStateOf(PermissionHelper.hasAllPermissions(activity))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        allGranted = granted
        if (granted) {
            onComplete()
        } else {
            permissionDenied = true
            // Check if any denied permission was permanently denied ("Don't ask again")
            val deniedPerms = permissions.filter { !it.value }.keys
            permanentlyDenied = deniedPerms.any { perm ->
                !activity.shouldShowRequestPermissionRationale(perm)
            }
        }
    }

    // Auto-navigate if permissions already granted and we're on page 3
    LaunchedEffect(pagerState.currentPage, allGranted) {
        if (pagerState.currentPage == 2 && allGranted) {
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> OnboardingPage(
                    icon = Icons.Filled.Hub,
                    title = "Mesh Visualiser",
                    body = "See how devices connect and talk to each other in a peer-to-peer mesh network — right in augmented reality."
                )
                1 -> OnboardingPage(
                    icon = Icons.Filled.Sensors,
                    title = "How It Works",
                    body = "Your device discovers nearby peers, elects a leader, and visualises the network in AR. Send TCP & UDP packets and watch them travel between devices."
                )
                2 -> PermissionsPage(
                    permissionDenied = permissionDenied,
                    permanentlyDenied = permanentlyDenied,
                    onRequestPermissions = {
                        permissionDenied = false
                        permanentlyDenied = false
                        permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
                    },
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )
            }
        }

        // Bottom navigation row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Skip button (hidden on last page) — TextButton hierarchy
            if (pagerState.currentPage < 2) {
                TextButton(onClick = {
                    scope.launch { pagerState.animateScrollToPage(2) }
                }) {
                    Text(
                        "Skip",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(64.dp))
            }

            // Page indicators — morphing pill (existing logic preserved)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val isActive = index == pagerState.currentPage
                    val color by animateColorAsState(
                        targetValue = if (isActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        label = "dotColor"
                    )
                    val width by animateDpAsState(
                        targetValue = if (isActive) 24.dp else 8.dp,
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        label = "dotWidth"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                    )
                }
            }

            // Next → Button; Grant Permissions → FilledTonalButton
            if (pagerState.currentPage < 2) {
                Button(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text("Next")
                }
            } else {
                FilledTonalButton(
                    onClick = {
                        if (allGranted) {
                            onComplete()
                        } else if (permanentlyDenied) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } else {
                            permissionDenied = false
                            permanentlyDenied = false
                            permissionLauncher.launch(PermissionHelper.getRequiredPermissions())
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        when {
                            allGranted -> "Get Started"
                            permanentlyDenied -> "Open App Settings"
                            else -> "Grant Permissions"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    icon: ImageVector,
    title: String,
    body: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // ElevatedCard replaces GlassSurface
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private data class PermissionItem(
    val label: String,
    val reason: String,
    val icon: ImageVector
)

@Composable
private fun PermissionsPage(
    permissionDenied: Boolean,
    permanentlyDenied: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val permissions = remember { buildList {
        add(
            PermissionItem(
                "Camera",
                "AR needs your camera to overlay the mesh on the real world",
                Icons.Default.Camera
            )
        )
        add(
            PermissionItem(
                "Location",
                "Android requires location access to discover nearby Bluetooth & WiFi devices",
                Icons.Default.LocationOn
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(
                PermissionItem(
                    "Bluetooth",
                    "Used to find and connect to other devices",
                    Icons.Default.Sensors
                )
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(
                PermissionItem(
                    "Nearby WiFi",
                    "Enables high-speed data transfer between peers",
                    Icons.Default.NetworkWifi
                )
            )
        }
    } }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // ElevatedCard replaces GlassSurface
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Before We Start",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ListItem-based permission list with icon + supporting text
                permissions.forEach { perm ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = perm.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = perm.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        supportingContent = {
                            Text(
                                text = perm.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }

                if (permissionDenied) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (permanentlyDenied)
                                    "Some permissions were permanently denied. Please grant them in app settings."
                                else
                                    "Some permissions were denied. The app needs these to work.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (permanentlyDenied) {
                                Button(onClick = onOpenSettings) {
                                    Text("Open App Settings")
                                }
                            } else {
                                OutlinedButton(onClick = onRequestPermissions) {
                                    Text("Try Again")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
