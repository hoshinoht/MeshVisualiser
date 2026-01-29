package com.example.meshvisualiser.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

/** Helper for managing runtime permissions required by the app. */
object PermissionHelper {

  /** All permissions required for the app to function. */
  fun getRequiredPermissions(): Array<String> {
    val permissions =
            mutableListOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            )

    // Bluetooth permissions for Android 12+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
      permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
      permissions.add(Manifest.permission.BLUETOOTH_SCAN)
    }

    // Nearby WiFi for Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    return permissions.toTypedArray()
  }

  /** Check if all required permissions are granted. */
  fun hasAllPermissions(activity: ComponentActivity): Boolean {
    return getRequiredPermissions().all { permission ->
      ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }
  }

  /** Get the list of permissions that are not yet granted. */
  fun getMissingPermissions(activity: ComponentActivity): List<String> {
    return getRequiredPermissions().filter { permission ->
      ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
    }
  }
}
