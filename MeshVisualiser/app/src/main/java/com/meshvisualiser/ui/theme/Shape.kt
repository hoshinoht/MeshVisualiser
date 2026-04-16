package com.meshvisualiser.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Shapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

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
// Uses the official M3E RoundedPolygon.toShape() composable extension.
// ---------------------------------------------------------------------------

val ScoreShape: Shape
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable get() = MaterialShapes.Sunny.toShape()

val AiBadgeShape: Shape
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable get() = MaterialShapes.Cookie6Sided.toShape()

val StepDiscoveringShape: Shape
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable get() = MaterialShapes.Diamond.toShape()

val StepElectingShape: Shape
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable get() = MaterialShapes.Pentagon.toShape()

val StepResolvingShape: Shape
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable get() = MaterialShapes.Clover4Leaf.toShape()

val StepConnectedShape: Shape
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable get() = MaterialShapes.Flower.toShape()
