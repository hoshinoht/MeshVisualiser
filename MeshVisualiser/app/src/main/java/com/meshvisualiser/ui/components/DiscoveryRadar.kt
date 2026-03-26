package com.meshvisualiser.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.toPath
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val RING_PULSE_MS = 2000
private const val RING_DELAY_MS = 600
private const val SWEEP_ROTATION_MS = 8000

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DiscoveryRadar(
    isActive: Boolean,
    peerCount: Int,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

    // Pulsing rings
    val infiniteTransition = rememberInfiniteTransition(label = "radar")

    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(RING_PULSE_MS, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1Alpha"
    )
    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(RING_PULSE_MS, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1Scale"
    )

    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(RING_PULSE_MS, delayMillis = RING_DELAY_MS, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2Alpha"
    )
    val ring2Scale by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(RING_PULSE_MS, delayMillis = RING_DELAY_MS, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2Scale"
    )

    val ring3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(RING_PULSE_MS, delayMillis = RING_DELAY_MS * 2, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring3Alpha"
    )
    val ring3Scale by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(RING_PULSE_MS, delayMillis = RING_DELAY_MS * 2, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring3Scale"
    )

    // Center shape rotation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(SWEEP_ROTATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Blip positions for discovered peers
    val blipAngles = remember(peerCount) {
        List(peerCount) { i -> (i * 360f / peerCount.coerceAtLeast(1)) + 45f }
    }

    Box(
        modifier = modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val maxRadius = size.minDimension / 2f

            if (isActive) {
                // Pulsing concentric rings
                listOf(
                    ring1Scale to ring1Alpha,
                    ring2Scale to ring2Alpha,
                    ring3Scale to ring3Alpha
                ).forEach { (ringScale, ringAlpha) ->
                    drawCircle(
                        color = primaryColor.copy(alpha = ringAlpha),
                        radius = maxRadius * ringScale,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 2f)
                    )
                }
            }

            // Static outer ring
            drawCircle(
                color = surfaceVariant,
                radius = maxRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 1f)
            )

            // Center shape (Cookie6Sided from MaterialShapes)
            val sunnyShape = MaterialShapes.Cookie6Sided
            val sunnyPath = sunnyShape.toPath()
            val centerScale = 0.15f

            rotate(rotation, pivot = Offset(centerX, centerY)) {
                scale(centerScale, pivot = Offset(centerX, centerY)) {
                    drawPath(
                        path = sunnyPath.asComposePath(),
                        color = primaryColor,
                        style = Stroke(width = 8f)
                    )
                }
            }

            // Peer blips
            blipAngles.forEach { angle ->
                val rad = angle * PI.toFloat() / 180f
                val blipRadius = maxRadius * 0.65f
                val bx = centerX + cos(rad) * blipRadius
                val by = centerY + sin(rad) * blipRadius
                drawCircle(
                    color = primaryColor,
                    radius = 6f,
                    center = Offset(bx, by)
                )
            }
        }

        // Peer count text
        Text(
            text = if (peerCount > 0) "$peerCount" else "",
            style = MaterialTheme.typography.labelSmall,
            color = onSurface
        )
    }
}
