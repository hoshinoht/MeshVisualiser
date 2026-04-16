package com.meshvisualiser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.meshvisualiser.models.MeshState
import com.meshvisualiser.ui.theme.StepConnectedShape
import com.meshvisualiser.ui.theme.StepDiscoveringShape
import com.meshvisualiser.ui.theme.StepElectingShape
import com.meshvisualiser.ui.theme.StepResolvingShape

private const val STEP_PULSE_MS = 800

private data class StepInfo(
    val label: String,
    val sublabel: String,
    val state: MeshState
)

private val steps = listOf(
    StepInfo("Discovering", "Finding nearby peers...", MeshState.DISCOVERING),
    StepInfo("Electing", "Bully algorithm in progress...", MeshState.ELECTING),
    StepInfo("Resolving", "Establishing shared anchor...", MeshState.RESOLVING),
    StepInfo("Connected", "Mesh formed!", MeshState.CONNECTED)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MeshFormationStepper(
    currentState: MeshState,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    val currentIndex = steps.indexOfFirst { it.state == currentState }.coerceAtLeast(0)

    // Pulsing animation for the active step dot
    val infiniteTransition = rememberInfiniteTransition(label = "stepPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = STEP_PULSE_MS),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stepPulseAlpha"
    )

    Column(modifier = modifier) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            val isCompleted = index < currentIndex
            val isCurrent = index == currentIndex

            val dotColor by animateColorAsState(
                targetValue = when {
                    isCompleted || isCurrent -> primary
                    else -> surfaceVariant
                },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "stepColor_$index"
            )

            val textColor by animateColorAsState(
                targetValue = if (isCompleted || isCurrent) onSurface else onSurfaceVariant,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "stepTextColor_$index"
            )

            // Dot + label
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val stepShape = when (step.state) {
                    MeshState.DISCOVERING -> StepDiscoveringShape
                    MeshState.ELECTING -> StepElectingShape
                    MeshState.RESOLVING -> StepResolvingShape
                    MeshState.CONNECTED -> StepConnectedShape
                }
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(stepShape)
                        .background(
                            when {
                                isCompleted -> dotColor
                                isCurrent -> dotColor.copy(alpha = pulseAlpha)
                                else -> dotColor.copy(alpha = 0.3f)
                            }
                        )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = step.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor
                )
            }

            // Connecting line between steps
            if (index < steps.lastIndex) {
                val lineColor by animateColorAsState(
                    targetValue = if (index < currentIndex) primary else surfaceVariant,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "lineColor_$index"
                )
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    drawLine(
                        color = lineColor,
                        start = Offset(0f, size.height / 2),
                        end = Offset(size.width, size.height / 2),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }

    // Sublabel for the current step
    val currentStep = steps.find { it.state == currentState }
    if (currentStep != null) {
        Text(
            text = currentStep.sublabel,
            style = MaterialTheme.typography.bodySmall,
            color = onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
    } // end Column
}
