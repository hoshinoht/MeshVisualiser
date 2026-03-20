package com.meshvisualiser.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
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
