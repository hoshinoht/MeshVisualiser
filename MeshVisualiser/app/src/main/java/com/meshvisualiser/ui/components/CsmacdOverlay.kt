package com.meshvisualiser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshvisualiser.simulation.CsmaState
import com.meshvisualiser.simulation.CsmacdState
import com.meshvisualiser.ui.theme.AiBadgeShape
import com.meshvisualiser.ui.theme.StepConnectedShape
import com.meshvisualiser.ui.theme.StepDiscoveringShape
import com.meshvisualiser.ui.theme.StepResolvingShape


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CsmacdOverlay(
    csmaState: CsmacdState,
    modifier: Modifier = Modifier
) {
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val sensingColor = MaterialTheme.colorScheme.tertiary
    val transmittingColor = MaterialTheme.colorScheme.primary
    val collisionColor = MaterialTheme.colorScheme.error
    val backoffColor = MaterialTheme.colorScheme.secondary

    val stateColor by animateColorAsState(
        targetValue = when (csmaState.currentState) {
            CsmaState.IDLE -> idleColor
            CsmaState.SENSING -> sensingColor
            CsmaState.TRANSMITTING -> transmittingColor
            CsmaState.COLLISION -> collisionColor
            CsmaState.BACKOFF -> backoffColor
            CsmaState.SUCCESS -> transmittingColor
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "csmaColor"
    )

    val stateLabel = when (csmaState.currentState) {
        CsmaState.IDLE -> "Channel idle"
        CsmaState.SENSING -> "Sensing channel..."
        CsmaState.TRANSMITTING -> "Transmitting"
        CsmaState.COLLISION -> "Collision detected!"
        CsmaState.BACKOFF -> "Backing off (random wait)"
        CsmaState.SUCCESS -> "Transmitted successfully"
    }

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

                // State indicator: M3E expressive shape per CSMA state
                val csmaShape = when (csmaState.currentState) {
                    CsmaState.IDLE -> CircleShape
                    CsmaState.SENSING -> StepDiscoveringShape    // diamond = scanning
                    CsmaState.TRANSMITTING -> StepConnectedShape // flower = active flow
                    CsmaState.COLLISION -> AiBadgeShape          // cookie6 = spiky/collision
                    CsmaState.BACKOFF -> StepResolvingShape      // clover = waiting/backoff
                    CsmaState.SUCCESS -> StepConnectedShape      // flower = success
                }
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(csmaShape)
                        .background(stateColor)
                )

                // State name as filled badge
                Surface(
                    color = stateColor.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = stateLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = stateColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = csmaState.currentStep,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (csmaState.collisionCount > 0) {
                Text(
                    text = "Collisions: ${csmaState.collisionCount} | Backoff: ${csmaState.backoffSlots} slots",
                    style = MaterialTheme.typography.bodySmall,
                    color = collisionColor,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (csmaState.currentState == CsmaState.BACKOFF && csmaState.backoffRemainingMs > 0) {
                Text(
                    text = "Backoff remaining: ${csmaState.backoffRemainingMs}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = backoffColor,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
