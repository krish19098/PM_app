package com.pmbible.app.ui.today

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Cases
import androidx.compose.material.icons.outlined.EmojiObjects
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pmbible.app.ui.AppViewModel
import com.pmbible.app.ui.components.AppCard
import com.pmbible.app.ui.components.ScreenScaffold
import com.pmbible.app.ui.session.SessionMode
import com.pmbible.core.domain.Accountability
import com.pmbible.core.domain.Mastery
import com.pmbible.core.domain.NudgeKind
import com.pmbible.core.domain.PlanItem
import com.pmbible.core.domain.SessionPlanner
import com.pmbible.core.model.Curriculum
import com.pmbible.core.model.Segment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

private val MINUTE_OPTIONS = listOf(5, 10, 15, 30, 45, 60)

/** Interests chosen in onboarding map to a starting unit (or the interview studio). */
private val INTEREST_UNITS = mapOf(
    "AI PM" to "u13", "Analytics" to "u09", "Strategy" to "u04", "Growth" to "u11", "Discovery" to "u02", "UX" to "u08",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    app: AppViewModel,
    onStart: (SessionMode, String?, Int?) -> Unit,
    onOpenLesson: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenInterviews: () -> Unit,
) {
    val library by app.library.collectAsState()
    val prefsState by app.prefs.collectAsState()
    val snapshot by app.snapshot.collectAsState()
    val chosen by app.todayMinutes.collectAsState()
    val interviewAttempts by app.interviewAttempts.collectAsState()
    val c = library?.curriculum ?: return
    val prefs = prefsState ?: return
    val today = app.today()
    val minutes = chosen?.takeIf { it.first == today }?.second ?: prefs.dailyMinutes
    val doneInterviews = remember(interviewAttempts) { interviewAttempts.map { it.questionId }.toSet() }
    val plan = remember(snapshot, minutes, prefs, today, doneInterviews) {
        SessionPlanner.plan(c, snapshot, prefs, minutes, today, doneInterviews)
    }
    val status = remember(snapshot, prefs, today) { Accountability.weekStatus(prefs, snapshot.activeDays, today) }
    val due = snapshot.dueReviews(today).size
    val nudge = Accountability.nudge(prefs, status, due, today)
    val gap = remember(snapshot, today) { Mastery.gaps(c, snapshot, today).firstOrNull() }
    val inProgress = c.orderedLessons.firstOrNull { snapshot.lesson(it.id).inProgress }

    ScreenScaffold(
        title = greeting(prefs.name),
        actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") } },
    ) {
        item {
            val warn = nudge.kind == NudgeKind.CREATIVE_WARNING
            AppCard(container = if (warn) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (warn) Icons.Outlined.WarningAmber else Icons.Outlined.EmojiObjects, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(nudge.title, style = MaterialTheme.typography.titleMedium)
                }
                Text(nudge.message, style = MaterialTheme.typography.bodyMedium)
                nudge.catchUpMinutes?.let { m ->
                    TextButton(onClick = { app.setTodayMinutes(m); onStart(SessionMode.TODAY, null, m) }) { Text("Start a $m-minute catch-up") }
                }
            }
        }

        item {
            AppCard(container = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Text("Today's session", style = MaterialTheme.typography.titleLarge)
                Text("How much time do you have?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MINUTE_OPTIONS.forEach { m ->
                        FilterChip(
                            selected = m == minutes,
                            onClick = { app.setTodayMinutes(if (m == prefs.dailyMinutes) null else m) },
                            label = { Text(if (m == prefs.dailyMinutes) "$m min ★" else "$m min") },
                        )
                    }
                }
                if (plan.isEmpty) {
                    plan.notes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                } else {
                    plan.items.forEach { PlanRow(it, c) }
                    plan.notes.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { onStart(SessionMode.TODAY, null, minutes) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text("Start · about ${plan.minutes} min")
                    }
                }
            }
        }

        item { WeekCard(prefs.studyDays, snapshot.activeDays, today, status.daysActive, status.goal) }

        if (due > 0) {
            item {
                AppCard(onClick = { onStart(SessionMode.REVIEW, null, null) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Replay, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("$due review${if (due == 1) "" else "s"} due", style = MaterialTheme.typography.titleMedium)
                            Text("Review only — no new material", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (gap != null) {
            item {
                val pre = c.lessonById[gap.prerequisiteId]
                val because = c.lessonById[gap.becauseOfLessonId]
                if (pre != null && because != null) {
                    AppCard(onClick = { onOpenLesson(pre.id) }) {
                        Text("Strengthen a building block", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "You've missed questions in “${because.title}”. It builds on “${pre.title}” — a short refresher should help.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        if (inProgress != null) {
            item {
                AppCard(onClick = { onStart(SessionMode.LESSON, inProgress.id, null) }) {
                    Text("Continue where you left off", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(inProgress.title, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        val interest = prefs.interests.firstOrNull()
        if (interest != null) {
            item {
                val unit = INTEREST_UNITS[interest]?.let { c.unitById[it] }
                val firstLesson = unit?.let { c.lessonsByUnit[it.id]?.firstOrNull() }
                AppCard(onClick = { if (firstLesson != null) onOpenLesson(firstLesson.id) else onOpenInterviews() }) {
                    Text("Because you're interested in $interest", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(unit?.title ?: "Interview Studio", style = MaterialTheme.typography.titleMedium)
                    Text(
                        unit?.summary ?: "Practise questions linked to the concepts you've learned.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun greeting(name: String): String {
    val hour = LocalTime.now().hour
    val part = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    return if (name.isBlank()) part else "$part, ${name.trim()}"
}

@Composable
private fun PlanRow(item: PlanItem, c: Curriculum) {
    val (icon, title, subtitle) = describe(item, c)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${kotlin.math.ceil(item.minutes).toInt()} min", style = MaterialTheme.typography.labelMedium)
    }
}

private data class Described(val icon: ImageVector, val title: String, val subtitle: String)

private fun describe(item: PlanItem, c: Curriculum): Described = when (item) {
    is PlanItem.Review -> Described(Icons.Outlined.Replay, "Review ${item.questionIds.size} question${if (item.questionIds.size == 1) "" else "s"}", "Spaced recall of earlier concepts")
    is PlanItem.GapRefresh -> Described(Icons.Outlined.Lightbulb, "Refresher: ${c.lessonById[item.lessonId]?.title}", "A building block you need next")
    is PlanItem.LessonPart -> {
        val title = c.lessonById[item.lessonId]?.title.orEmpty()
        when (item.segment) {
            Segment.QUICK -> Described(Icons.Outlined.AutoStories, title, "Quick Learn")
            Segment.PRACTICE -> Described(Icons.Outlined.FitnessCenter, title, "Knowledge check")
            Segment.EXERCISE -> Described(Icons.Outlined.Extension, title, "Application exercise")
            Segment.DEEP -> Described(Icons.AutoMirrored.Outlined.MenuBook, title, "Deep Dive")
        }
    }
    is PlanItem.CheckpointChallenge -> Described(Icons.Outlined.Extension, c.checkpoints[item.unitId]?.title.orEmpty(), "Unit challenge — combines concepts")
    is PlanItem.Case -> Described(Icons.Outlined.Cases, c.caseById[item.caseId]?.title.orEmpty(), "Case study")
    is PlanItem.Interview -> Described(Icons.Outlined.RecordVoiceOver, "Interview practice", c.interviewById[item.questionId]?.category?.label.orEmpty())
}

/** Seven dots for this week: filled = learned, ring = planned study day. Consistency, not streaks. */
@Composable
private fun WeekCard(studyDays: Set<DayOfWeek>, active: Set<Long>, today: Long, daysActive: Int, goal: Int) {
    val monday = LocalDate.ofEpochDay(today).with(DayOfWeek.MONDAY)
    AppCard {
        Text("This week: $daysActive of $goal days", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0..6).forEach { i ->
                val date = monday.plusDays(i.toLong())
                val day = date.toEpochDay()
                val learned = day in active
                val planned = date.dayOfWeek in studyDays
                val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.semantics {
                        contentDescription = "$label: " + when {
                            learned -> "learned"
                            planned -> "planned"
                            else -> "rest day"
                        }
                    },
                ) {
                    Text(label.take(2), style = MaterialTheme.typography.labelSmall, color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(4.dp))
                    Box(
                        Modifier.size(22.dp).then(
                            when {
                                learned -> Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                                planned -> Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                else -> Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            },
                        ),
                    )
                }
            }
        }
        Text("Missed days never erase progress — the plan simply adapts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
