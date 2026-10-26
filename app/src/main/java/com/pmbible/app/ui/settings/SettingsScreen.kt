package com.pmbible.app.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pmbible.app.notifications.Notifications
import com.pmbible.app.notifications.ReminderScheduler
import com.pmbible.app.ui.AppViewModel
import com.pmbible.app.ui.components.AppCard
import com.pmbible.app.ui.components.Callout
import com.pmbible.app.ui.components.ScreenScaffold
import com.pmbible.app.ui.components.SectionHeader
import com.pmbible.app.ui.components.TimePickerDialog
import com.pmbible.app.ui.components.block
import com.pmbible.app.ui.components.formatMinuteOfDay
import com.pmbible.core.domain.ReminderCopy
import com.pmbible.core.domain.ReminderPlanner
import com.pmbible.core.model.LanguageMode
import com.pmbible.core.model.NudgeTone
import com.pmbible.core.model.Pace
import com.pmbible.core.model.Reminder
import com.pmbible.core.model.ReminderKind
import com.pmbible.core.model.ThemeMode
import com.pmbible.core.model.UserPrefs
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

val MINUTE_CHOICES = listOf(5, 10, 15, 20, 30, 45, 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(app: AppViewModel, onBack: () -> Unit) {
    val prefsState by app.prefs.collectAsState()
    val library by app.library.collectAsState()
    val p = prefsState ?: return
    val update: ((UserPrefs) -> UserPrefs) -> Unit = { app.updatePrefs(it) }
    var confirmReset by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<Pair<String, Int>?>(null) }

    ScreenScaffold("Settings", onBack) {
        block {
            SectionHeader("Profile")
            var name by rememberSaveable { mutableStateOf(p.name) }
            var goal by rememberSaveable { mutableStateOf(p.goal) }
            OutlinedTextField(name, { name = it }, label = { Text("Your name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(goal, { goal = it }, label = { Text("Your goal") }, modifier = Modifier.fillMaxWidth())
            val changed = name.trim() != p.name || goal.trim() != p.goal
            OutlinedButton(onClick = { update { it.copy(name = name.trim(), goal = goal.trim()) } }, enabled = changed) {
                Text(if (changed) "Save profile" else "Profile saved")
            }
        }

        block {
            SectionHeader("Learning")
            Text("Default daily time", style = MaterialTheme.typography.labelLarge)
            ChipRow(MINUTE_CHOICES, p.dailyMinutes, { "$it min" }) { m -> update { it.copy(dailyMinutes = m) } }
            Text("Pace — how many new lessons a session may include", style = MaterialTheme.typography.labelLarge)
            ChipRow(Pace.entries, p.pace, { "${it.label} (${it.maxNewLessons})" }) { v -> update { it.copy(pace = v) } }
            Text("Teaching language", style = MaterialTheme.typography.labelLarge)
            ChipRow(LanguageMode.entries, p.language, { it.label }) { v -> update { it.copy(language = v) } }
            Text(
                "Thanglish covers each lesson's core explanation, analogy and takeaway; deep dives and questions are in English.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        block {
            SectionHeader("Schedule")
            Text("Study days", style = MaterialTheme.typography.labelLarge)
            StudyDayChips(p.studyDays) { days -> update { it.copy(studyDays = days, weeklyGoal = it.weeklyGoal.coerceAtMost(days.size.coerceAtLeast(1))) } }
            Text("Weekly goal: ${p.weeklyGoal} day(s)", style = MaterialTheme.typography.labelLarge)
            ChipRow((1..7).toList(), p.weeklyGoal, { "$it" }) { g -> update { it.copy(weeklyGoal = g) } }
            OutlinedButton(onClick = { picker = "preferred" to p.preferredMinuteOfDay }) {
                Text("Preferred study time: ${formatMinuteOfDay(p.preferredMinuteOfDay)}")
            }
        }

        block {
            SectionHeader("Reminders")
            SwitchRow("Reminders on", p.remindersEnabled) { on -> update { it.copy(remindersEnabled = on) } }
            if (p.remindersEnabled) {
                ReminderEditor(p.reminders, p.quietStartMinute, p.quietEndMinute, onChange = { list -> update { it.copy(reminders = list) } })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker = "quietStart" to p.quietStartMinute }) { Text("Quiet from ${formatMinuteOfDay(p.quietStartMinute)}") }
                    OutlinedButton(onClick = { picker = "quietEnd" to p.quietEndMinute }) { Text("until ${formatMinuteOfDay(p.quietEndMinute)}") }
                }
                SwitchRow("Weekly recap (Sunday 7 PM)", p.weeklyRecap) { on -> update { it.copy(weeklyRecap = on) } }
                Text(
                    "Reminders only fire on study days, outside quiet hours, and never after you've already learned today.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { NotificationStatus() }

        block {
            SectionHeader("Accountability")
            Text("Nudge tone", style = MaterialTheme.typography.labelLarge)
            ChipRow(NudgeTone.entries, p.tone, { it.label }) { v -> update { it.copy(tone = v) } }
            SwitchRow("Light-hearted warnings after missed planned days", p.creativeWarnings) { on -> update { it.copy(creativeWarnings = on) } }
        }

        block {
            SectionHeader("Display")
            ChipRow(ThemeMode.entries, p.themeMode, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) { v -> update { it.copy(themeMode = v) } }
            Text("Text size: ${(p.textScale * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            var scale by remember(p.textScale) { mutableStateOf(p.textScale) }
            Slider(
                value = scale,
                onValueChange = { scale = it },
                onValueChangeFinished = { update { it.copy(textScale = scale) } },
                valueRange = 0.85f..1.4f,
                steps = 10,
            )
        }

        block {
            SectionHeader("Your data")
            Text(
                "Everything — progress, notes, answers — stays on this device. Nothing is sent to a server.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { confirmReset = true }) { Text("Reset learning progress…") }
        }

        block {
            SectionHeader("About this content")
            val m = library?.curriculum?.manifest
            if (m != null) {
                Text("Content version ${m.contentVersion}, reviewed ${m.reviewedOn}", style = MaterialTheme.typography.bodyMedium)
                Text(m.coverageNote, style = MaterialTheme.typography.bodySmall)
            }
            Callout(
                "This version works fully offline with curated content. It has no AI tutor or live news: those need a secure server " +
                    "and a paid AI API key, which are deliberately not built into the app.",
            )
        }
    }

    picker?.let { (which, minute) ->
        TimePickerDialog(
            initialMinute = minute,
            title = when (which) { "quietStart" -> "Quiet hours start"; "quietEnd" -> "Quiet hours end"; else -> "Preferred study time" },
            onDismiss = { picker = null },
            onConfirm = { m ->
                when (which) {
                    "quietStart" -> update { it.copy(quietStartMinute = m) }
                    "quietEnd" -> update { it.copy(quietEndMinute = m) }
                    else -> update { it.copy(preferredMinuteOfDay = m) }
                }
                picker = null
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all learning progress?") },
            text = { Text("This permanently deletes lesson progress, reviews, answers, notes and bookmarks on this device. Your settings stay.") },
            confirmButton = { TextButton(onClick = { app.resetProgress(); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudyDayChips(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { d ->
            val on = d in days
            FilterChip(
                selected = on,
                onClick = { onChange(if (on) days - d else days + d) },
                label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
            )
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Edit the reminder list: time and type per reminder, add up to the scheduler's limit, or remove. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderEditor(reminders: List<Reminder>, quietStart: Int, quietEnd: Int, onChange: (List<Reminder>) -> Unit) {
    var editing by remember { mutableStateOf<Reminder?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (reminders.isEmpty()) Text("No reminders — that's fine. Learning never depends on notifications.", style = MaterialTheme.typography.bodyMedium)
        reminders.forEach { r ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { editing = r }) { Text(formatMinuteOfDay(r.minuteOfDay), style = MaterialTheme.typography.titleMedium) }
                    Spacer(Modifier.weight(1f))
                    if (ReminderPlanner.isQuiet(r.minuteOfDay, quietStart, quietEnd)) {
                        Text("In quiet hours — won't fire", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = { onChange(reminders - r) }) { Icon(Icons.Outlined.Delete, contentDescription = "Remove reminder") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReminderKind.entries.forEach { k ->
                        FilterChip(
                            selected = r.kind == k,
                            onClick = { onChange(reminders.map { if (it.id == r.id) it.copy(kind = k) else it }) },
                            label = { Text(k.label) },
                        )
                    }
                }
            }
        }
        if (reminders.size < ReminderScheduler.MAX_REMINDERS) {
            OutlinedButton(onClick = {
                val id = (1..ReminderScheduler.MAX_REMINDERS).first { i -> reminders.none { it.id == i } }
                val kind = if (reminders.any { it.kind == ReminderKind.LESSON }) ReminderKind.REVIEW else ReminderKind.LESSON
                onChange(reminders + Reminder(id, 19 * 60, kind))
            }) { Text("Add reminder") }
        }
        Text(
            "Lesson: today's session. Review: only when reviews are due. Accountability nudge: only after a missed planned day.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    editing?.let { r ->
        TimePickerDialog(r.minuteOfDay, "Reminder time", onDismiss = { editing = null }) { m ->
            onChange(reminders.map { if (it.id == r.id) it.copy(minuteOfDay = m) else it }.sortedBy { it.minuteOfDay })
            editing = null
        }
    }
}

/** Live notification permission state, test notification, and battery guidance for aggressive OEMs. */
@Composable
private fun NotificationStatus() {
    val context = LocalContext.current
    var canPost by remember { mutableStateOf(Notifications.canPost(context)) }
    var testSent by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canPost = Notifications.canPost(context) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canPost = Notifications.canPost(context) }
    val manufacturer = Build.MANUFACTURER.lowercase()
    val xiaomiFamily = listOf("xiaomi", "redmi", "poco").any { it in manufacturer }

    AppCard {
        Text("Notifications", style = MaterialTheme.typography.titleMedium)
        Text(
            if (canPost) "Allowed. Reminders can appear." else "Not allowed. Reminders won't appear, but everything else works normally.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!canPost) {
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("Allow notifications") }
            Text(
                "If you denied permission before, Android may not ask again — use App settings › Notifications instead.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            OutlinedButton(onClick = {
                Notifications.post(context, ReminderKind.LESSON.name, ReminderCopy("Test reminder", "Notifications work. Tap to open today's session."))
                testSent = true
            }) { Text(if (testSent) "Test sent" else "Send a test notification") }
        }
        Text(
            "Reminders use battery-friendly alarms, so Android may deliver them a few minutes late.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (xiaomiFamily) {
            Callout(
                "On Xiaomi, Redmi and POCO phones, MIUI/HyperOS can block reminders for apps it puts to sleep. " +
                    "In App settings, enable Autostart and set Battery saver to “No restrictions” for PM Bible.",
            )
        }
        OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }) { Text("Open app settings") }
    }
}
