package com.pmbible.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pmbible.app.PmBibleApp
import com.pmbible.app.R
import com.pmbible.app.ui.MainActivity
import com.pmbible.core.domain.Accountability
import com.pmbible.core.domain.ReminderContext
import com.pmbible.core.domain.ReminderCopy
import com.pmbible.core.domain.ReminderPlanner
import com.pmbible.core.model.ReminderKind
import com.pmbible.core.model.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

/** Destinations a notification can open; read by MainActivity. */
object Destinations {
    const val EXTRA_OPEN = "open"
    const val SESSION = "session"
    const val REVIEW = "review"
    const val PROGRESS = "progress"
}

/**
 * Schedules reminders with inexact, Doze-tolerant alarms. Exact delivery is not guaranteed:
 * Android may batch alarms, and some manufacturers (e.g. Xiaomi/MIUI) stop alarms for apps
 * without "autostart". Rules are re-checked at fire time, so stale alarms never nag.
 */
class ReminderScheduler(private val context: Context) {

    private val alarms: AlarmManager = context.getSystemService(AlarmManager::class.java)!!

    fun reschedule(prefs: UserPrefs) {
        cancelAll()
        if (!prefs.onboarded) return
        val now = ZonedDateTime.now()
        prefs.reminders.forEach { r ->
            ReminderPlanner.nextTrigger(r, prefs, now)?.let { schedule(r.id, r.kind.name, it.toInstant().toEpochMilli()) }
        }
        ReminderPlanner.nextWeeklyRecap(prefs, now)?.let {
            schedule(ReminderPlanner.WEEKLY_RECAP_ID, KIND_RECAP, it.toInstant().toEpochMilli())
        }
    }

    private fun schedule(id: Int, kind: String, atMillis: Long) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent(id, kind))
    }

    private fun cancelAll() {
        (1..MAX_REMINDERS).forEach { alarms.cancel(pendingIntent(it, null)) }
        alarms.cancel(pendingIntent(ReminderPlanner.WEEKLY_RECAP_ID, null))
    }

    // Request code identifies the alarm; extras don't affect PendingIntent equality, so cancel matches.
    private fun pendingIntent(id: Int, kind: String?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE)
            .putExtra(EXTRA_ID, id)
            .apply { if (kind != null) putExtra(EXTRA_KIND, kind) }
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val ACTION_FIRE = "com.pmbible.app.REMINDER"
        const val EXTRA_ID = "id"
        const val EXTRA_KIND = "kind"
        const val KIND_RECAP = "RECAP"
        const val MAX_REMINDERS = 6
    }
}

object Notifications {
    private const val CHANNEL = "reminders"
    private const val ID_BASE = 1000
    private const val ID_RECAP = 2000

    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.channel_reminders_desc) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun post(context: Context, kind: String, copy: ReminderCopy) {
        if (!canPost(context)) return
        ensureChannel(context)
        val destination = when (kind) {
            ReminderKind.REVIEW.name -> Destinations.REVIEW
            ReminderScheduler.KIND_RECAP -> Destinations.PROGRESS
            else -> Destinations.SESSION
        }
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(Destinations.EXTRA_OPEN, destination)
        val contentIntent = PendingIntent.getActivity(context, kind.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_pm)
            .setContentTitle(copy.title)
            .setContentText(copy.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy.body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        // One id per kind: a newer reminder replaces an older one instead of stacking duplicates.
        try {
            NotificationManagerCompat.from(context).notify(idFor(kind), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing to do.
        }
    }

    /** Called when today's learning is done so earlier reminders don't linger. */
    fun clearLearningReminders(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        ReminderKind.entries.forEach { nm.cancel(idFor(it.name)) }
    }

    private fun idFor(kind: String) =
        if (kind == ReminderScheduler.KIND_RECAP) ID_RECAP else ID_BASE + (ReminderKind.entries.firstOrNull { it.name == kind }?.ordinal ?: 0)
}

/** Handles reminder alarms and system events that require rescheduling. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as PmBibleApp).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = container.prefs.current()
                if (intent.action == ReminderScheduler.ACTION_FIRE) fire(context, intent.getStringExtra(ReminderScheduler.EXTRA_KIND), prefs)
                container.reminders.reschedule(prefs)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, kind: String?, prefs: UserPrefs) {
        if (kind == null) return
        val container = (context.applicationContext as PmBibleApp).container
        val snapshot = container.progress.snapshot.first()
        val today = LocalDate.now().toEpochDay()
        val status = Accountability.weekStatus(prefs, snapshot.activeDays, today)
        if (kind == ReminderScheduler.KIND_RECAP) {
            if (prefs.remindersEnabled && prefs.weeklyRecap) {
                Notifications.post(context, kind, ReminderPlanner.recapCopy(status.daysActive, prefs.weeklyGoal))
            }
            return
        }
        val reminderKind = ReminderKind.entries.firstOrNull { it.name == kind } ?: return
        val ctx = ReminderContext(
            sessionDoneToday = (snapshot.minutesByDay[today] ?: 0) > 0,
            dueReviews = snapshot.dueReviews(today).size,
            missedPlannedStreak = status.missedPlannedStreak,
        )
        if (ReminderPlanner.shouldFire(reminderKind, prefs, ZonedDateTime.now(), ctx)) {
            Notifications.post(context, kind, ReminderPlanner.copy(reminderKind, prefs.tone, ctx))
        }
    }
}
