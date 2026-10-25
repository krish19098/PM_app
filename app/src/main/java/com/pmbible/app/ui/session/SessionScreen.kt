package com.pmbible.app.ui.session

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pmbible.app.data.AnswerContext
import com.pmbible.app.ui.AppViewModel
import com.pmbible.app.ui.components.AppCard
import com.pmbible.app.ui.components.BilingualText
import com.pmbible.app.ui.components.Callout
import com.pmbible.app.ui.components.EmptyState
import com.pmbible.app.ui.components.LoadingState
import com.pmbible.app.ui.components.Pill
import com.pmbible.app.ui.components.QuestionCard
import com.pmbible.app.ui.components.SectionHeader
import com.pmbible.app.ui.learn.DeepDiveContent
import com.pmbible.app.ui.learn.QuickLearnContent
import com.pmbible.app.ui.practice.InterviewPractice
import com.pmbible.core.domain.SessionPlanner
import com.pmbible.core.model.Curriculum
import com.pmbible.core.model.LanguageMode
import com.pmbible.core.model.Segment
import kotlin.math.ceil

/** Holds a session's steps and position so they survive rotation; time is tracked for the learning log. */
class SessionViewModel : ViewModel() {
    var steps: List<Step>? = null
    var index by mutableIntStateOf(0)
    val results = mutableStateMapOf<String, Boolean>()
    private var startedAt = SystemClock.elapsedRealtime()
    private var loggedMinutes = 0

    fun init(build: () -> List<Step>) {
        if (steps == null) {
            steps = build()
            startedAt = SystemClock.elapsedRealtime()
        }
    }

    /** Minutes not yet logged, capped so an idle open screen doesn't inflate study time. */
    fun takeUnloggedMinutes(): Int {
        val total = ceil((SystemClock.elapsedRealtime() - startedAt) / 60_000.0).toInt().coerceIn(1, 120)
        val delta = total - loggedMinutes
        loggedMinutes = total
        return delta.coerceAtLeast(0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    app: AppViewModel,
    mode: SessionMode,
    arg: String?,
    minutes: Int?,
    onClose: () -> Unit,
    onOpenLesson: (String) -> Unit,
) {
    val library by app.library.collectAsState()
    val prefs by app.prefs.collectAsState()
    val interviewAttempts by app.interviewAttempts.collectAsState()
    val snapshotReady by app.snapshotReady.collectAsState()
    val c = library?.curriculum
    val p = prefs
    // Wait for stored progress so a cold start from a notification doesn't plan from an empty history.
    if (c == null || p == null || !snapshotReady) {
        LoadingState()
        return
    }
    val vm: SessionViewModel = viewModel(key = "session-$mode-$arg-$minutes")
    vm.init {
        val snapshot = app.snapshot.value
        val today = app.today()
        when (mode) {
            SessionMode.TODAY -> {
                val done = interviewAttempts.map { it.questionId }.toSet()
                SessionBuilder.fromPlan(c, SessionPlanner.plan(c, snapshot, p, minutes ?: p.dailyMinutes, today, done))
            }
            SessionMode.LESSON -> c.lessonById[arg]?.let { SessionBuilder.forLesson(it, snapshot) }.orEmpty()
            SessionMode.REVIEW -> SessionBuilder.forReview(c, snapshot, today)
            SessionMode.CHECKPOINT -> arg?.let { SessionBuilder.checkpointSteps(c, it) }.orEmpty()
            SessionMode.CASE -> arg?.let { SessionBuilder.caseSteps(c, it) }.orEmpty()
        }
    }
    val steps = vm.steps.orEmpty()
    var confirmExit by remember { mutableStateOf(false) }
    val finished = vm.index >= steps.size

    // Log study time whenever the learner leaves, including mid-session; progress is saved per step.
    DisposableEffect(vm) {
        onDispose { if (vm.index > 0) app.addMinutes(vm.takeUnloggedMinutes()) }
    }
    LaunchedEffect(finished) {
        if (finished && steps.isNotEmpty()) app.addMinutes(vm.takeUnloggedMinutes())
    }

    val exit: () -> Unit = {
        if (finished || vm.index == 0) onClose() else { confirmExit = true }
    }
    BackHandler(onBack = exit)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (!finished && steps.isNotEmpty()) {
                        LinearProgressIndicator(
                            progress = { vm.index / steps.size.toFloat() },
                            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                        )
                    } else Text("Session")
                },
                navigationIcon = { IconButton(onClick = exit) { Icon(Icons.Outlined.Close, contentDescription = "Close session") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(remember(vm.index) { ScrollState(0) }).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                steps.isEmpty() -> EmptyState(
                    Icons.Outlined.EventAvailable, "Nothing to do here right now",
                    "You're all caught up for this activity. Try the Library or another session length.",
                    "Back" to onClose,
                )
                finished -> Summary(vm, steps, c, onClose)
                else -> StepContent(app, vm, steps[vm.index], c, p.language, interviewAttempts, onOpenLesson) { vm.index++ }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("Leave this session?") },
            text = { Text("Everything you've completed so far is saved. You can pick up the rest later.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; onClose() }) { Text("Leave") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Keep going") } },
        )
    }
}

