package com.meshvisualiser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clip
import com.meshvisualiser.ai.NarratorTemplates.NarratorMessage
import com.meshvisualiser.ui.theme.AiBadgeShape
import kotlinx.coroutines.delay

/**
 * Overlay showing the latest 2 narrator messages with swipe-to-dismiss and 8-second auto-dismiss.
 * An overflow [AssistChip] shows "+N more" when there are more than 2 queued messages.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NarratorOverlay(
    messages: List<NarratorMessage>,
    onDismiss: (NarratorMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()

    // Show only the last 2 messages; calculate overflow count
    val visibleMessages = messages.takeLast(2)
    val overflowCount = (messages.size - 2).coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Overflow badge — only visible when there are hidden older messages
        if (overflowCount > 0) {
            Box(modifier = Modifier.align(Alignment.End)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "+$overflowCount more",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        visibleMessages.forEach { message ->
            // key() ensures proper enter/exit animation per message identity
            key(message.title + message.explanation.take(20)) {
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(
                        animationSpec = spatialSpec,
                        initialOffsetY = { it / 2 }
                    ) + fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = slideOutVertically(
                        animationSpec = spatialSpec,
                        targetOffsetY = { it / 2 }
                    ) + fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec())
                ) {
                    NarratorCard(
                        message = message,
                        onDismiss = { onDismiss(message) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NarratorCard(
    message: NarratorMessage,
    onDismiss: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    var isDismissed by remember { mutableStateOf(false) }

    // 8-second auto-dismiss — pauses while expanded, restarts when collapsed
    LaunchedEffect(message.title, message.explanation, expanded) {
        if (!expanded) {
            delay(8_000L)
            if (!isDismissed) {
                isDismissed = true
                onDismiss()
            }
        }
    }

    val dismissState = rememberSwipeToDismissBoxState()

    // React to swipe completion: call onDismiss when user commits a swipe in either direction
    LaunchedEffect(dismissState.currentValue) {
        if (!isDismissed && (dismissState.currentValue == SwipeToDismissBoxValue.StartToEnd
            || dismissState.currentValue == SwipeToDismissBoxValue.EndToStart)
        ) {
            isDismissed = true
            onDismiss()
        }
    }

    // SwipeToDismissBox: horizontal swipe (either direction) dismisses the card
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        modifier = Modifier.fillMaxWidth()
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = MaterialTheme.shapes.large,
            onClick = { expanded = !expanded }
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(AiBadgeShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Narrator",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = message.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { if (!isDismissed) { isDismissed = true; onDismiss() } }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val displayText = if (expanded) {
                    message.explanation
                } else {
                    if (message.explanation.length > 100) {
                        message.explanation.take(100) + "..."
                    } else {
                        message.explanation
                    }
                }

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (!expanded && message.explanation.length > 100) {
                    Text(
                        text = "Tap to read more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
