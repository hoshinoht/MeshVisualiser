package com.meshvisualiser.ui.components

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.graphics.shapes.toPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.util.lerp
import com.meshvisualiser.models.PeerInfo
import com.meshvisualiser.ui.DataLogEntry
import com.meshvisualiser.ui.PacketAnimEvent
import com.meshvisualiser.ui.theme.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

private data class ActivePacketAnim(
    val eventId: Long,
    val fromId: Long,
    val toId: Long,
    val type: String,
    val progress: Animatable<Float, AnimationVector1D>
)

/**
 * Full-screen animated mesh graph visualization using M3 Expressive motion tokens.
 * Replaces the AR camera view with a Canvas-based topology that animates
 * node entry, leader glow, and packet flow.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MeshVisualizationView(
    localId: Long,
    peers: Map<String, PeerInfo>,
    leaderId: Long,
    peerRttHistory: Map<Long, List<Long>>,
    dataLogs: List<DataLogEntry>,
    packetAnimEvents: List<PacketAnimEvent>,
    onEventConsumed: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val validPeers = remember(peers) { peers.values.filter { it.hasValidPeerId } }
    val isLocalLeader = leaderId == localId

    // Build stable list of all node IDs (leader + followers).
    // Sorted so the list identity doesn't change when leaderId flips
    // (which would restart LaunchedEffect and cancel in-flight animations).
    val allNodeIds = remember(leaderId, peers) {
        val ids = mutableSetOf<Long>()
        if (leaderId > 0) ids.add(leaderId)
        ids.add(localId)
        validPeers.forEach { ids.add(it.peerId) }
        ids.sorted()
    }

    // --- Node entry animation (M3 expressive bounce) ---
    val nodeScales = remember { mutableStateMapOf<Long, Animatable<Float, AnimationVector1D>>() }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(allNodeIds) {
        allNodeIds.forEach { id ->
            if (id !in nodeScales) {
                nodeScales[id] = Animatable(0f)
            }
            // Always launch: covers both new entries and animations
            // that were cancelled by a prior LaunchedEffect restart.
            val anim = nodeScales[id]!!
            if (anim.value < 1f) {
                launch { anim.animateTo(1f, spatialSpec) }
            }
        }
        // Clean up removed nodes
        val toRemove = nodeScales.keys - allNodeIds.toSet()
        toRemove.forEach { nodeScales.remove(it) }
    }

    // --- Leader glow pulse (slow spatial spring) ---
    val glowRadius = remember { Animatable(48f) }
    val slowSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

    LaunchedEffect(leaderId) {
        if (leaderId > 0) {
            while (true) {
                glowRadius.animateTo(64f, slowSpec)
                glowRadius.animateTo(48f, slowSpec)
            }
        }
    }

    // --- Edge appearance alpha ---
    val edgeAlpha = remember { Animatable(0f) }
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    LaunchedEffect(allNodeIds.size) {
        if (allNodeIds.size > 1) {
            edgeAlpha.animateTo(1f, effectsSpec)
        }
    }

    // --- Node receive pulse (quick scale bump when a packet arrives) ---
    val nodePulses = remember { mutableStateMapOf<Long, Animatable<Float, AnimationVector1D>>() }

    // --- Packet flow animations (SLOW so users can follow) ---
    val activeAnims = remember { mutableStateListOf<ActivePacketAnim>() }
    val scope = rememberCoroutineScope()

    // Packet travel: 1500ms ease-in-out — slow enough to clearly see the dot travel
    val packetTravelSpec = tween<Float>(durationMillis = 1500, easing = FastOutSlowInEasing)
    // Drop travel: 2000ms — even slower so users notice the stop + fade
    val packetDropSpec = tween<Float>(durationMillis = 2000, easing = LinearEasing)
    val pulseSpec = spatialSpec // reuse the bouncy default spatial for pulse

    LaunchedEffect(packetAnimEvents) {
        packetAnimEvents.forEach { event ->
            if (activeAnims.none { it.eventId == event.id } && activeAnims.size < 20) {
                val anim = ActivePacketAnim(
                    event.id, event.fromId, event.toId,
                    event.type, Animatable(0f)
                )
                activeAnims.add(anim)
                scope.launch {
                    val spec = if (anim.type == "DROP") packetDropSpec else packetTravelSpec
                    anim.progress.animateTo(1f, spec)
                    activeAnims.remove(anim)

                    // Pulse the destination node (skip for drops)
                    if (anim.type != "DROP") {
                        val targetId = anim.toId
                        val pulse = nodePulses.getOrPut(targetId) { Animatable(0f) }
                        launch {
                            pulse.snapTo(1f)
                            pulse.animateTo(0f, pulseSpec)
                        }
                    }

                    onEventConsumed(event.id)
                }
            }
        }
    }

    // Seeded random scatter dot positions (stable across recompose)
    val scatterDots = remember {
        val rng = Random(42)
        List(22) {
            Triple(
                rng.nextFloat(),  // x fraction
                rng.nextFloat(),  // y fraction
                rng.nextFloat() * 4f + 3f  // radius 3-7
            )
        }
    }
    val scatterColors = remember {
        listOf(MeshAccent1, MeshAccent2, MeshAccent3, MeshAccent4)
    }

    // Colors — read from theme outside Canvas to avoid re-reading on every frame
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val leaderColor = StatusLeader
    val connectedColor = StatusConnected

    // Pre-cached Paint objects — allocated once, color updated per-frame inside Canvas
    val labelPaint = remember {
        Paint().apply {
            textSize = 38f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }
    val rolePaint = remember {
        Paint().apply {
            textSize = 26f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val rttPaint = remember {
        Paint().apply {
            textSize = 24f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }
    }
    val subtitlePaint = remember {
        Paint().apply {
            textSize = 28f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val packetLabelPaint = remember {
        Paint().apply {
            textSize = 22f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }
    val innerTextPaint = remember {
        Paint().apply {
            textSize = 30f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
    }

    // Pre-cached Matrix and Path objects for background blobs — reused every frame
    val blobMatrix1 = remember { Matrix() }
    val blobMatrix2 = remember { Matrix() }
    val blobMatrix3 = remember { Matrix() }
    val blobMatrix4 = remember { Matrix() }

    // Pre-allocated objects reused every frame to reduce GC pressure
    val curvePath1Ref = remember { androidx.compose.ui.graphics.Path() }
    val curvePath2Ref = remember { androidx.compose.ui.graphics.Path() }
    val nodePositionsRef = remember { mutableMapOf<Long, Offset>() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height * 0.38f
        val graphRadius = minOf(size.width, size.height) * 0.28f

        // Update theme-dependent paint colors each frame (cheap — no allocation)
        labelPaint.color = onSurfaceColor.toArgb()
        rolePaint.color = onSurfaceVariantColor.copy(alpha = 0.8f).toArgb()
        rttPaint.color = onSurfaceVariantColor.copy(alpha = 0.7f).toArgb()
        subtitlePaint.color = onSurfaceVariantColor.toArgb()
        innerTextPaint.color = onSurfaceColor.toArgb()

        // =============================================
        // 1. BACKGROUND — organic blobs + scatter dots
        // =============================================

        // Base fill
        val maxDim = maxOf(size.width, size.height)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(surfaceContainerColor, surfaceColor),
                center = Offset(centerX, centerY),
                radius = maxDim * 0.7f
            )
        )

        // Decorative M3 shape blobs using MaterialShapes
        // Top-left: Cookie shape
        blobMatrix1.apply {
            reset()
            setScale(size.width * 0.28f, size.width * 0.28f)
            postTranslate(size.width * 0.12f, size.height * 0.10f)
        }
        val blob1 = MaterialShapes.Cookie6Sided.toPath().also { it.transform(blobMatrix1) }.asComposePath()
        drawPath(blob1, color = primaryColor.copy(alpha = 0.06f))

        // Bottom-right: Clover shape
        blobMatrix2.apply {
            reset()
            setScale(size.width * 0.32f, size.width * 0.32f)
            postTranslate(size.width * 0.85f, size.height * 0.72f)
        }
        val blob2 = MaterialShapes.Clover4Leaf.toPath().also { it.transform(blobMatrix2) }.asComposePath()
        drawPath(blob2, color = tertiaryColor.copy(alpha = 0.05f))

        // Center-right: SoftBurst shape
        blobMatrix3.apply {
            reset()
            setScale(size.width * 0.22f, size.width * 0.22f)
            postTranslate(size.width * 0.78f, size.height * 0.25f)
        }
        val blob3 = MaterialShapes.SoftBurst.toPath().also { it.transform(blobMatrix3) }.asComposePath()
        drawPath(blob3, color = secondaryColor.copy(alpha = 0.05f))

        // Bottom-left: Flower shape
        blobMatrix4.apply {
            reset()
            setScale(size.width * 0.20f, size.width * 0.20f)
            postTranslate(size.width * 0.18f, size.height * 0.68f)
        }
        val blob4 = MaterialShapes.Flower.toPath().also { it.transform(blobMatrix4) }.asComposePath()
        drawPath(blob4, color = primaryColor.copy(alpha = 0.04f))

        // Scatter dots
        scatterDots.forEachIndexed { i, (xFrac, yFrac, radius) ->
            val color = scatterColors[i % scatterColors.size]
            val alpha = 0.15f + (i % 3) * 0.05f
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = Offset(xFrac * size.width, yFrac * size.height)
            )
        }

        // Flowing bezier curves (reuse paths to avoid per-frame allocations)
        val curvePath1 = curvePath1Ref.apply { reset() }.apply {
            moveTo(0f, size.height * 0.3f)
            cubicTo(
                size.width * 0.25f, size.height * 0.15f,
                size.width * 0.65f, size.height * 0.45f,
                size.width, size.height * 0.28f
            )
        }
        drawPath(
            curvePath1,
            color = outlineColor.copy(alpha = 0.08f),
            style = Stroke(width = 1.2f, cap = StrokeCap.Round)
        )

        val curvePath2 = curvePath2Ref.apply { reset() }.apply {
            moveTo(size.width * 0.1f, size.height * 0.85f)
            cubicTo(
                size.width * 0.4f, size.height * 0.6f,
                size.width * 0.7f, size.height * 0.75f,
                size.width * 0.95f, size.height * 0.55f
            )
        }
        drawPath(
            curvePath2,
            color = outlineColor.copy(alpha = 0.06f),
            style = Stroke(width = 1f, cap = StrokeCap.Round)
        )

        // ============================
        // 2. BUILD NODE POSITIONS
        // ============================
        nodePositionsRef.clear()
        val nodePositions = nodePositionsRef

        val effectiveLeaderId = if (leaderId > 0) leaderId else localId
        nodePositions[effectiveLeaderId] = Offset(centerX, centerY)

        val followers = allNodeIds.filter { it != effectiveLeaderId }
        followers.forEachIndexed { index, peerId ->
            val angle = 2.0 * PI * index / maxOf(followers.size, 1) - PI / 2.0
            val x = centerX + graphRadius * cos(angle).toFloat()
            val y = centerY + graphRadius * sin(angle).toFloat()
            nodePositions[peerId] = Offset(x, y)
        }

        // Paints are pre-cached via remember {} above Canvas — colors updated at top of Canvas block

        // =============================================
        // 4. EDGES — mesh connections + junction dots
        // =============================================
        val leaderPos = nodePositions[effectiveLeaderId] ?: Offset(centerX, centerY)
        val currentEdgeAlpha = edgeAlpha.value

        // Primary edges: leader ↔ each follower
        followers.forEach { peerId ->
            val peerPos = nodePositions[peerId] ?: return@forEach
            val avgRtt = peerRttHistory[peerId]?.takeIf { it.isNotEmpty() }?.average()?.toLong()
            val edgeColor = when {
                avgRtt == null -> connectedColor
                avgRtt < 50 -> TopologyExcellent
                avgRtt < 150 -> TopologyGood
                else -> TopologyPoor
            }

            // Glow line behind
            drawLine(
                color = edgeColor.copy(alpha = currentEdgeAlpha * 0.12f),
                start = leaderPos,
                end = peerPos,
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
            // Main edge
            drawLine(
                color = edgeColor.copy(alpha = currentEdgeAlpha * 0.7f),
                start = leaderPos,
                end = peerPos,
                strokeWidth = 2.5f,
                cap = StrokeCap.Round
            )

            // Junction dot at midpoint
            val mid = Offset(
                (leaderPos.x + peerPos.x) / 2f,
                (leaderPos.y + peerPos.y) / 2f
            )
            drawCircle(
                color = edgeColor.copy(alpha = currentEdgeAlpha * 0.6f),
                radius = 5f,
                center = mid
            )

            // RTT label at midpoint
            if (avgRtt != null) {
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.4f),
                    topLeft = Offset(mid.x - 42f, mid.y - 22f),
                    size = androidx.compose.ui.geometry.Size(84f, 30f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f)
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "${avgRtt}ms",
                    mid.x,
                    mid.y,
                    rttPaint
                )
            }
        }

        // Secondary edges: follower ↔ follower (dashed mesh web)
        if (followers.size >= 2) {
            for (i in followers.indices) {
                val j = (i + 1) % followers.size
                val posA = nodePositions[followers[i]] ?: continue
                val posB = nodePositions[followers[j]] ?: continue

                // Dashed line
                drawLine(
                    color = outlineColor.copy(alpha = currentEdgeAlpha * 0.40f),
                    start = posA,
                    end = posB,
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )

                // Junction dot at midpoint
                val meshMid = Offset(
                    (posA.x + posB.x) / 2f,
                    (posA.y + posB.y) / 2f
                )
                drawCircle(
                    color = outlineColor.copy(alpha = currentEdgeAlpha * 0.35f),
                    radius = 4f,
                    center = meshMid
                )
            }
        }

        // =============================================
        // 5. NODES — double-ring with M3 shape accents
        // =============================================
        val leaderNodeRadius = 52f
        val followerNodeRadius = 40f

        allNodeIds.forEach { nodeId ->
            val pos = nodePositions[nodeId] ?: return@forEach
            val scale = nodeScales[nodeId]?.value ?: 0f
            val isLeaderNode = nodeId == effectiveLeaderId
            val isLocal = nodeId == localId
            val pulseValue = nodePulses[nodeId]?.value ?: 0f

            val baseRadius = if (isLeaderNode) leaderNodeRadius else followerNodeRadius
            val scaledRadius = baseRadius * scale * (1f + pulseValue * 0.2f)
            val ringStroke = if (isLeaderNode) 5f else 4f

            val nodeColor = when {
                isLeaderNode -> leaderColor
                isLocal -> primaryColor
                else -> connectedColor
            }

            // Leader glow — radial brush behind the outer ring
            val glowDrawRadius = glowRadius.value * 1.8f * scale
            if (isLeaderNode && glowDrawRadius > 1f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            leaderColor.copy(alpha = 0.18f * scale),
                            leaderColor.copy(alpha = 0.06f * scale),
                            Color.Transparent
                        ),
                        center = pos,
                        radius = glowDrawRadius
                    ),
                    radius = glowDrawRadius,
                    center = pos
                )

                // Decorative M3 shape halo (Sunny shape behind leader)
                if (scaledRadius > 10f) {
                    val haloMatrix = Matrix().apply {
                        val haloScale = scaledRadius * 1.9f
                        setScale(haloScale, haloScale)
                        postTranslate(pos.x, pos.y)
                    }
                    val haloPath = MaterialShapes.Sunny.toPath().also { it.transform(haloMatrix) }.asComposePath()
                    drawPath(
                        haloPath,
                        color = leaderColor.copy(alpha = 0.10f * scale),
                        style = Stroke(width = 1.5f)
                    )
                }
            }

            // Outer decorative ring (leader only)
            if (isLeaderNode && scale > 0.3f) {
                drawCircle(
                    color = nodeColor.copy(alpha = 0.30f * scale),
                    radius = scaledRadius + 8f,
                    center = pos,
                    style = Stroke(width = 1.5f)
                )
            }

            // Main ring (thick stroke)
            drawCircle(
                color = nodeColor.copy(alpha = scale),
                radius = scaledRadius,
                center = pos,
                style = Stroke(width = ringStroke)
            )

            // Inner fill (dark semi-transparent)
            val innerRadius = scaledRadius - ringStroke / 2f
            if (innerRadius > 0f) {
                drawCircle(
                    color = surfaceColor.copy(alpha = 0.85f * scale),
                    radius = innerRadius,
                    center = pos
                )
            }

            // Receive pulse ring — expanding ring that fades out
            if (pulseValue > 0.01f) {
                val ringRadius = scaledRadius + 20f * (1f - pulseValue)
                drawCircle(
                    color = nodeColor.copy(alpha = pulseValue * 0.6f),
                    radius = ringRadius,
                    center = pos,
                    style = Stroke(width = 3f)
                )
            }

            // Center content
            if (scale > 0.3f) {
                val centerText = when {
                    isLeaderNode -> "\u2605" // ★
                    isLocal -> "Me"
                    else -> {
                        validPeers.find { it.peerId == nodeId }?.deviceModel
                            ?.firstOrNull()?.uppercase()
                            ?: "?"
                    }
                }
                innerTextPaint.color = nodeColor.copy(alpha = scale).toArgb()
                // Adjust text size for single char vs "Me"
                innerTextPaint.textSize = if (centerText.length <= 1) 34f else 28f
                drawContext.canvas.nativeCanvas.drawText(
                    centerText,
                    pos.x,
                    pos.y + 11f, // vertical centering
                    innerTextPaint
                )
            }

            // Labels below node
            if (scale > 0.5f) {
                val label = when {
                    isLocal -> "Me"
                    else -> {
                        validPeers.find { it.peerId == nodeId }?.deviceModel
                            ?.takeIf { it.isNotEmpty() }
                            ?: nodeId.toString().takeLast(4)
                    }
                }
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    pos.x,
                    pos.y + scaledRadius + 34f,
                    labelPaint
                )

                val role = when {
                    isLeaderNode && isLocal -> "Leader (You)"
                    isLeaderNode -> "\u2605 Leader"
                    isLocal -> "You"
                    else -> "Follower"
                }
                drawContext.canvas.nativeCanvas.drawText(
                    role,
                    pos.x,
                    pos.y + scaledRadius + 60f,
                    rolePaint
                )
            }
        }

        // =============================================
        // 6. PACKET ANIMATIONS — kept as-is
        // =============================================
        val packetDotRadius = 14f
        val packetGlowRadius = 26f

        activeAnims.forEach { anim ->
            val from = nodePositions[anim.fromId] ?: return@forEach
            val to = nodePositions[anim.toId] ?: return@forEach
            val t = anim.progress.value.coerceIn(0f, 1f)

            val dotColor = when (anim.type) {
                "TCP" -> PacketTcp
                "UDP" -> PacketUdp
                "ACK" -> PacketAck
                else -> PacketDrop
            }

            if (anim.type == "DROP") {
                val mt = minOf(t * 2f, 1f)
                val alpha = if (t < 0.5f) 1f else 1f - (t - 0.5f) * 2f
                val midpoint = Offset(
                    lerp(from.x, to.x, 0.5f),
                    lerp(from.y, to.y, 0.5f)
                )
                val pos = Offset(
                    lerp(from.x, midpoint.x, mt),
                    lerp(from.y, midpoint.y, mt)
                )
                drawCircle(dotColor.copy(alpha = alpha * 0.25f), radius = packetGlowRadius, center = pos)
                drawCircle(dotColor.copy(alpha = alpha), radius = packetDotRadius, center = pos)
                if (t >= 0.5f) {
                    val crossAlpha = (t - 0.5f) * 2f
                    val crossSize = 10f
                    drawLine(
                        Color.White.copy(alpha = crossAlpha * alpha),
                        Offset(pos.x - crossSize, pos.y - crossSize),
                        Offset(pos.x + crossSize, pos.y + crossSize),
                        strokeWidth = 3f
                    )
                    drawLine(
                        Color.White.copy(alpha = crossAlpha * alpha),
                        Offset(pos.x + crossSize, pos.y - crossSize),
                        Offset(pos.x - crossSize, pos.y + crossSize),
                        strokeWidth = 3f
                    )
                }
                if (alpha > 0.3f) {
                    packetLabelPaint.color = dotColor.copy(alpha = alpha).toArgb()
                    drawContext.canvas.nativeCanvas.drawText(
                        "DROP",
                        pos.x,
                        pos.y - packetGlowRadius - 6f,
                        packetLabelPaint
                    )
                }
            } else {
                val pos = Offset(lerp(from.x, to.x, t), lerp(from.y, to.y, t))

                val trailStart = Offset(
                    lerp(from.x, to.x, maxOf(t - 0.15f, 0f)),
                    lerp(from.y, to.y, maxOf(t - 0.15f, 0f))
                )
                drawLine(
                    color = dotColor.copy(alpha = 0.3f),
                    start = trailStart,
                    end = pos,
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )

                drawCircle(dotColor.copy(alpha = 0.25f), radius = packetGlowRadius, center = pos)
                drawCircle(dotColor, radius = packetDotRadius, center = pos)
                drawCircle(Color.White.copy(alpha = 0.4f), radius = 5f, center = pos)

                packetLabelPaint.color = dotColor.toArgb()
                drawContext.canvas.nativeCanvas.drawText(
                    anim.type,
                    pos.x,
                    pos.y - packetGlowRadius - 6f,
                    packetLabelPaint
                )
            }
        }

        // --- "Waiting for peers" text when alone ---
        if (validPeers.isEmpty()) {
            // Dashed orbit ring using M3 shape instead of circle
            val orbitMatrix = Matrix().apply {
                setScale(graphRadius, graphRadius)
                postTranslate(centerX, centerY)
            }
            val orbitPath = MaterialShapes.Cookie9Sided.toPath().also { it.transform(orbitMatrix) }.asComposePath()
            drawPath(
                orbitPath,
                color = outlineColor.copy(alpha = 0.25f),
                style = Stroke(
                    width = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
                )
            )
            drawContext.canvas.nativeCanvas.drawText(
                "Waiting for peers...",
                centerX,
                centerY + graphRadius + 48f,
                subtitlePaint
            )
        }

        // Topology label
        val topoLabel = when {
            validPeers.isEmpty() -> "No peers"
            validPeers.size == 1 -> "Point-to-Point"
            else -> "Star Topology"
        }
        drawContext.canvas.nativeCanvas.drawText(
            topoLabel,
            centerX,
            size.height - 32f,
            subtitlePaint
        )
    }
}
