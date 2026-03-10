package com.meshvisualiser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class HardwareIssue(
    val type: HardwareType,
    val isReady: Boolean,
    val message: String
)

enum class HardwareType { BLUETOOTH, WIFI, LOCATION }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HardwareChecklist(
    issues: List<HardwareIssue>,
    onEnableAction: (HardwareType) -> Unit,
    modifier: Modifier = Modifier
) {
    val allReady = issues.all { it.isReady }

    if (allReady) {
        // Collapsed "All systems ready" chip
        val chipColor by animateColorAsState(
            targetValue = MaterialTheme.colorScheme.primaryContainer,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
            label = "chipColor"
        )
        AssistChip(
            onClick = {},
            label = { Text("All systems ready") },
            leadingIcon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            modifier = modifier,
            colors = AssistChipDefaults.assistChipColors(
                containerColor = chipColor
            )
        )
    } else {
        // Expanded row of status chips
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            issues.forEach { issue ->
                val icon = when (issue.type) {
                    HardwareType.BLUETOOTH -> Icons.Default.Bluetooth
                    HardwareType.WIFI -> Icons.Default.Wifi
                    HardwareType.LOCATION -> Icons.Default.LocationOn
                }
                val chipColor by animateColorAsState(
                    targetValue = if (issue.isReady) MaterialTheme.colorScheme.primaryContainer
                                  else MaterialTheme.colorScheme.errorContainer,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "hw_${issue.type}"
                )

                AssistChip(
                    onClick = { if (!issue.isReady) onEnableAction(issue.type) },
                    label = {
                        Text(
                            when (issue.type) {
                                HardwareType.BLUETOOTH -> "BT"
                                HardwareType.WIFI -> "WiFi"
                                HardwareType.LOCATION -> "GPS"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (issue.isReady) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (issue.isReady) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.error
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = chipColor
                    )
                )
            }
        }
    }
}
