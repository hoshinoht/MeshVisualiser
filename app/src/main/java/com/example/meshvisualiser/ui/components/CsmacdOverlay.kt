package com.example.meshvisualiser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.meshvisualiser.simulation.CsmaState
import com.example.meshvisualiser.simulation.CsmacdState
import com.example.meshvisualiser.ui.theme.*

@Composable
fun CsmacdOverlay(
    csmaState: CsmacdState,
    modifier: Modifier = Modifier
) {
    val stateColor by animateColorAsState(
        targetValue = when (csmaState.currentState) {
            CsmaState.IDLE -> CsmaIdle
            CsmaState.SENSING -> CsmaSensing
            CsmaState.TRANSMITTING -> CsmaTransmitting
            CsmaState.COLLISION -> CsmaCollision
            CsmaState.BACKOFF -> CsmaBackoff
            CsmaState.SUCCESS -> CsmaTransmitting
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "csmaColor"
    )

    GlassSurface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "CSMA/CD",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // State indicator dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(stateColor)
                )

                Text(
                    text = csmaState.currentState.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = stateColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = csmaState.currentStep,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )

            if (csmaState.collisionCount > 0) {
                Text(
                    text = "Collisions: ${csmaState.collisionCount} | Backoff: ${csmaState.backoffSlots} slots",
                    style = MaterialTheme.typography.bodySmall,
                    color = CsmaCollision,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (csmaState.currentState == CsmaState.BACKOFF && csmaState.backoffRemainingMs > 0) {
                Text(
                    text = "Backoff remaining: ${csmaState.backoffRemainingMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = CsmaBackoff,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
