package com.pmbible.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pmbible.app.data.AnswerContext
import com.pmbible.app.ui.AppViewModel
import com.pmbible.app.ui.components.AppCard
import com.pmbible.app.ui.components.EmptyState
import com.pmbible.app.ui.components.LabeledProgress
import com.pmbible.app.ui.components.MasteryPill
import com.pmbible.app.ui.components.Pill
import com.pmbible.app.ui.components.ScreenScaffold
import com.pmbible.app.ui.components.SectionHeader
import com.pmbible.app.ui.practice.CompetencyBars
import com.pmbible.app.ui.theme.LocalExtraColors
import com.pmbible.core.domain.Insights
import com.pmbible.core.domain.Mastery
import com.pmbible.core.domain.MasteryLevel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE = DateTimeFormatter.ofPattern("d MMM")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressScreen(
    app: AppViewModel,
    onReview: () -> Unit,
    onOpenLesson: (String) -> Unit,
    onOpenSaved: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val library by app.library.collectAsState()
    val prefsState by app.prefs.collectAsState()
    val snapshot by app.snapshot.collectAsState()
    val c = library?.curriculum ?: return
    val prefs = prefsState ?: return
    val today = app.today()
    val recap = remember(snapshot, today, prefs.weeklyGoal) { Insights.weeklyRecap(c, snapshot, prefs.weeklyGoal, today) }
    val gaps = remember(snapshot, today) { Mastery.gaps(c, snapshot, today) }
    val levels = remember(snapshot, today) { c.lessons.groupingBy { Mastery.level(it, snapshot, today) }.eachCount() }
    val due = snapshot.dueReviews(today).size

    ScreenScaffold(
        "Progress",
        actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") } },
    ) {
        item {
            AppCard(container = MaterialTheme.colorScheme.primaryContainer) {
                Text("This week (from ${recap.weekStart.format(DATE)})", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${recap.daysActive} of ${recap.goal} study days · ${recap.minutes} min · ${recap.lessonsCompleted} lesson(s) completed",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    recap.accuracy?.let { "${recap.answers} answers, ${(it * 100).toInt()}% correct" } ?: "No answers yet this week",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item { LastFourWeeks(snapshot.minutesByDay, today) }
        item {
            AppCard {
                Text("Mastery across ${c.lessons.size} concepts", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MasteryLevel.entries.reversed().forEach { l -> levels[l]?.let { Pill("${l.label}: $it") } }
                }
                Text(
                    "Mastery rises with correct spaced reviews and applied practice, and fades gently when reviews go overdue.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onReview, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (due > 0) "Review $due due question${if (due == 1) "" else "s"}" else "Strengthen weak concepts")
                }
            }
        }
        item { SectionHeader("Weak areas") }
        val weakest = Mastery.weakest(c, snapshot, today)
        if (weakest.isEmpty() && gaps.isEmpty()) {
            item { EmptyState(Icons.Outlined.History, "Nothing to flag yet", "Complete a few lessons and reviews, and weak areas will appear here.") }
        }
        gaps.forEach { g ->
            item(key = "gap-${g.prerequisiteId}") {
                AppCard(onClick = { onOpenLesson(g.prerequisiteId) }) {
                    Text("Prerequisite gap: ${c.lessonById[g.prerequisiteId]?.title}", style = MaterialTheme.typography.titleSmall)
                    Text("Missed questions in “${c.lessonById[g.becauseOfLessonId]?.title}” point back to this concept.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        weakest.forEach { (lesson, score) ->
            item(key = "weak-${lesson.id}") {
                AppCard(onClick = { onOpenLesson(lesson.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(lesson.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        MasteryPill(MasteryLevel.of(score))
                    }
                }
            }
        }
        item { SectionHeader("Mastery by unit") }
        item {
            AppCard {
                c.units.forEach { u ->
                    val score = Mastery.unitScore(c, u.id, snapshot, today)
                    LabeledProgress(u.title, score.toFloat(), "${(score * 100).toInt()}%")
                }
            }
        }
        item { SectionHeader("Interview competency (self-rated)") }
        item {
            AppCard {
                if (recap.competency.isEmpty()) Text("Practise in the Interview Studio to track competencies.", style = MaterialTheme.typography.bodyMedium)
                else CompetencyBars(recap.competency)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onOpenSaved, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.BookmarkBorder, contentDescription = null); Text("  Saved")
                }
                OutlinedButton(onClick = onOpenHistory, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                    Icon(Icons.Outlined.History, contentDescription = null); Text("  History")
                }
            }
        }
    }
}

/** 28 small bars of daily minutes — a calm view of consistency without streak pressure. */
@Composable
private fun LastFourWeeks(minutesByDay: Map<Long, Int>, today: Long) {
    val days = (27 downTo 0).map { today - it }
    val max = days.maxOf { minutesByDay[it] ?: 0 }.coerceAtLeast(15)
    val total = days.sumOf { minutesByDay[it] ?: 0 }
    val active = days.count { (minutesByDay[it] ?: 0) > 0 }
    AppCard {
        Text("Last 4 weeks", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = "$active active days and $total minutes in the last four weeks" },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            days.forEach { d ->
                val m = minutesByDay[d] ?: 0
                Box(
                    Modifier.weight(1f).height((4 + 52f * m / max).dp)
                        .background(if (m > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall),
                )
            }
        }
        Text("$active active days · $total minutes", style = MaterialTheme.typography.bodySmall)
    }
}

/** Bookmarked items and personal notes, resolved to their current content titles. */
@Composable
fun SavedScreen(
    app: AppViewModel,
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit,
    onOpenCase: (String) -> Unit,
    onOpenInterview: (String) -> Unit,
) {
    val library by app.library.collectAsState()
    val bookmarks by app.bookmarks.collectAsState()
    val notes by app.notes.collectAsState()
    val c = library?.curriculum ?: return

    fun titleOf(key: String): String? {
        val parts = key.split(":", limit = 2)
        if (parts.size != 2) return null
        val id = parts[1]
        return when (parts[0]) {
            "LESSON" -> c.lessonById[id]?.title
            "CASE" -> c.caseById[id]?.title
            "INTERVIEW" -> c.interviewById[id]?.prompt
            else -> null
        }
    }
    fun open(key: String) {
        val parts = key.split(":", limit = 2)
        if (parts.size != 2) return
        when (parts[0]) {
            "LESSON" -> onOpenLesson(parts[1])
            "CASE" -> onOpenCase(parts[1])
            "INTERVIEW" -> onOpenInterview(parts[1])
        }
    }

    ScreenScaffold("Saved", onBack) {
        item { SectionHeader("Bookmarks") }
        val marks = bookmarks.mapNotNull { k -> titleOf(k)?.let { k to it } }
        if (marks.isEmpty()) item { Text("Tap the bookmark icon on any concept to save it here.", style = MaterialTheme.typography.bodyMedium) }
        marks.forEach { (k, title) ->
            item(key = "b-$k") { AppCard(onClick = { open(k) }) { Text(title, style = MaterialTheme.typography.bodyLarge) } }
        }
        item { SectionHeader("My notes") }
        val noted = notes.mapNotNull { (k, text) -> titleOf(k)?.let { Triple(k, it, text) } }
        if (noted.isEmpty()) item { Text("Notes you write on concept pages appear here.", style = MaterialTheme.typography.bodyMedium) }
        noted.forEach { (k, title, text) ->
            item(key = "n-$k") {
                AppCard(onClick = { open(k) }) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Learning history: recent answers, exercise write-ups and interview attempts. */
@Composable
fun HistoryScreen(app: AppViewModel, onBack: () -> Unit, onOpenLesson: (String) -> Unit) {
    val library by app.library.collectAsState()
    val attempts by app.attemptLog.collectAsState()
    val exercises by app.exerciseAnswers.collectAsState()
    val interviews by app.interviewAttempts.collectAsState()
    val c = library?.curriculum ?: return
    val extra = LocalExtraColors.current
    fun date(ts: Long) = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE)

    ScreenScaffold("History", onBack) {
        if (attempts.isEmpty() && exercises.isEmpty() && interviews.isEmpty()) {
            item { EmptyState(Icons.Outlined.History, "No history yet", "Your answers, exercises and interview practice will be listed here.") }
        }
        if (exercises.isNotEmpty()) item { SectionHeader("Exercise write-ups") }
        exercises.take(30).forEach { e ->
            item(key = "e-${e.id}") {
                AppCard(onClick = { onOpenLesson(e.lessonId) }) {
                    Text("${c.lessonById[e.lessonId]?.title ?: e.lessonId} · ${date(e.timestamp)} · ${(e.score * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Text(e.answer.ifBlank { "(no answer written)" }, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
                }
            }
        }
        if (interviews.isNotEmpty()) item { SectionHeader("Interview attempts") }
        interviews.take(30).forEach { a ->
            item(key = "i-${a.id}") {
                AppCard {
                    Text("${date(a.timestamp)} · self-score ${(a.score * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Text(c.interviewById[a.questionId]?.prompt ?: a.questionId, style = MaterialTheme.typography.bodyMedium)
                    if (a.answer.isNotBlank()) Text(a.answer, style = MaterialTheme.typography.bodySmall, maxLines = 4)
                }
            }
        }
        if (attempts.isNotEmpty()) item { SectionHeader("Recent answers") }
        attempts.take(60).forEach { a ->
            item(key = "a-${a.id}") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.width(6.dp).height(36.dp)
                            .background(if (a.correct) extra.success else MaterialTheme.colorScheme.error, MaterialTheme.shapes.extraSmall),
                    )
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(c.questionById[a.questionId]?.prompt ?: a.questionId, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                        Text(
                            "${date(a.timestamp)} · ${contextLabel(a.context)} · ${if (a.correct) "correct" else "incorrect"}",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun contextLabel(name: String) = when (name) {
    AnswerContext.PRACTICE.name -> "practice"
    AnswerContext.REVIEW.name -> "review"
    AnswerContext.CHECKPOINT.name -> "challenge"
    AnswerContext.CASE.name -> "case"
    else -> name.lowercase()
}
