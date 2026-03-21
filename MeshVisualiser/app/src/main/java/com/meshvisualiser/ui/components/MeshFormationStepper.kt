package com.meshvisualiser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.meshvisualiser.models.MeshState

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

    Row(
        modifier = modifier.fillMaxWidth(),
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
                Canvas(modifier = Modifier.size(10.dp)) {
                    drawCircle(color = dotColor)
                }
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
}
