package com.pmbible.core.domain

import com.pmbible.core.model.Curriculum
import com.pmbible.core.model.InterviewCategory
import com.pmbible.core.model.InterviewQuestion
import com.pmbible.core.model.ProgressSnapshot
import com.pmbible.core.model.Signal
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Local, transparent self-review for written interview answers. It only checks whether
 * structural ingredients are present (keyword heuristics) — it is not AI grading.
 */
object AnswerReview {

    val genericSignals = listOf(
        Signal("Clarifies the goal or asks questions", listOf("clarify", "goal", "objective", "assume", "assumption", "question")),
        Signal("Names a specific user or segment", listOf("user", "customer", "segment", "persona", "people", "who")),
        Signal("Defines how success is measured", listOf("metric", "measure", "kpi", "rate", "%", "retention", "conversion")),
        Signal("Prioritises explicitly", listOf("priorit", "first", "focus", "rice", "impact", "effort")),
        Signal("Discusses trade-offs or risks", listOf("trade", "risk", "downside", "cost", "however", "but")),
    )

    data class Check(val signal: Signal, val found: Boolean)

    fun review(answer: String, question: InterviewQuestion): List<Check> {
        val text = answer.lowercase()
        return (question.signals + genericSignals).distinctBy { it.label }
            .map { s -> Check(s, s.keywords.any { text.contains(it.lowercase()) }) }
    }

    fun lengthFeedback(answer: String): String {
        val words = answer.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        return when {
            words == 0 -> "Write your answer first — even bullet points help."
            words < 60 -> "$words words — quite short. Interviewers expect you to walk through your reasoning."
            words > 600 -> "$words words — long. Practise summarising the structure before going deep."
            else -> "$words words — a workable length for a structured spoken answer."
        }
    }
}

data class WeeklyRecap(
    val weekStart: LocalDate,
    val daysActive: Int,
    val goal: Int,
    val minutes: Int,
    val lessonsCompleted: Int,
    val answers: Int,
    val accuracy: Double?,
    val weakest: List<Pair<String, Double>>,
    val competency: Map<InterviewCategory, Double>,
)

object Insights {

    fun weeklyRecap(c: Curriculum, p: ProgressSnapshot, goal: Int, today: Long): WeeklyRecap {
        val start = LocalDate.ofEpochDay(today).with(DayOfWeek.MONDAY)
        val range = start.toEpochDay()..today
        val attempts = p.attempts.filter { it.day in range }
        return WeeklyRecap(
            weekStart = start,
            daysActive = p.activeDays.count { it in range },
            goal = goal,
            minutes = p.minutesByDay.filterKeys { it in range }.values.sum(),
            lessonsCompleted = p.lessons.values.count { !it.placed && it.completedDay != null && it.completedDay in range },
            answers = attempts.size,
            accuracy = if (attempts.isEmpty()) null else attempts.count { it.correct }.toDouble() / attempts.size,
            weakest = Mastery.weakest(c, p, today).map { it.first.title to it.second },
            competency = competency(p),
        )
    }

    /** Average self-rated rubric score per interview category (latest attempt per question). */
    fun competency(p: ProgressSnapshot): Map<InterviewCategory, Double> =
        p.interviewScores.groupBy { it.questionId }.mapValues { e -> e.value.maxBy { it.day } }.values
            .groupBy { it.category }
            .mapValues { e -> e.value.sumOf { it.score } / e.value.size }
}
