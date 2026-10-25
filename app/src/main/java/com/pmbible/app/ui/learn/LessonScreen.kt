package com.pmbible.app.ui.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pmbible.app.data.ProgressRepository
import com.pmbible.app.ui.AppViewModel
import com.pmbible.app.ui.components.AppCard
import com.pmbible.app.ui.components.Callout
import com.pmbible.app.ui.components.EmptyState
import com.pmbible.app.ui.components.MasteryPill
import com.pmbible.app.ui.components.Pill
import com.pmbible.app.ui.components.ScreenScaffold
import com.pmbible.app.ui.components.block
import com.pmbible.app.ui.components.SectionHeader
import com.pmbible.app.ui.components.label
import com.pmbible.core.domain.Mastery
import com.pmbible.core.model.Curriculum
import com.pmbible.core.model.LanguageMode
import com.pmbible.core.model.Lesson
import com.pmbible.core.model.Segment

private val TABS = listOf("Quick Learn", "Deep Dive", "Connections")

/** A concept page: explanations, the concept graph around it, notes, and the entry to practise it. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    app: AppViewModel,
    lessonId: String,
    onBack: () -> Unit,
    onOpenLesson: (String) -> Unit,
    onStartLesson: (String) -> Unit,
    onOpenCase: (String) -> Unit,
    onOpenInterview: (String) -> Unit,
    onStartChallenge: (String) -> Unit,
) {
    val library by app.library.collectAsState()
    val prefsState by app.prefs.collectAsState()
    val snapshot by app.snapshot.collectAsState()
    val bookmarks by app.bookmarks.collectAsState()
    val notes by app.notes.collectAsState()
    val c = library?.curriculum ?: return
    val prefs = prefsState ?: return
    val lesson = c.lessonById[lessonId]
    if (lesson == null) {
        ScreenScaffold("Concept", onBack) {
            item { EmptyState(Icons.Outlined.SearchOff, "Concept not found", "It may have been renamed in a content update.", "Back" to onBack) }
        }
        return
    }
    LaunchedEffect(lessonId) { app.openLesson(lessonId) }

    val key = ProgressRepository.key("LESSON", lesson.id)
    val bookmarked = key in bookmarks
    val state = snapshot.lesson(lesson.id)
    val today = app.today()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val missingPrereqs = lesson.prerequisites.filter { !snapshot.lesson(it).completed }.mapNotNull { c.lessonById[it] }

    ScreenScaffold(
        title = c.unitById[lesson.unitId]?.title.orEmpty(),
        onBack = onBack,
        actions = {
            IconButton(onClick = { app.setBookmark(key, !bookmarked) }) {
                Icon(
                    if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark this concept",
                )
            }
        },
    ) {
        block {
            Text(lesson.title, style = MaterialTheme.typography.headlineSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(lesson.level.label())
                Pill("${lesson.quickMinutes} min quick · ${lesson.deepMinutes} min deep")
                MasteryPill(Mastery.level(lesson, snapshot, today))
                if (state.completed) Pill(if (state.placed) "Placed" else "Completed")
            }
        }
        if (missingPrereqs.isNotEmpty()) {
            item {
                Callout("Builds on: ${missingPrereqs.joinToString { it.title }}. You can read this now, but learning those first will make it easier.")
            }
        }
        item {
            Button(onClick = { onStartLesson(lesson.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(
                    when {
                        !state.quickDone -> "Start lesson"
                        !state.practiceDone -> "Continue: knowledge check"
                        !state.exerciseDone -> "Continue: application exercise"
                        !state.deepDone -> "Continue: deep dive"
                        else -> "Practise again"
                    },
                )
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageMode.entries.forEach { mode ->
                    FilterChip(
                        selected = prefs.language == mode,
                        onClick = { app.updatePrefs { it.copy(language = mode) } },
                        label = { Text(mode.label) },
                    )
                }
            }
        }
        item {
            PrimaryTabRow(selectedTabIndex = tab) {
                TABS.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
        }
        when (tab) {
            0 -> item { QuickLearnContent(lesson, prefs.language) }
            1 -> {
                item { DeepDiveContent(lesson) }
                item {
                    if (state.deepDone) Callout("You've read this deep dive. Revisit any section above whenever you like.")
                    else OutlinedButton(onClick = { app.markSegment(lesson.id, Segment.DEEP) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Mark deep dive as read")
                    }
                }
            }
            else -> block {
                val unitDone = c.lessonsByUnit[lesson.unitId].orEmpty().all { snapshot.lesson(it.id).completed }
                Connections(lesson, c, onOpenLesson, onOpenCase, onOpenInterview, onStartChallenge.takeIf { unitDone })
            }
        }
        block {
            SectionHeader("My notes")
            var draft by rememberSaveable(key, notes[key]) { mutableStateOf(notes[key].orEmpty()) }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text("Write what you'd explain to a friend") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
            val changed = draft.trim() != notes[key].orEmpty()
            OutlinedButton(onClick = { app.saveNote(key, draft) }, enabled = changed, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (changed) "Save note" else "Note saved")
            }
        }
    }
}

/** The concept graph around a lesson: what it needs, what it unlocks, and where it's applied. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Connections(
    lesson: Lesson,
    c: Curriculum,
    onOpenLesson: (String) -> Unit,
    onOpenCase: (String) -> Unit,
    onOpenInterview: (String) -> Unit,
    /** Null while the unit's lessons are unfinished; the challenge is shown but not startable. */
    onStartChallenge: ((String) -> Unit)?,
) {
    @Composable
    fun group(title: String, lessons: List<Lesson>) {
        if (lessons.isEmpty()) return
        SectionHeader(title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            lessons.forEach { l -> AssistChip(onClick = { onOpenLesson(l.id) }, label = { Text(l.title) }) }
        }
    }
    group("Builds on (prerequisites)", lesson.prerequisites.mapNotNull { c.lessonById[it] })
    group("Unlocks", c.dependents(lesson.id))
    group("Related concepts", lesson.related.mapNotNull { c.lessonById[it] })

    val cases = c.cases.filter { lesson.id in it.lessonIds }
    if (cases.isNotEmpty()) {
        SectionHeader("Applied in case studies")
        cases.forEach { cs -> AppCard(onClick = { onOpenCase(cs.id) }) { Text(cs.title, style = MaterialTheme.typography.bodyLarge) } }
    }
    val interviews = c.interview.filter { lesson.id in it.lessonIds }
    if (interviews.isNotEmpty()) {
        SectionHeader("Interview questions that test it")
        interviews.forEach { q ->
            AppCard(onClick = { onOpenInterview(q.id) }) {
                Text(q.prompt, style = MaterialTheme.typography.bodyMedium)
                Text(q.category.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    c.checkpoints.entries.firstOrNull { (_, cp) -> cp.steps.any { it.lessonId == lesson.id } }?.let { (unitId, cp) ->
        SectionHeader("Unit challenge")
        AppCard(onClick = onStartChallenge?.let { start -> { start(unitId) } }) {
            Text(cp.title, style = MaterialTheme.typography.bodyLarge)
            if (onStartChallenge == null) Text("Unlocks after you finish this unit's lessons", style = MaterialTheme.typography.bodySmall)
        }
    }
}
