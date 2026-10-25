package com.pmbible.app.ui.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pmbible.app.data.db.InterviewAttemptEntity
import com.pmbible.app.ui.components.AppCard
import com.pmbible.app.ui.components.Callout
import com.pmbible.app.ui.components.Pill
import com.pmbible.app.ui.components.SectionHeader
import com.pmbible.app.ui.components.label
import com.pmbible.app.ui.theme.LocalExtraColors
import com.pmbible.core.domain.AnswerReview
import com.pmbible.core.model.Curriculum
import com.pmbible.core.model.InterviewQuestion
import com.pmbible.core.model.QuestionSourceType
import kotlinx.coroutines.delay

private val RATINGS = listOf("Not yet" to 0.0, "Partly" to 0.5, "Yes" to 1.0)

/**
 * Answer-first interview practice: write (optionally timed), get transparent keyword-based
 * structure checks, compare with a suggested approach, self-rate against the rubric, retry.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterviewPractice(
    question: InterviewQuestion,
    curriculum: Curriculum,
    attempts: List<InterviewAttemptEntity>,
    onSave: (answer: String, score: Double) -> Unit,
    onOpenLesson: (String) -> Unit,
    onFinished: (() -> Unit)? = null,
) {
    var answer by rememberSaveable(question.id) { mutableStateOf("") }
    var revealed by rememberSaveable(question.id) { mutableStateOf(false) }
    var saved by rememberSaveable(question.id) { mutableStateOf(false) }
    // -1 = not rated yet; a plain List keeps the state saveable across rotation and process death.
    var ratings by rememberSaveable(question.id) { mutableStateOf(List(question.rubric.size) { -1 }) }
    var timerOn by rememberSaveable(question.id) { mutableStateOf(false) }
    var seconds by rememberSaveable(question.id) { mutableIntStateOf(0) }
    val target = 5 * 60

    LaunchedEffect(timerOn, revealed) {
        while (timerOn && !revealed) {
            delay(1000)
            seconds++
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(question.category.label)
            Pill(question.level.label())
            question.company?.let { Pill(it) }
        }
        Callout(
            if (question.sourceType == QuestionSourceType.REPRESENTATIVE) "${question.sourceType.label}. It reflects the company's product domain for practice only."
            else question.sourceType.label,
        )
        Text(question.prompt, style = MaterialTheme.typography.titleMedium)
        attempts.firstOrNull()?.let {
            Text(
                "Previous attempts: ${attempts.size} · last self-score ${(it.score * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!revealed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = timerOn,
                    onClick = { timerOn = !timerOn },
                    label = { Text(if (timerOn) "Timer on" else "Practise timed (5 min)") },
                    leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                )
                if (timerOn) {
                    Spacer(Modifier.width(12.dp))
                    val over = seconds > target
                    Text(
                        "%d:%02d / 5:00".format(seconds / 60, seconds % 60),
                        color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it },
                label = { Text("Your answer — think aloud in writing") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
            )
            Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (answer.isBlank()) "Skip to the suggested approach" else "Get feedback")
            }
        } else {
            if (answer.isNotBlank()) {
                SectionHeader("Structure check")
                Text(
                    "A local keyword check of your answer's ingredients — not AI grading. Use it as a prompt for self-review.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(AnswerReview.lengthFeedback(answer), style = MaterialTheme.typography.bodyMedium)
                AnswerReview.review(answer, question).forEach { check ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (check.found) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (check.found) "Present" else "Not found",
                            tint = if (check.found) LocalExtraColors.current.success else MaterialTheme.colorScheme.outline,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(check.signal.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            SectionHeader("Suggested approach")
            question.approach.forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium) }
            SectionHeader("Common pitfalls")
            question.pitfalls.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
            SectionHeader("Likely follow-ups")
            question.followUps.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }

            SectionHeader("Rate yourself on the rubric")
            question.rubric.forEachIndexed { i, criterion ->
                AppCard {
                    Text(criterion, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RATINGS.forEachIndexed { r, (label, _) ->
                            FilterChip(selected = ratings[i] == r, onClick = { ratings = ratings.toMutableList().also { it[i] = r } }, label = { Text(label) })
                        }
                    }
                }
            }
            SectionHeader("Concepts this tests")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                question.lessonIds.mapNotNull { curriculum.lessonById[it] }.forEach { l ->
                    AssistChip(onClick = { onOpenLesson(l.id) }, label = { Text(l.title) })
                }
            }
            val allRated = ratings.none { it < 0 }
            if (!saved) {
                Button(
                    onClick = {
                        val score = ratings.sumOf { RATINGS[it].second } / ratings.size
                        onSave(answer, score)
                        saved = true
                    },
                    enabled = allRated,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(if (allRated) "Save attempt" else "Rate every criterion to save") }
            } else {
                Callout("Saved. Your competency for ${question.category.label} is updated in Progress.")
            }
            OutlinedButton(
                onClick = {
                    answer = ""; revealed = false; saved = false; seconds = 0
                    ratings = List(question.rubric.size) { -1 }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Retry with a fresh answer") }
            onFinished?.let { done ->
                Button(onClick = done, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Continue") }
            }
        }
    }
}
