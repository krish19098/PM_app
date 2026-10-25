package com.pmbible.core

import com.pmbible.core.domain.Accountability
import com.pmbible.core.domain.NudgeKind
import com.pmbible.core.domain.ReminderContext
import com.pmbible.core.domain.ReminderPlanner
import com.pmbible.core.model.NudgeTone
import com.pmbible.core.model.Reminder
import com.pmbible.core.model.ReminderKind
import com.pmbible.core.model.UserPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RemindersAndAccountabilityTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    // Wednesday 2026-10-21 10:00 IST
    private val now = ZonedDateTime.of(2026, 10, 21, 10, 0, 0, 0, zone)
    private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    private val prefs = UserPrefs(onboarded = true, studyDays = weekdays, quietStartMinute = 22 * 60, quietEndMinute = 7 * 60)

    @Test
    fun quietHoursWrapPastMidnight() {
        assertTrue(ReminderPlanner.isQuiet(23 * 60, 22 * 60, 7 * 60))
        assertTrue(ReminderPlanner.isQuiet(6 * 60 + 59, 22 * 60, 7 * 60))
        assertFalse(ReminderPlanner.isQuiet(7 * 60, 22 * 60, 7 * 60))
        assertFalse(ReminderPlanner.isQuiet(12 * 60, 13 * 60, 13 * 60))
    }

    @Test
    fun laterTodayFiresToday() {
        val next = ReminderPlanner.nextTrigger(Reminder(1, 18 * 60, ReminderKind.LESSON), prefs, now)
        assertEquals(now.withHour(18).withMinute(0), next)
    }

    @Test
    fun pastTimeMovesToNextStudyDaySkippingWeekend() {
        val friday = ZonedDateTime.of(2026, 10, 23, 20, 0, 0, 0, zone)
        val next = ReminderPlanner.nextTrigger(Reminder(1, 8 * 60, ReminderKind.LESSON), prefs, friday)!!
        assertEquals(DayOfWeek.MONDAY, next.dayOfWeek)
        assertEquals(8, next.hour)
    }

    @Test
    fun reminderInsideQuietHoursNeverSchedules() {
        assertNull(ReminderPlanner.nextTrigger(Reminder(1, 23 * 60, ReminderKind.LESSON), prefs, now))
    }

    @Test
    fun disabledOrNoStudyDaysMeansNoReminders() {
        val r = Reminder(1, 18 * 60, ReminderKind.LESSON)
        assertNull(ReminderPlanner.nextTrigger(r, prefs.copy(remindersEnabled = false), now))
        assertNull(ReminderPlanner.nextTrigger(r, prefs.copy(studyDays = emptySet()), now))
    }

    @Test
    fun noReminderAboutWorkAlreadyDone() {
        val done = ReminderContext(sessionDoneToday = true, dueReviews = 5, missedPlannedStreak = 0)
        ReminderKind.entries.forEach { assertFalse(ReminderPlanner.shouldFire(it, prefs, now, done)) }
    }

    @Test
    fun reviewAndNudgeRemindersOnlyFireWhenRelevant() {
        val ctx = ReminderContext(sessionDoneToday = false, dueReviews = 0, missedPlannedStreak = 0)
        assertTrue(ReminderPlanner.shouldFire(ReminderKind.LESSON, prefs, now, ctx))
        assertFalse(ReminderPlanner.shouldFire(ReminderKind.REVIEW, prefs, now, ctx))
        assertFalse(ReminderPlanner.shouldFire(ReminderKind.NUDGE, prefs, now, ctx))
        assertTrue(ReminderPlanner.shouldFire(ReminderKind.NUDGE, prefs, now, ctx.copy(missedPlannedStreak = 2)))
    }

    @Test
    fun weeklyRecapIsSundayEvening() {
        val next = ReminderPlanner.nextWeeklyRecap(prefs, now)!!
        assertEquals(DayOfWeek.SUNDAY, next.dayOfWeek)
        assertEquals(19, next.hour)
    }

    @Test
    fun dstStyleZonesStillProduceFutureTimes() {
        val ny = ZonedDateTime.of(2026, 3, 7, 23, 0, 0, 0, ZoneId.of("America/New_York"))
        val next = ReminderPlanner.nextTrigger(Reminder(1, 8 * 60, ReminderKind.LESSON), prefs.copy(studyDays = DayOfWeek.entries.toSet()), ny)!!
        assertTrue(next.isAfter(ny))
        assertEquals(8, next.hour)
    }

    // --- Accountability ---

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test
    fun missesCountOnlyPlannedDaysAndStopAtLastActiveDay() {
        val today = day(2026, 10, 21) // Wednesday
        val p = prefs.copy(startDay = day(2026, 10, 1))
        // Active last Friday; Sat/Sun are not study days; Mon and Tue missed.
        val status = Accountability.weekStatus(p, setOf(day(2026, 10, 16)), today)
        assertEquals(2, status.missedPlannedStreak)
        assertEquals(5, status.daysSinceActive)
    }

    @Test
    fun missesBeforeOnboardingAreNeverCounted() {
        val today = day(2026, 10, 21)
        val status = Accountability.weekStatus(prefs.copy(startDay = today), emptySet(), today)
        assertEquals(0, status.missedPlannedStreak)
    }

    @Test
    fun creativeWarningIsOptionalAndNeverShaming() {
        val today = day(2026, 10, 21)
        val p = prefs.copy(startDay = day(2026, 10, 1), tone = NudgeTone.PLAYFUL)
        val status = Accountability.weekStatus(p, setOf(day(2026, 10, 16)), today)
        val nudge = Accountability.nudge(p, status, dueReviews = 7, today = today)
        assertEquals(NudgeKind.CREATIVE_WARNING, nudge.kind)
        assertTrue(nudge.catchUpMinutes != null)
        val off = Accountability.nudge(p.copy(creativeWarnings = false), status, 7, today)
        assertEquals(NudgeKind.GENTLE_MISS, off.kind)
        val banned = listOf("lazy", "fail", "disappoint", "shame", "lost your")
        (NudgeTone.entries).forEach { tone ->
            (0..5).forEach { offset ->
                val n = Accountability.nudge(p.copy(tone = tone), status, 7, today + offset * 0)
                banned.forEach { assertFalse("${n.message} contains $it", n.message.lowercase().contains(it)) }
            }
        }
    }

    @Test
    fun longAbsenceGetsWelcomeBackNotWarning() {
        val today = day(2026, 10, 21)
        val p = prefs.copy(startDay = day(2026, 9, 1))
        val status = Accountability.weekStatus(p, setOf(day(2026, 10, 5)), today)
        val nudge = Accountability.nudge(p, status, 30, today)
        assertEquals(NudgeKind.WELCOME_BACK, nudge.kind)
        assertTrue(nudge.message.contains("still here"))
    }
}