@Composable
private fun StepContent(
    app: AppViewModel,
    vm: SessionViewModel,
    step: Step,
    c: Curriculum,
    language: LanguageMode,
    interviewAttempts: List<com.pmbible.app.data.db.InterviewAttemptEntity>,
    onOpenLesson: (String) -> Unit,
    next: () -> Unit,
) {
    when (step) {
        is Step.Intro -> {
            Pill(step.label)
            Text(step.title, style = MaterialTheme.typography.headlineSmall)
            BilingualText(step.body, language)
            step.bullets.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
            PrimaryButton("Start", next)
        }
        is Step.Quick -> {
            Pill("Quick Learn · ${step.lesson.quickMinutes} min")
            Text(step.lesson.title, style = MaterialTheme.typography.headlineSmall)
            QuickLearnContent(step.lesson, language)
            PrimaryButton("Got it — continue") {
                app.markSegment(step.lesson.id, Segment.QUICK)
                next()
            }
        }
        is Step.Deep -> {
            Pill("Deep Dive · ${step.lesson.deepMinutes} min")
            Text(step.lesson.title, style = MaterialTheme.typography.headlineSmall)
            DeepDiveContent(step.lesson)
            PrimaryButton("Finish deep dive") {
                app.markSegment(step.lesson.id, Segment.DEEP)
                next()
            }
        }
        is Step.Ask -> {
            val lessonTitle = c.lessonById[step.question.lessonId]?.title
            if (lessonTitle != null) {
                Text(lessonTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            QuestionCard(
                question = step.question,
                allowGuessFlag = step.context == AnswerContext.PRACTICE || step.context == AnswerContext.REVIEW,
                onDone = { correct, confident ->
                    app.recordAnswer(step.question, correct, confident, step.context, step.sourceId)
                    vm.results[step.question.id] = correct
                    step.completesPracticeOf?.let { app.markSegment(it, Segment.PRACTICE) }
                    next()
                },
            )
        }
        is Step.Exercise -> ExerciseStep(step, onSave = { answer, score -> app.recordExercise(step.lesson.id, answer, score) }, next = next)
        is Step.CheckpointEnd -> {
            val cp = c.checkpoints.getValue(step.unitId)
            val correct = cp.steps.count { vm.results[it.id] == true }
            Debrief("Challenge complete", "$correct of ${cp.steps.size} decisions matched the recommended reasoning.", cp.debrief, emptyList()) {
                app.completeCheckpoint(step.unitId, correct / cp.steps.size.toDouble())
                next()
            }
        }
        is Step.CaseEnd -> {
            val cs = c.caseById.getValue(step.caseId)
            val correct = cs.decisions.count { vm.results[it.id] == true }
            Debrief(
                "Case debrief", "$correct of ${cs.decisions.size} decisions matched the recommended reasoning.",
                "Other approaches: ${cs.alternatives}\n\nTakeaway: ${cs.takeaway}",
                cs.sources.map { "${it.publisher}: ${it.title} — ${it.url}" },
            ) {
                app.completeCase(step.caseId, correct / cs.decisions.size.toDouble())
                next()
            }
        }
        is Step.Interview -> InterviewPractice(
            question = step.question,
            curriculum = c,
            attempts = interviewAttempts.filter { it.questionId == step.question.id },
            onSave = { answer, score -> app.recordInterview(step.question, answer, score) },
            onOpenLesson = onOpenLesson,
            onFinished = next,
        )
    }
}

@Composable
private fun ExerciseStep(step: Step.Exercise, onSave: (String, Double) -> Unit, next: () -> Unit) {
    val ex = step.lesson.exercise
    var answer by rememberSaveable(step.lesson.id) { mutableStateOf("") }
    var revealed by rememberSaveable(step.lesson.id) { mutableStateOf(false) }
    var checked by rememberSaveable(step.lesson.id) { mutableStateOf(List(ex.checklist.size) { false }) }

    Pill("Apply it · exercise")
    Text(step.lesson.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    AppCard { Text(ex.scenario, style = MaterialTheme.typography.bodyMedium) }
    Text(ex.prompt, style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = answer,
        onValueChange = { answer = it },
        enabled = !revealed,
        label = { Text("Your answer") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
    )
    if (!revealed) {
        PrimaryButton(if (answer.isBlank()) "Show a model answer" else "Compare with a model answer") { revealed = true }
    } else {
        SectionHeader("A strong answer")
        Text(ex.modelAnswer, style = MaterialTheme.typography.bodyMedium)
        SectionHeader("Tick what your answer covered")
        ex.checklist.forEachIndexed { i, item ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked[i], role = Role.Checkbox) { on ->
                    checked = checked.toMutableList().also { it[i] = on }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked[i], onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Text(item, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (answer.isBlank()) Callout("You skipped writing an answer, so this counts as low application evidence. Writing even three lines helps you learn.")
        PrimaryButton("Save and continue") {
            val score = if (answer.isBlank()) 0.0 else checked.count { it } / ex.checklist.size.toDouble()
            onSave(answer, score)
            next()
        }
    }
}

@Composable
private fun Debrief(title: String, result: String, body: String, sources: List<String>, onDone: () -> Unit) {
    Pill("Debrief")
    Text(title, style = MaterialTheme.typography.headlineSmall)
    AppCard(container = MaterialTheme.colorScheme.primaryContainer) { Text(result, style = MaterialTheme.typography.titleMedium) }
    Text(body, style = MaterialTheme.typography.bodyMedium)
    if (sources.isNotEmpty()) {
        SectionHeader("Sources")
        sources.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    PrimaryButton("Continue", onDone)
}

@Composable
private fun Summary(vm: SessionViewModel, steps: List<Step>, c: Curriculum, onClose: () -> Unit) {
    val answered = vm.results.size
    val correct = vm.results.values.count { it }
    val lessons = steps.filterIsInstance<Step.Quick>().map { it.lesson.title }.distinct()
    EmptyState(Icons.Outlined.Celebration, "Session complete", "Nice work. Everything is saved, and reviews are scheduled automatically.")
    AppCard {
        if (lessons.isNotEmpty()) Text("New concepts: ${lessons.joinToString()}", style = MaterialTheme.typography.bodyMedium)
        if (answered > 0) Text("Answers: $correct of $answered correct", style = MaterialTheme.typography.bodyMedium)
        val missed = vm.results.filterValues { !it }.keys.mapNotNull { c.questionById[it]?.lessonId }.distinct().mapNotNull { c.lessonById[it]?.title }
        if (missed.isNotEmpty()) Text("You'll see these again soon: ${missed.joinToString()}", style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.height(8.dp))
    PrimaryButton("Done", onClose)
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(text) }
}
