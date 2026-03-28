package com.meshvisualiser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meshvisualiser.quiz.QuizState
import com.meshvisualiser.ui.theme.LogAck
import com.meshvisualiser.ui.theme.LogError
import com.meshvisualiser.ui.theme.ScoreShape

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuizOverlay(
    quizState: QuizState,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    onReplay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}  // Consume all touches on the scrim
            ),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
        ) {
            if (quizState.isFinished) {
                // Final score screen
                FinalScoreScreen(quizState, onClose, onReplay)
            } else {
                quizState.currentQuestion?.let { question ->
                    QuestionScreen(
                        questionIndex = quizState.currentIndex,
                        totalQuestions = quizState.questions.size,
                        questionText = question.text,
                        options = question.options,
                        correctIndex = question.correctIndex,
                        selectedAnswer = quizState.selectedAnswer,
                        isRevealed = quizState.isAnswerRevealed,
                        score = quizState.score,
                        timerSeconds = quizState.timerSecondsRemaining,
                        onAnswer = onAnswer,
                        onNext = onNext,
                        onClose = onClose
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuestionScreen(
    questionIndex: Int,
    totalQuestions: Int,
    questionText: String,
    options: List<String>,
    correctIndex: Int,
    selectedAnswer: Int?,
    isRevealed: Boolean,
    score: Int,
    timerSeconds: Int,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    val timerScale by animateFloatAsState(
        targetValue = if (timerSeconds <= 5 && !isRevealed) 1.2f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "timerPulse"
    )

    var showExitConfirm by remember { mutableStateOf(false) }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Quit quiz?") },
            text = { Text("Your progress will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    onClose()
                }) {
                    Text("Quit")
                }
            },
            dismissButton = {
                Button(onClick = { showExitConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(modifier = Modifier.padding(20.dp)) {
        // Header: close + progress + score + timer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { showExitConfirm = true }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Quit quiz",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Q${questionIndex + 1}/$totalQuestions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Score: $score",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            // Timer
            Box(
                modifier = Modifier.size(40.dp).scale(timerScale),
                contentAlignment = Alignment.Center
            ) {
                CircularWavyProgressIndicator(
                    progress = { timerSeconds / 30f },
                    modifier = Modifier.fillMaxSize(),
                    color = if (timerSeconds <= 10) LogError else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "$timerSeconds",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Progress bar
        LinearWavyProgressIndicator(
            progress = { (questionIndex + 1).toFloat() / totalQuestions },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Question
        Text(
            text = questionText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Options
        options.forEachIndexed { index, option ->
            val isSelected = selectedAnswer == index
            val isCorrect = index == correctIndex

            val buttonColor by animateColorAsState(
                targetValue = when {
                    !isRevealed -> {
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    }
                    isCorrect -> LogAck.copy(alpha = 0.3f)
                    isSelected -> LogError.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "optionColor"
            )

            val scale by animateFloatAsState(
                targetValue = if (isSelected && isRevealed) 1.02f else 1f,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "optionScale"
            )

            Surface(
                onClick = { if (!isRevealed) onAnswer(index) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .scale(scale),
                shape = MaterialTheme.shapes.small,
                color = buttonColor
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${'A' + index}.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (isRevealed) {
                        when {
                            isCorrect -> Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Correct answer",
                                tint = LogAck,
                                modifier = Modifier.size(20.dp)
                            )
                            isSelected -> Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Wrong answer",
                                tint = LogError,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Next / Close button
        if (isRevealed) {
            Button(
                onClick = onNext,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(if (questionIndex + 1 >= totalQuestions) "See Results" else "Next")
            }
        }
    }
}

@Composable
private fun FinalScoreScreen(quizState: QuizState, onClose: () -> Unit, onReplay: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Quiz Complete!",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        val percentage = if (quizState.questions.isNotEmpty())
            (quizState.score * 100) / quizState.questions.size else 0

        Box(
            modifier = Modifier
                .size(120.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, ScoreShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${quizState.score}/${quizState.questions.size}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val feedback = when {
            percentage >= 90 -> "Excellent! You really know your networking!"
            percentage >= 70 -> "Good job! Solid understanding."
            percentage >= 50 -> "Not bad! Keep learning."
            else -> "Keep studying — you'll get there!"
        }

        Text(
            text = feedback,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onReplay) {
                Text("Try Again")
            }
            Button(onClick = onClose) {
                Text("Close")
            }
        }
    }
}
