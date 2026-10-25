package com.pmbible.core.domain

import com.pmbible.core.model.NudgeTone
import com.pmbible.core.model.Reminder
import com.pmbible.core.model.ReminderKind
import com.pmbible.core.model.UserPrefs
import java.time.DayOfWeek
import java.time.ZonedDateTime

/** Context known at the moment a reminder could fire. */
data class ReminderContext(
    val sessionDoneToday: Boolean,
    val dueReviews: Int,
    val missedPlannedStreak: Int,
)

data class ReminderCopy(val title: String, val body: String)

/**
 * Pure reminder rules. The Android layer only schedules the instants computed here
 * and re-checks [shouldFire] when an alarm arrives, because state may have changed.
 */
object ReminderPlanner {

    const val WEEKLY_RECAP_ID = 100
    private const val WEEKLY_RECAP_MINUTE = 19 * 60

    fun isQuiet(minuteOfDay: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> minuteOfDay in start until end
        else -> minuteOfDay >= start || minuteOfDay < end // wraps past midnight
    }

    /** Next time [reminder] may fire, skipping non-study days and quiet hours; null if never. */
    fun nextTrigger(reminder: Reminder, prefs: UserPrefs, now: ZonedDateTime): ZonedDateTime? {
        if (!prefs.remindersEnabled || prefs.studyDays.isEmpty()) return null
        if (isQuiet(reminder.minuteOfDay, prefs.quietStartMinute, prefs.quietEndMinute)) return null
        return firstOccurrence(now, reminder.minuteOfDay) { it.dayOfWeek in prefs.studyDays }
    }

    fun nextWeeklyRecap(prefs: UserPrefs, now: ZonedDateTime): ZonedDateTime? {
        if (!prefs.remindersEnabled || !prefs.weeklyRecap) return null
        if (isQuiet(WEEKLY_RECAP_MINUTE, prefs.quietStartMinute, prefs.quietEndMinute)) return null
        return firstOccurrence(now, WEEKLY_RECAP_MINUTE) { it.dayOfWeek == DayOfWeek.SUNDAY }
    }

    private fun firstOccurrence(now: ZonedDateTime, minuteOfDay: Int, allowed: (ZonedDateTime) -> Boolean): ZonedDateTime? {
        for (offset in 0..7L) {
            val candidate = now.toLocalDate().plusDays(offset)
                .atTime(minuteOfDay / 60, minuteOfDay % 60)
                .atZone(now.zone)
            if (candidate.isAfter(now) && allowed(candidate)) return candidate
        }
        return null
    }

    /** Final check when an alarm fires: never remind about finished work or outside the learner's rules. */
    fun shouldFire(kind: ReminderKind, prefs: UserPrefs, now: ZonedDateTime, ctx: ReminderContext): Boolean {
        if (!prefs.remindersEnabled) return false
        if (isQuiet(now.hour * 60 + now.minute, prefs.quietStartMinute, prefs.quietEndMinute)) return false
        if (now.dayOfWeek !in prefs.studyDays) return false
        if (ctx.sessionDoneToday) return false
        return when (kind) {
            ReminderKind.LESSON -> true
            ReminderKind.REVIEW -> ctx.dueReviews > 0
            ReminderKind.NUDGE -> ctx.missedPlannedStreak >= 1
        }
    }

    fun copy(kind: ReminderKind, tone: NudgeTone, ctx: ReminderContext): ReminderCopy = when (kind) {
        ReminderKind.LESSON -> when (tone) {
            NudgeTone.GENTLE -> ReminderCopy("Today's lesson is ready", "A short session is waiting whenever you are.")
            NudgeTone.PLAYFUL -> ReminderCopy("Your PM brain called", "It wants today's lesson. It promised to be quick.")
            NudgeTone.COACH -> ReminderCopy("Time to learn", "Your planned session is ready. Start now and keep the cadence.")
        }
        ReminderKind.REVIEW -> ReminderCopy(
            "${ctx.dueReviews} concept${if (ctx.dueReviews == 1) "" else "s"} to refresh",
            "A quick review now keeps them from fading.",
        )
        ReminderKind.NUDGE -> when (tone) {
            NudgeTone.GENTLE -> ReminderCopy("Pick up where you left off", "Nothing is lost. Five minutes gets you going again.")
            NudgeTone.PLAYFUL -> ReminderCopy("Roadmap slip detected", "Even great PMs re-plan. Five minutes puts you back on track.")
            NudgeTone.COACH -> ReminderCopy("Reset the cadence", "You've missed ${ctx.missedPlannedStreak} planned day(s). A 10-minute catch-up fixes it.")
        }
    }

    fun recapCopy(daysActive: Int, goal: Int): ReminderCopy =
        ReminderCopy("Your weekly recap", "$daysActive of $goal study days this week. See what you learned and what's next.")
}
