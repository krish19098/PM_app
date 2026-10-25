package com.pmbible.core.model

import java.time.DayOfWeek

/*
 * Learner state. Days are epoch days (LocalDate.toEpochDay) in the learner's
 * current time zone, which keeps scheduling maths simple and testable.
 */

enum class Segment { QUICK, PRACTICE, EXERCISE, DEEP }

data class LessonState(
    val lessonId: String,
    val quickDone: Boolean = false,
    val practiceDone: Boolean = false,
    val exerciseDone: Boolean = false,
    val deepDone: Boolean = false,
    /** Set when the learner placed past this lesson during onboarding. */
    val placed: Boolean = false,
    val completedDay: Long? = null,
    val lastOpenedDay: Long? = null,
) {
    /** Completion = core understanding + practice. Deep dive and exercise add depth and mastery. */
    val completed: Boolean get() = placed || (quickDone && practiceDone)
    val inProgress: Boolean get() = !completed && (quickDone || practiceDone || exerciseDone || deepDone)

    fun isDone(segment: Segment) = when (segment) {
        Segment.QUICK -> quickDone
        Segment.PRACTICE -> practiceDone
        Segment.EXERCISE -> exerciseDone
        Segment.DEEP -> deepDone
    }
}

/** Spaced-repetition state of a single question (Leitner box). */
data class ReviewState(
    val questionId: String,
    val lessonId: String,
    val box: Int,
    val dueDay: Long,
    val lastDay: Long,
    val correctCount: Int,
    val wrongCount: Int,
    val lastCorrect: Boolean,
)

/** Evidence of applying a concept: exercise self-check, checkpoint, case or interview. Score 0..1. */
data class ApplicationScore(
    val lessonId: String,
    val sourceId: String,
    val score: Double,
    val day: Long,
)

data class Attempt(
    val questionId: String,
    val lessonId: String,
    val correct: Boolean,
    val day: Long,
)

data class InterviewScore(
    val questionId: String,
    val category: InterviewCategory,
    /** Self-rated rubric fraction 0..1. */
    val score: Double,
    val day: Long,
)

data class ProgressSnapshot(
    val lessons: Map<String, LessonState> = emptyMap(),
    val reviews: Map<String, ReviewState> = emptyMap(),
    val appScores: List<ApplicationScore> = emptyList(),
    val attempts: List<Attempt> = emptyList(),
    val interviewScores: List<InterviewScore> = emptyList(),
    val checkpointsDone: Set<String> = emptySet(),
    val casesDone: Set<String> = emptySet(),
    /** Minutes of learning recorded per epoch day. */
    val minutesByDay: Map<Long, Int> = emptyMap(),
) {
    val activeDays: Set<Long> get() = minutesByDay.filterValues { it > 0 }.keys
    fun lesson(id: String) = lessons[id] ?: LessonState(id)
    fun dueReviews(today: Long) = reviews.values.filter { it.dueDay <= today }
}

enum class LanguageMode(val label: String) { ENGLISH("English"), THANGLISH("Thanglish"), BOTH("Both") }

enum class Pace(val label: String, val maxNewLessons: Int) {
    RELAXED("Relaxed", 1), STEADY("Steady", 2), FAST("Fast", 3)
}

enum class NudgeTone(val label: String) { GENTLE("Gentle"), PLAYFUL("Playful"), COACH("Coach") }

enum class StartingPoint(val label: String, val placedUnits: Int) {
    ZERO("I'm new to PM", 0),
    BASICS("I know the basics", 2),
    EXPERIENCED("I've worked with PMs", 4),
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ReminderKind(val label: String) {
    LESSON("Lesson"), REVIEW("Review"), NUDGE("Accountability nudge")
}

data class Reminder(val id: Int, val minuteOfDay: Int, val kind: ReminderKind)

data class UserPrefs(
    val onboarded: Boolean = false,
    val name: String = "",
    val goal: String = "",
    val startingPoint: StartingPoint = StartingPoint.ZERO,
    val dailyMinutes: Int = 15,
    val studyDays: Set<DayOfWeek> = DayOfWeek.entries.toSet() - DayOfWeek.SUNDAY,
    val weeklyGoal: Int = 5,
    val preferredMinuteOfDay: Int = 8 * 60,
    val remindersEnabled: Boolean = true,
    val reminders: List<Reminder> = listOf(Reminder(1, 8 * 60, ReminderKind.LESSON)),
    val quietStartMinute: Int = 22 * 60,
    val quietEndMinute: Int = 7 * 60,
    val weeklyRecap: Boolean = true,
    val language: LanguageMode = LanguageMode.ENGLISH,
    val pace: Pace = Pace.STEADY,
    val tone: NudgeTone = NudgeTone.GENTLE,
    /** Light-hearted warnings after repeatedly missed planned days. */
    val creativeWarnings: Boolean = true,
    val interests: Set<String> = emptySet(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val textScale: Float = 1f,
    /** Epoch day the learner finished onboarding; misses before this are never counted. */
    val startDay: Long = 0,
)
