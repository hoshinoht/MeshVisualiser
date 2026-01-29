package com.example.meshvisualiser.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.meshvisualiser.quiz.QuizState
import com.example.meshvisualiser.ui.theme.LogAck
import com.example.meshvisualiser.ui.theme.LogError

@Composable
fun QuizOverlay(
    quizState: QuizState,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            if (quizState.isFinished) {
                // Final score screen
                FinalScoreScreen(quizState, onClose)
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
    Column(modifier = Modifier.padding(20.dp)) {
        // Header: progress + score + timer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { timerSeconds / 30f },
                    modifier = Modifier.fillMaxSize(),
                    color = if (timerSeconds <= 10) LogError else MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
                Text(
                    text = "$timerSeconds",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Progress bar
        LinearProgressIndicator(
            progress = { (questionIndex + 1).toFloat() / totalQuestions },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(4.dp),
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
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "optionColor"
            )

            val scale by animateFloatAsState(
                targetValue = if (isSelected && isRevealed) 1.02f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
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
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
private fun FinalScoreScreen(quizState: QuizState, onClose: () -> Unit) {
    Column(
        modifier = Modifier.padding(24.dp),
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

        Text(
            text = "${quizState.score} / ${quizState.questions.size}",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "$percentage%",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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

        Button(onClick = onClose) {
            Text("Close")
        }
    }
}
