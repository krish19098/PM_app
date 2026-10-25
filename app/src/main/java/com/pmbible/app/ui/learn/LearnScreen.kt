package com.pmbible.app.ui.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pmbible.app.ui.AppViewModel
import com.pmbible.app.ui.components.AppCard
import com.pmbible.app.ui.components.Callout
import com.pmbible.app.ui.components.LabeledProgress
import com.pmbible.app.ui.components.MasteryPill
import com.pmbible.app.ui.components.Pill
import com.pmbible.app.ui.components.ScreenScaffold
import com.pmbible.app.ui.components.label
import com.pmbible.app.ui.theme.LocalExtraColors
import com.pmbible.core.domain.Mastery
import com.pmbible.core.model.Curriculum
import com.pmbible.core.model.CurriculumUnit
import com.pmbible.core.model.ProgressSnapshot

/** The learning path: units in order, each lesson's status and mastery, and the unit challenge. */
@Composable
fun LearnScreen(app: AppViewModel, onOpenLesson: (String) -> Unit, onStartChallenge: (String) -> Unit) {
    val library by app.library.collectAsState()
    val snapshot by app.snapshot.collectAsState()
    val c = library?.curriculum ?: return
    val today = app.today()
    val completed = c.lessons.count { snapshot.lesson(it.id).completed }

    ScreenScaffold("Learning path") {
        item {
            AppCard {
                LabeledProgress("Lessons completed", completed / c.lessons.size.toFloat(), "$completed / ${c.lessons.size}")
                Text(c.manifest.coverageNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Callout("Completing a lesson isn't the same as mastering it. Mastery grows as you answer spaced reviews correctly and apply concepts in exercises, cases and interviews.") }
        c.units.forEach { unit ->
            item(key = unit.id) { UnitCard(unit, c, snapshot, today, onOpenLesson, onStartChallenge) }
        }
    }
}

@Composable
private fun UnitCard(
    unit: CurriculumUnit,
    c: Curriculum,
    p: ProgressSnapshot,
    today: Long,
    onOpenLesson: (String) -> Unit,
    onStartChallenge: (String) -> Unit,
) {
    val lessons = c.lessonsByUnit[unit.id].orEmpty()
    val done = lessons.count { p.lesson(it.id).completed }
    val started = lessons.any { p.lesson(it.id).let { s -> s.completed || s.inProgress } }
    var open by rememberSaveable(unit.id) { mutableStateOf(started && done < lessons.size) }
    val mastery = Mastery.unitScore(c, unit.id, p, today)
    val extra = LocalExtraColors.current

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Unit ${unit.order}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(unit.title, style = MaterialTheme.typography.titleMedium)
            }
            Pill(unit.level.label())
            IconButton(onClick = { open = !open }) {
                Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) "Collapse unit" else "Expand unit")
            }
        }
        Text(unit.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LabeledProgress("$done of ${lessons.size} lessons", done / lessons.size.toFloat(), "Mastery ${(mastery * 100).toInt()}%")
        if (open) {
            HorizontalDivider()
            lessons.forEach { lesson ->
                val state = p.lesson(lesson.id)
                val locked = !state.completed && lesson.prerequisites.any { !p.lesson(it).completed }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpenLesson(lesson.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val (icon, tint, desc) = when {
                        state.completed -> Triple(Icons.Outlined.CheckCircle, extra.success, "Completed")
                        state.inProgress -> Triple(Icons.Outlined.PlayCircle, MaterialTheme.colorScheme.primary, "In progress")
                        locked -> Triple(Icons.Outlined.Lock, MaterialTheme.colorScheme.outline, "Prerequisites not yet completed")
                        else -> Triple(Icons.Outlined.RadioButtonUnchecked, MaterialTheme.colorScheme.outline, "Not started")
                    }
                    Icon(icon, contentDescription = desc, tint = tint)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(lesson.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (state.placed) "Placed — confirm with reviews" else "${lesson.quickMinutes + lesson.deepMinutes} min · ${lesson.level.label()}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.completed) MasteryPill(Mastery.level(lesson, p, today))
                }
            }
            val allDone = done == lessons.size
            val cpDone = unit.id in p.checkpointsDone
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Unit challenge: ${c.checkpoints[unit.id]?.title}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when {
                            cpDone -> "Completed — you can retake it any time"
                            allDone -> "Ready — combines every concept in this unit"
                            else -> "Unlocks after all lessons in this unit"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(onClick = { onStartChallenge(unit.id) }, enabled = allDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (cpDone) "Retake challenge" else "Start challenge")
            }
        }
    }
}
