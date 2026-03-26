package com.meshvisualiser.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.jeziellago.compose.markdowntext.MarkdownText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SessionSummarySheet(
    summaryContent: String?,
    isLoading: Boolean,
    error: String?,
    onRegenerate: () -> Unit,
    onNewSession: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    // State for the "New Session" confirmation dialog
    var showNewSessionDialog by remember { mutableStateOf(false) }

    // New Session confirmation AlertDialog
    if (showNewSessionDialog) {
        AlertDialog(
            onDismissRequest = { showNewSessionDialog = false },
            title = {
                Text(
                    "Start New Session?",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    "This will end the current session and clear all session data. Are you sure?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        showNewSessionDialog = false
                        onNewSession()
                    }
                ) {
                    Text("Yes, New Session")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSessionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Session Summary",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            when {
                isLoading -> {
                    // LoadingIndicator replaces CircularProgressIndicator
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LoadingIndicator()
                        Text(
                            "Generating your learning summary...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Could not generate summary",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = onRegenerate) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Retry", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                summaryContent == null -> {
                    Text(
                        text = "AI will summarize the messages, transfers, and quiz results from this session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    Button(onClick = onRegenerate) {
                        Text("Generate Summary")
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        MarkdownText(
                            markdown = summaryContent,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            linkColor = MaterialTheme.colorScheme.primary,
                            isTextSelectable = true
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Copy button with ContentCopy leading icon
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(
                                    Context.CLIPBOARD_SERVICE
                                ) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("Session Summary", summaryContent)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Copy")
                        }

                        // New Session button — triggers confirmation dialog
                        FilledTonalButton(
                            onClick = { showNewSessionDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("New Session")
                        }
                    }
                }
            }
        }
    }
}
