package com.meshvisualiser.ui.theme

import android.graphics.Matrix as AndroidMatrix
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(40.dp)
)

// Chat bubble shapes (WhatIfSheet messages)
val ChatBubbleUser = RoundedCornerShape(
    topStart = 20.dp, topEnd = 20.dp,
    bottomEnd = 4.dp, bottomStart = 20.dp
)
val ChatBubbleAi = RoundedCornerShape(
    topStart = 20.dp, topEnd = 20.dp,
    bottomEnd = 20.dp, bottomStart = 4.dp
)

// Input field shape
val PillShape = RoundedCornerShape(50)

// Status badge shape
val StatusBadgeShape = RoundedCornerShape(8.dp)

// ---------------------------------------------------------------------------
// M3 Expressive shape tokens for decorative UI elements
// Uses GenericShape + RoundedPolygon.toPath() since .toShape() is unavailable
// in the current androidx.graphics.shapes version on this project.
// ---------------------------------------------------------------------------

/**
 * Converts a [RoundedPolygon] to a Compose [Shape] by scaling the polygon's
 * normalised [−1, 1] coordinate space to fill the draw bounds.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun RoundedPolygon.asShape(): Shape = GenericShape { size, _ ->
    val polygon = this@asShape
    val m = AndroidMatrix()
    // RoundedPolygon is normalised to [−1, 1]; scale to the destination bounds.
    m.setScale(size.width / 2f, size.height / 2f)
    m.postTranslate(size.width / 2f, size.height / 2f)
    val androidPath = polygon.toPath().also { it.transform(m) }
    addPath(androidPath.asComposePath())
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val ScoreShape: Shape = MaterialShapes.Sunny.asShape()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val AiBadgeShape: Shape = MaterialShapes.Cookie6Sided.asShape()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val StepDiscoveringShape: Shape = MaterialShapes.Diamond.asShape()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val StepElectingShape: Shape = MaterialShapes.Pentagon.asShape()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val StepResolvingShape: Shape = MaterialShapes.Clover4Leaf.asShape()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val StepConnectedShape: Shape = MaterialShapes.Flower.asShape()
