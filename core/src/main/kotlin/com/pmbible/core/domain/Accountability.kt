package com.pmbible.core.domain

import com.pmbible.core.model.NudgeTone
import com.pmbible.core.model.UserPrefs
import java.time.DayOfWeek
import java.time.LocalDate

data class WeekStatus(
    val daysActive: Int,
    val goal: Int,
    /** Consecutive planned study days missed, counting back from yesterday. */
    val missedPlannedStreak: Int,
    val daysSinceActive: Int?,
    val activeToday: Boolean,
)

enum class NudgeKind { WELCOME, ON_TRACK, GOAL_MET, GENTLE_MISS, CREATIVE_WARNING, WELCOME_BACK, REST_DAY }

data class Nudge(
    val kind: NudgeKind,
    val title: String,
    val message: String,
    /** When set, the UI offers a short catch-up session of this many minutes. */
    val catchUpMinutes: Int? = null,
)

/**
 * Consistency without punishment: progress is never reset, misses are only counted on
 * the learner's own planned days, and every message offers a small way back in.
 */
object Accountability {

    fun weekStatus(prefs: UserPrefs, activeDays: Set<Long>, today: Long): WeekStatus {
        val date = LocalDate.ofEpochDay(today)
        val monday = date.with(DayOfWeek.MONDAY).toEpochDay()
        val daysActive = activeDays.count { it in monday..today }
        var missed = 0
        var day = today - 1
        while (day >= prefs.startDay && missed < 60) {
            if (day in activeDays) break
            if (LocalDate.ofEpochDay(day).dayOfWeek in prefs.studyDays) missed++
            day--
        }
        val last = activeDays.filter { it <= today }.maxOrNull()
        return WeekStatus(
            daysActive = daysActive,
            goal = prefs.weeklyGoal,
            missedPlannedStreak = missed,
            daysSinceActive = last?.let { (today - it).toInt() },
            activeToday = today in activeDays,
        )
    }

    fun nudge(prefs: UserPrefs, status: WeekStatus, dueReviews: Int, today: Long): Nudge {
        val isStudyDay = LocalDate.ofEpochDay(today).dayOfWeek in prefs.studyDays
        val pick = { options: List<Pair<String, String>> -> options[(today % options.size).toInt()] }
        return when {
            status.daysSinceActive == null -> Nudge(
                NudgeKind.WELCOME, "Your first session",
                "Start with a few minutes — the plan adapts to whatever time you have.",
            )
            status.activeToday && status.daysActive >= status.goal -> Nudge(
                NudgeKind.GOAL_MET, "Weekly goal reached",
                "${status.daysActive} of ${status.goal} days this week. Anything more is a bonus, not an obligation.",
            )
            status.activeToday -> Nudge(
                NudgeKind.ON_TRACK, "Done for today",
                "${status.daysActive} of ${status.goal} days this week. Come back tomorrow or explore the Library freely.",
            )
            (status.daysSinceActive ?: 0) >= 7 -> Nudge(
                NudgeKind.WELCOME_BACK, "Welcome back",
                "Everything you learned is still here. Start with a 10-minute catch-up: a few reviews, then pick up where you stopped.",
                catchUpMinutes = 10,
            )
            status.missedPlannedStreak >= 2 && prefs.creativeWarnings -> {
                val (title, msg) = pick(creativeWarnings(prefs.tone, status.missedPlannedStreak, dueReviews))
                Nudge(NudgeKind.CREATIVE_WARNING, title, msg, catchUpMinutes = minOf(prefs.dailyMinutes, 10))
            }
            status.missedPlannedStreak >= 1 -> Nudge(
                NudgeKind.GENTLE_MISS, "Pick up where you left off",
                "Missed a planned day? No problem — progress is never erased. A short catch-up keeps reviews manageable.",
                catchUpMinutes = minOf(prefs.dailyMinutes, 10),
            )
            !isStudyDay -> Nudge(
                NudgeKind.REST_DAY, "Rest day",
                "Today isn't one of your study days. Learn if you feel like it — or rest, that's part of the plan too.",
            )
            else -> Nudge(
                NudgeKind.ON_TRACK, "Today's session is ready",
                "${status.daysActive} of ${status.goal} days so far this week.",
            )
        }
    }

    private fun creativeWarnings(tone: NudgeTone, missed: Int, due: Int): List<Pair<String, String>> = when (tone) {
        NudgeTone.GENTLE -> listOf(
            "A small step counts" to "It's been $missed planned days. Five minutes today is enough to get moving again.",
            "Your concepts are waiting" to "Nothing is lost. A short review today makes the next lesson easier.",
        )
        NudgeTone.PLAYFUL -> listOf(
            "Your backlog formed a committee" to "$due reviews are holding a meeting about you. Five minutes dissolves the committee.",
            "RICE score of today's session" to "Reach: you. Impact: high. Confidence: 100%. Effort: 10 minutes. The maths says ship it.",
            "Roadmap slip detected" to "$missed planned days slipped. Every PM has been here — re-plan, don't despair.",
            "Your North Star is blinking" to "Not angry, just blinking. One small session turns it steady again.",
        )
        NudgeTone.COACH -> listOf(
            "Reset the cadence" to "$missed planned days missed. Commit to a 10-minute catch-up now; consistency beats intensity.",
            "Protect the habit" to "Skipping compounds like interest. One focused session today breaks the pattern.",
        )
    }
}
