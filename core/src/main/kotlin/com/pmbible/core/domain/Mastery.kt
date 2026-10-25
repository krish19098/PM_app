package com.pmbible.core.domain

import com.pmbible.core.model.Curriculum
import com.pmbible.core.model.Lesson
import com.pmbible.core.model.ProgressSnapshot
import com.pmbible.core.model.ReviewState
import kotlin.math.pow

/**
 * Leitner-style spaced repetition. A correct, confident answer on a due question moves it
 * up a box (longer interval); a wrong answer moves it down two boxes and brings it back
 * tomorrow. Answering early (before due) never promotes, so cramming cannot fake mastery.
 */
object SpacedRepetition {
    const val MAX_BOX = 5
    /** Interval in days for each box index. */
    val intervals = intArrayOf(1, 1, 3, 7, 16, 35)

    fun schedule(
        prev: ReviewState?,
        questionId: String,
        lessonId: String,
        correct: Boolean,
        confident: Boolean,
        today: Long,
    ): ReviewState {
        val success = correct && confident
        val box = when {
            prev == null -> if (success) 1 else 0
            !correct -> (prev.box - 2).coerceAtLeast(0)
            // A guess, or an early/same-day repeat, keeps the box but still counts as practice.
            !success || prev.dueDay > today || prev.lastDay == today -> prev.box
            else -> (prev.box + 1).coerceAtMost(MAX_BOX)
        }
        val due = if (!success) today + 1 else if (prev != null && prev.dueDay > today && box == prev.box) prev.dueDay else today + intervals[box]
        return ReviewState(
            questionId = questionId,
            lessonId = lessonId,
            box = box,
            dueDay = due,
            lastDay = today,
            correctCount = (prev?.correctCount ?: 0) + if (correct) 1 else 0,
            wrongCount = (prev?.wrongCount ?: 0) + if (correct) 0 else 1,
            lastCorrect = correct,
        )
    }
}

enum class MasteryLevel(val label: String, val min: Double) {
    NOT_STARTED("Not started", 0.0),
    LEARNING("Learning", 0.01),
    FAMILIAR("Familiar", 0.35),
    PROFICIENT("Proficient", 0.6),
    MASTERED("Mastered", 0.85);

    companion object {
        fun of(score: Double): MasteryLevel = entries.last { score >= it.min }
    }
}

/** Gap: a prerequisite that looks weak while the learner struggles with a dependent lesson. */
data class Gap(val prerequisiteId: String, val becauseOfLessonId: String)

/**
 * Mastery is evidence-based and separate from completion: recall strength decays when
 * reviews go overdue, and full mastery also requires applying the concept.
 */
object Mastery {

    fun questionStrength(r: ReviewState?, today: Long): Double {
        if (r == null) return 0.0
        val base = r.box.toDouble() / SpacedRepetition.MAX_BOX
        val overdue = today - r.dueDay
        if (overdue <= 0) return base
        val interval = SpacedRepetition.intervals[r.box].coerceAtLeast(1)
        return base * 0.5.pow(overdue.toDouble() / (2.0 * interval))
    }

    fun recall(lesson: Lesson, p: ProgressSnapshot, today: Long): Double =
        if (lesson.questions.isEmpty()) 0.0
        else lesson.questions.sumOf { questionStrength(p.reviews[it.id], today) } / lesson.questions.size

    /** Recent application evidence (latest three, newest weighted most), or null if none. */
    fun application(lessonId: String, p: ProgressSnapshot): Double? {
        val recent = p.appScores.filter { it.lessonId == lessonId }.sortedByDescending { it.day }.take(3)
        if (recent.isEmpty()) return null
        val weights = listOf(3.0, 2.0, 1.0)
        return recent.withIndex().sumOf { it.value.score * weights[it.index] } / weights.take(recent.size).sum()
    }

    fun score(lesson: Lesson, p: ProgressSnapshot, today: Long): Double {
        val recall = recall(lesson, p, today)
        val app = application(lesson.id, p) ?: return recall * 0.85
        return 0.7 * recall + 0.3 * app
    }

    fun level(lesson: Lesson, p: ProgressSnapshot, today: Long) = MasteryLevel.of(score(lesson, p, today))

    fun unitScore(c: Curriculum, unitId: String, p: ProgressSnapshot, today: Long): Double {
        val lessons = c.lessonsByUnit[unitId].orEmpty()
        return if (lessons.isEmpty()) 0.0 else lessons.sumOf { score(it, p, today) } / lessons.size
    }

    /** A lesson the learner is visibly struggling with: repeated or latest misses. */
    fun isStruggling(lesson: Lesson, p: ProgressSnapshot): Boolean {
        val states = lesson.questions.mapNotNull { p.reviews[it.id] }
        val wrong = states.sumOf { it.wrongCount }
        val right = states.sumOf { it.correctCount }
        return states.count { !it.lastCorrect } >= 2 || (wrong >= 2 && wrong >= right)
    }

    private fun isWeak(lesson: Lesson, p: ProgressSnapshot, today: Long): Boolean {
        val state = p.lesson(lesson.id)
        if (!state.completed) return true
        val reviews = lesson.questions.mapNotNull { p.reviews[it.id] }
        return reviews.any { !it.lastCorrect } || (state.placed && reviews.isEmpty()) ||
            (reviews.isNotEmpty() && recall(lesson, p, today) < 0.15)
    }

    fun gaps(c: Curriculum, p: ProgressSnapshot, today: Long): List<Gap> =
        c.orderedLessons.filter { isStruggling(it, p) }.flatMap { lesson ->
            lesson.prerequisites.mapNotNull { c.lessonById[it] }
                .filter { isWeak(it, p, today) }
                .map { Gap(it.id, lesson.id) }
        }.distinctBy { it.prerequisiteId }

    /** Lessons with the lowest mastery among those already completed. */
    fun weakest(c: Curriculum, p: ProgressSnapshot, today: Long, limit: Int = 3): List<Pair<Lesson, Double>> =
        c.orderedLessons.filter { p.lesson(it.id).completed }
            .map { it to score(it, p, today) }
            .sortedBy { it.second }
            .take(limit)
}
