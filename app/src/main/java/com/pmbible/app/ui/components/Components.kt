package com.pmbible.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pmbible.app.ui.theme.LocalExtraColors
import com.pmbible.core.domain.MasteryLevel
import com.pmbible.core.model.Bilingual
import com.pmbible.core.model.LanguageMode
import com.pmbible.core.model.Level
import com.pmbible.core.model.Question
import com.pmbible.core.model.QuestionKind
import com.pmbible.core.model.QuestionType

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = container)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, content = inner)
    else Card(modifier = modifier.fillMaxWidth(), colors = colors, content = inner)
}

@Composable
fun Pill(text: String, container: Color = MaterialTheme.colorScheme.secondaryContainer, content: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.small) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

fun Level.label() = name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun MasteryPill(level: MasteryLevel) {
    val extra = LocalExtraColors.current
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (level) {
        MasteryLevel.NOT_STARTED -> scheme.surfaceVariant to scheme.onSurfaceVariant
        MasteryLevel.LEARNING -> scheme.secondaryContainer to scheme.onSecondaryContainer
        MasteryLevel.FAMILIAR -> scheme.primaryContainer to scheme.onPrimaryContainer
        MasteryLevel.PROFICIENT, MasteryLevel.MASTERED -> extra.successContainer to extra.onSuccessContainer
    }
    Pill(level.label, bg, fg)
}

@Composable
fun LabeledProgress(label: String, fraction: Float, trailing: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp))
    }
}

/** Shows text in the learner's language mode; Thanglish falls back to English when missing. */
@Composable
fun BilingualText(text: Bilingual, mode: LanguageMode, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge) {
    val showEn = mode != LanguageMode.THANGLISH || text.ta.isBlank()
    val showTa = mode != LanguageMode.ENGLISH && text.ta.isNotBlank()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (showEn) Text(text.en, style = style)
        if (showTa) {
            if (showEn) Text("Thanglish", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(text.ta, style = style)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: Pair<String, () -> Unit>? = null) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.let { (label, onClick) -> OutlinedButton(onClick = onClick) { Text(label) } }
    }
}

@Composable
fun LoadingState(message: String = "Loading…") {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(Icons.Outlined.ErrorOutline, "Something went wrong", message, "Try again" to onRetry)
    }
}

@Composable
fun Callout(text: String, icon: ImageVector = Icons.Outlined.Info) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

fun formatMinuteOfDay(minute: Int): String {
    val h = minute / 60
    val m = minute % 60
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(initialMinute: Int, title: String, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * A single question: choose, check, then see why each option is right or wrong.
 * [allowGuessFlag] lets the learner mark a correct answer as a guess, so it doesn't count toward mastery.
 */
@Composable
fun QuestionCard(
    question: Question,
    allowGuessFlag: Boolean,
    /** Called once, when the learner moves on, with the final correctness and confidence. */
    onDone: (correct: Boolean, confident: Boolean) -> Unit,
    continueLabel: String = "Continue",
) {
    var selected by rememberSaveable(question.id) { mutableStateOf(setOf<Int>()) }
    var submitted by rememberSaveable(question.id) { mutableStateOf(false) }
    var guessed by rememberSaveable(question.id) { mutableStateOf(false) }
    val correctSet = question.options.withIndex().filter { it.value.correct }.map { it.index }.toSet()
    val isCorrect = selected == correctSet
    val extra = LocalExtraColors.current
    val scheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Pill(if (question.kind == QuestionKind.APPLICATION) "Apply it" else "Recall")
        Text(question.prompt, style = MaterialTheme.typography.titleMedium)
        if (question.type == QuestionType.MULTI) {
            Text("Select all that apply", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
        question.options.forEachIndexed { i, option ->
            val chosen = i in selected
            val border = when {
                !submitted -> if (chosen) scheme.primary else scheme.outlineVariant
                option.correct -> extra.success
                chosen -> scheme.error
                else -> scheme.outlineVariant
            }
            val toggle = {
                if (!submitted) selected = if (question.type == QuestionType.SINGLE) setOf(i) else if (chosen) selected - i else selected + i
            }
            Surface(
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(if (chosen || (submitted && option.correct)) 2.dp else 1.dp, border),
                color = scheme.surface,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).then(
                    if (question.type == QuestionType.SINGLE) Modifier.selectable(chosen, enabled = !submitted, role = Role.RadioButton, onClick = toggle)
                    else Modifier.toggleable(chosen, enabled = !submitted, role = Role.Checkbox, onValueChange = { toggle() }),
                ),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (question.type == QuestionType.SINGLE) RadioButton(selected = chosen, onClick = null)
                        else Checkbox(checked = chosen, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(option.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                    if (submitted && (chosen || option.correct)) {
                        Text(
                            option.why,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (option.correct) extra.success else scheme.error,
                            modifier = Modifier.padding(start = 40.dp, top = 4.dp),
                        )
                    }
                }
            }
        }
        if (!submitted) {
            Button(
                onClick = { submitted = true },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text("Check answer") }
        } else {
            Surface(color = if (isCorrect) extra.successContainer else scheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isCorrect) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = if (isCorrect) extra.onSuccessContainer else scheme.onErrorContainer,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isCorrect) "Correct" else "Not quite",
                            fontWeight = FontWeight.SemiBold,
                            color = if (isCorrect) extra.onSuccessContainer else scheme.onErrorContainer,
                        )
                    }
                    Text(question.explanation, color = if (isCorrect) extra.onSuccessContainer else scheme.onErrorContainer)
                }
            }
            if (allowGuessFlag && isCorrect) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(guessed, role = Role.Checkbox) { guessed = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = guessed, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text("I guessed — show this again sooner", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(onClick = { onDone(isCorrect, !guessed) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(continueLabel) }
        }
    }
}
