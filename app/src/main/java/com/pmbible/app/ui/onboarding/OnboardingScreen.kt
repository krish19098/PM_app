package com.pmbible.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pmbible.app.notifications.Notifications
import com.pmbible.app.ui.AppViewModel
import com.pmbible.app.ui.components.AppCard
import com.pmbible.app.ui.components.Callout
import com.pmbible.app.ui.components.TimePickerDialog
import com.pmbible.app.ui.components.formatMinuteOfDay
import com.pmbible.app.ui.settings.ChipRow
import com.pmbible.app.ui.settings.MINUTE_CHOICES
import com.pmbible.app.ui.settings.ReminderEditor
import com.pmbible.app.ui.settings.StudyDayChips
import com.pmbible.app.ui.settings.SwitchRow
import com.pmbible.core.model.LanguageMode
import com.pmbible.core.model.NudgeTone
import com.pmbible.core.model.Pace
import com.pmbible.core.model.Reminder
import com.pmbible.core.model.ReminderKind
import com.pmbible.core.model.StartingPoint
import com.pmbible.core.model.UserPrefs

private const val STEPS = 6

private val GOALS = listOf("Become an APM", "Switch into PM", "Become an AI PM", "Prepare for interviews", "Be a better PM at work")
private val INTERESTS = listOf("AI PM", "Analytics", "Strategy", "Growth", "Discovery", "UX")

/** Keeps the draft across rotation until the learner finishes. */
class OnboardingViewModel : ViewModel() {
    var draft by mutableStateOf(UserPrefs(reminders = listOf(Reminder(1, 8 * 60, ReminderKind.LESSON))))
    var step by mutableIntStateOf(0)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(app: AppViewModel) {
    val vm: OnboardingViewModel = viewModel()
    val d = vm.draft
    val context = LocalContext.current
    var pickTime by remember { mutableStateOf(false) }
    val finish = { app.completeOnboarding(vm.draft) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }

    BackHandler(enabled = vm.step > 0) { vm.step-- }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp)) {
        LinearProgressIndicator(progress = { (vm.step + 1) / STEPS.toFloat() }, modifier = Modifier.fillMaxWidth())
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (vm.step) {
                0 -> {
                    Text("Welcome to PM Bible", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Learn product management one day at a time — real understanding, practice with feedback, and revision that adapts to you.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    OutlinedTextField(
                        value = d.name, onValueChange = { vm.draft = d.copy(name = it) },
                        label = { Text("What should we call you? (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    Callout("Everything stays on your device. No account needed.")
                }
                1 -> {
                    Text("Where are you starting?", style = MaterialTheme.typography.headlineSmall)
                    StartingPoint.entries.forEach { sp ->
                        AppCard {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .selectable(d.startingPoint == sp, role = Role.RadioButton) { vm.draft = d.copy(startingPoint = sp) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = d.startingPoint == sp, onClick = null)
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(sp.label, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        when (sp) {
                                            StartingPoint.ZERO -> "Start from the very first concept."
                                            StartingPoint.BASICS -> "Skip the first 2 units; their questions come back as reviews to confirm."
                                            StartingPoint.EXPERIENCED -> "Skip the first 4 units; reviews confirm what you know."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                    Text("Your goal", style = MaterialTheme.typography.titleMedium)
                    ChipRow(GOALS, d.goal, { it }) { vm.draft = d.copy(goal = it) }
                }
                2 -> {
                    Text("How much time can you give most days?", style = MaterialTheme.typography.headlineSmall)
                    ChipRow(MINUTE_CHOICES, d.dailyMinutes, { "$it min" }) { vm.draft = d.copy(dailyMinutes = it) }
                    Text(
                        when {
                            d.dailyMinutes <= 5 -> "5 minutes: a focused concept or quick revision each day."
                            d.dailyMinutes <= 15 -> "${d.dailyMinutes} minutes: one meaningful lesson with an application exercise."
                            else -> "${d.dailyMinutes} minutes: lessons plus deep dives, cases and interview practice."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("You can change this any day on the Today screen.", style = MaterialTheme.typography.bodySmall)
                    Text("Pace", style = MaterialTheme.typography.titleMedium)
                    ChipRow(Pace.entries, d.pace, { it.label }) { vm.draft = d.copy(pace = it) }
                }
                3 -> {
                    Text("When will you study?", style = MaterialTheme.typography.headlineSmall)
                    StudyDayChips(d.studyDays) { days ->
                        vm.draft = d.copy(studyDays = days, weeklyGoal = days.size.coerceAtLeast(1))
                    }
                    Text("Weekly goal: ${d.weeklyGoal} day(s)", style = MaterialTheme.typography.titleMedium)
                    ChipRow((1..7).toList(), d.weeklyGoal, { "$it" }) { vm.draft = d.copy(weeklyGoal = it) }
                    OutlinedButton(onClick = { pickTime = true }) { Text("Preferred time: ${formatMinuteOfDay(d.preferredMinuteOfDay)}") }
                }
                4 -> {
                    Text("Reminders", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Choose how many reminders you want — zero is fine. They respect quiet hours and stop once you've learned today.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ReminderEditor(d.reminders, d.quietStartMinute, d.quietEndMinute) { vm.draft = d.copy(reminders = it, remindersEnabled = it.isNotEmpty()) }
                    Text("Accountability tone", style = MaterialTheme.typography.titleMedium)
                    ChipRow(NudgeTone.entries, d.tone, { it.label }) { vm.draft = d.copy(tone = it) }
                    SwitchRow("Light-hearted warnings if I miss planned days", d.creativeWarnings) { vm.draft = d.copy(creativeWarnings = it) }
                }
                else -> {
                    Text("Language and interests", style = MaterialTheme.typography.headlineSmall)
                    Text("Teach me in", style = MaterialTheme.typography.titleMedium)
                    ChipRow(LanguageMode.entries, d.language, { it.label }) { vm.draft = d.copy(language = it) }
                    Text("Simple English, natural Tamil-English (Thanglish), or both side by side.", style = MaterialTheme.typography.bodySmall)
                    Text("I'm especially interested in", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        INTERESTS.forEach { i ->
                            val on = i in d.interests
                            FilterChip(selected = on, onClick = { vm.draft = d.copy(interests = if (on) d.interests - i else d.interests + i) }, label = { Text(i) })
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (vm.step > 0) OutlinedButton(onClick = { vm.step-- }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("Back") }
            else Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    if (vm.step < STEPS - 1) vm.step++
                    else if (d.reminders.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(context)) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else finish()
                },
                enabled = vm.step != 3 || d.studyDays.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) { Text(if (vm.step < STEPS - 1) "Next" else "Start learning") }
        }
    }

    if (pickTime) {
        TimePickerDialog(d.preferredMinuteOfDay, "Preferred study time", onDismiss = { pickTime = false }) { m ->
            // Keep the first lesson reminder aligned with the chosen study time.
            val first = d.reminders.indexOfFirst { it.kind == ReminderKind.LESSON }
            val reminders = d.reminders.mapIndexed { i, r -> if (i == first) r.copy(minuteOfDay = m) else r }
            vm.draft = d.copy(preferredMinuteOfDay = m, reminders = reminders)
            pickTime = false
        }
    }
}
