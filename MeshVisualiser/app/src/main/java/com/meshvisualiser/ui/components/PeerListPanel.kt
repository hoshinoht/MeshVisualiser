package com.meshvisualiser.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshvisualiser.models.PeerInfo
import com.meshvisualiser.ui.theme.StatusConnected

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PeerListPanel(
    peers: Map<String, PeerInfo>,
    selectedPeerId: Long?,
    onSelectPeer: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    val validPeers = peers.values.filter { it.hasValidPeerId }

    GlassSurface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Peers (${validPeers.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec()
                ) + fadeIn()
            ) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    validPeers.forEach { peer ->
                        val isSelected = peer.peerId == selectedPeerId
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = peer.peerId.toString().takeLast(6),
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            supportingContent = {
                                Text(peer.deviceModel.ifEmpty { "Unknown" })
                            },
                            leadingContent = {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(StatusConnected)
                                )
                            },
                            trailingContent = if (isSelected) {
                                {
                                    Text(
                                        text = "TARGET",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else null,
                            colors = ListItemDefaults.colors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            ),
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable {
                                    onSelectPeer(if (isSelected) null else peer.peerId)
                                }
                        )
                    }
                }
            }
        }
    }
}
