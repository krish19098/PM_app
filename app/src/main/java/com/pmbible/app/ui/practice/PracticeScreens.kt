package com.pmbible.app.ui.practice

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pmbible.app.ui.AppViewModel
import com.pmbible.app.ui.components.AppCard
import com.pmbible.app.ui.components.Callout
import com.pmbible.app.ui.components.EmptyState
import com.pmbible.app.ui.components.LabeledProgress
import com.pmbible.app.ui.components.Pill
import com.pmbible.app.ui.components.ScreenScaffold
import com.pmbible.app.ui.components.SectionHeader
import com.pmbible.app.ui.components.block
import com.pmbible.app.ui.components.label
import com.pmbible.app.ui.theme.LocalExtraColors
import com.pmbible.core.domain.Insights
import com.pmbible.core.model.InterviewCategory
import com.pmbible.core.model.Level

/** Practice hub: interview studio, case studies, unit challenges and industry updates. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PracticeScreen(
    app: AppViewModel,
    onOpenInterviews: () -> Unit,
    onOpenCase: (String) -> Unit,
    onStartChallenge: (String) -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenLesson: (String) -> Unit,
) {
    val library by app.library.collectAsState()
    val snapshot by app.snapshot.collectAsState()
    val c = library?.curriculum ?: return
    val extra = LocalExtraColors.current
    val competency = Insights.competency(snapshot)

    ScreenScaffold("Practice") {
        item {
            AppCard(onClick = onOpenInterviews, container = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Outlined.RecordVoiceOver, contentDescription = null)
                Text("Interview Studio", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${c.interview.size} questions across product sense, execution, metrics, strategy, behavioural and AI PM — answer first, then compare and self-rate.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (competency.isNotEmpty()) Text("Practised: ${competency.keys.joinToString { it.label }}", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { SectionHeader("Case studies") }
        item { Callout("Cases are hypothetical scenarios set in realistic contexts. They don't use any company's internal data.") }
        c.cases.forEach { cs ->
            item(key = cs.id) {
                val missing = cs.lessonIds.filter { !snapshot.lesson(it).completed }.mapNotNull { c.lessonById[it] }
                AppCard(onClick = { onOpenCase(cs.id) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(cs.level.label())
                        Pill("${cs.minutes} min")
                        if (cs.id in snapshot.casesDone) Icon(Icons.Outlined.CheckCircle, contentDescription = "Completed", tint = extra.success)
                    }
                    Text(cs.title, style = MaterialTheme.typography.titleMedium)
                    Text(cs.context, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    if (missing.isNotEmpty()) {
                        Text("Best after learning:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            missing.forEach { l -> AssistChip(onClick = { onOpenLesson(l.id) }, label = { Text(l.title) }) }
                        }
                    }
                }
            }
        }
        item { SectionHeader("Unit challenges") }
        c.units.forEach { u ->
            item(key = "cp-${u.id}") {
                val lessons = c.lessonsByUnit[u.id].orEmpty()
                val ready = lessons.all { snapshot.lesson(it.id).completed }
                val done = u.id in snapshot.checkpointsDone
                AppCard(onClick = if (ready) ({ onStartChallenge(u.id) }) else null) {
                    Text("Unit ${u.order}: ${c.checkpoints[u.id]?.title}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            done -> "Completed — retake any time"
                            ready -> "Ready to start"
                            else -> "Finish ${lessons.count { !snapshot.lesson(it.id).completed }} more lesson(s) in “${u.title}” to unlock"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            AppCard(onClick = onOpenUpdates) {
                Text("PM & AI developments", style = MaterialTheme.typography.titleMedium)
                Text("Dated, sourced updates linked to lessons, with a ‘why it matters’ for PMs.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Interview question bank with filters by category, level, role and company. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterviewListScreen(app: AppViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val library by app.library.collectAsState()
    val attempts by app.interviewAttempts.collectAsState()
    val c = library?.curriculum ?: return
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var level by rememberSaveable { mutableStateOf<String?>(null) }
    var role by rememberSaveable { mutableStateOf<String?>(null) }
    var company by rememberSaveable { mutableStateOf<String?>(null) }
    val roles = c.interview.flatMap { it.roles }.distinct().sorted()
    val companies = c.interview.mapNotNull { it.company }.distinct().sorted()
    val attempted = attempts.map { it.questionId }.toSet()
    val list = c.interview.filter { q ->
        (category == null || q.category.name == category) && (level == null || q.level.name == level) &&
            (role == null || role in q.roles) && (company == null || q.company == company)
    }
    val anyFilter = listOf(category, level, role, company).any { it != null }

    ScreenScaffold("Interview Studio", onBack) {
        item { Callout("Company-tagged questions are representative practice in that company's product domain — never presented as questions the company actually asked.") }
        block {
            Text("Category", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InterviewCategory.entries.forEach { cat ->
                    FilterChip(selected = category == cat.name, onClick = { category = if (category == cat.name) null else cat.name }, label = { Text(cat.label) })
                }
            }
            Text("Level", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Level.entries.forEach { l ->
                    FilterChip(selected = level == l.name, onClick = { level = if (level == l.name) null else l.name }, label = { Text(l.label()) })
                }
            }
            Text("Role", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                roles.forEach { r -> FilterChip(selected = role == r, onClick = { role = if (role == r) null else r }, label = { Text(r) }) }
            }
            Text("Company context", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                companies.forEach { co -> FilterChip(selected = company == co, onClick = { company = if (company == co) null else co }, label = { Text(co) }) }
            }
            if (anyFilter) {
                AssistChip(
                    onClick = { category = null; level = null; role = null; company = null },
                    label = { Text("Clear filters") },
                    leadingIcon = { Icon(Icons.Outlined.FilterAltOff, contentDescription = null) },
                )
            }
        }
        if (list.isEmpty()) {
            item { EmptyState(Icons.Outlined.SearchOff, "No questions match", "Remove a filter to see more questions.") }
        }
        list.forEach { q ->
            item(key = q.id) {
                AppCard(onClick = { onOpen(q.id) }) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(q.category.label)
                        Pill(q.level.label())
                        q.company?.let { Pill(it) }
                        if (q.id in attempted) Pill("Practised")
                    }
                    Text(q.prompt, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun InterviewScreen(app: AppViewModel, questionId: String, onBack: () -> Unit, onOpenLesson: (String) -> Unit) {
    val library by app.library.collectAsState()
    val attempts by app.interviewAttempts.collectAsState()
    val c = library?.curriculum ?: return
    val q = c.interviewById[questionId]
    ScreenScaffold("Interview practice", onBack) {
        if (q == null) {
            item { EmptyState(Icons.Outlined.SearchOff, "Question not found", "It may have been removed in a content update.", "Back" to onBack) }
        } else {
            item {
                InterviewPractice(
                    question = q,
                    curriculum = c,
                    attempts = attempts.filter { it.questionId == q.id },
                    onSave = { answer, score -> app.recordInterview(q, answer, score) },
                    onOpenLesson = onOpenLesson,
                )
            }
        }
    }
}

/** Curated industry developments: each has a publication date, a source link and linked lessons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdatesScreen(app: AppViewModel, onBack: () -> Unit, onOpenLesson: (String) -> Unit) {
    val library by app.library.collectAsState()
    val c = library?.curriculum ?: return
    val context = LocalContext.current
    var linkError by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold("PM & AI developments", onBack) {
        item {
            Callout(
                "Curated snapshot, last reviewed ${c.manifest.reviewedOn}. This is not a live feed and may miss recent news — " +
                    "always open the source to check the latest details.",
            )
        }
        if (linkError) item { Callout("No browser app is available to open links on this device.") }
        c.updates.forEach { u ->
            item(key = u.id) {
                AppCard {
                    Text(u.date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(u.title, style = MaterialTheme.typography.titleMedium)
                    Text(u.summary, style = MaterialTheme.typography.bodyMedium)
                    Text("Why it matters for PMs", style = MaterialTheme.typography.labelLarge)
                    Text(u.whyItMatters, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        u.lessonIds.mapNotNull { c.lessonById[it] }.forEach { l -> AssistChip(onClick = { onOpenLesson(l.id) }, label = { Text(l.title) }) }
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.source.url)))
                            } catch (_: ActivityNotFoundException) {
                                linkError = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                        Text("  Source: ${u.source.publisher}")
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompetencyBars(competency: Map<InterviewCategory, Double>) {
    InterviewCategory.entries.filter { it in competency }.forEach { cat ->
        val v = competency.getValue(cat)
        LabeledProgress(cat.label, v.toFloat(), "${(v * 100).toInt()}%")
    }
}
