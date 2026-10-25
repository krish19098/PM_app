package com.pmbible.core.domain

import com.pmbible.core.model.Curriculum
import com.pmbible.core.model.Lesson
import com.pmbible.core.model.ProgressSnapshot
import com.pmbible.core.model.Segment
import com.pmbible.core.model.UserPrefs

sealed interface PlanItem {
    val minutes: Double

    data class Review(val questionIds: List<String>) : PlanItem {
        override val minutes get() = questionIds.size * REVIEW_MINUTES
    }

    data class GapRefresh(val lessonId: String, val becauseOf: String, val questionIds: List<String>) : PlanItem {
        override val minutes get() = 1.5 + questionIds.size * REVIEW_MINUTES
    }

    data class LessonPart(val lessonId: String, val segment: Segment, override val minutes: Double) : PlanItem

    data class CheckpointChallenge(val unitId: String, override val minutes: Double) : PlanItem

    data class Case(val caseId: String, override val minutes: Double) : PlanItem

    data class Interview(val questionId: String) : PlanItem {
        override val minutes get() = INTERVIEW_MINUTES
    }

    companion object {
        const val REVIEW_MINUTES = 0.75
        const val INTERVIEW_MINUTES = 8.0
        const val EXERCISE_MINUTES = 4.0
    }
}

data class SessionPlan(val items: List<PlanItem>, val notes: List<String>) {
    val minutes: Int get() = kotlin.math.ceil(items.sumOf { it.minutes }).toInt()
    val isEmpty: Boolean get() = items.isEmpty()
}

/**
 * Builds today's session from the time the learner has. Order of priority:
 * due reviews (capped so new learning still happens) → prerequisite gaps → pending unit
 * challenge → the next lesson's parts in their natural order (split at part boundaries,
 * never mid-part) → depth (deep dive, interview, case) when time remains.
 */
object SessionPlanner {

    private const val TOLERANCE = 1.5

    fun segmentMinutes(lesson: Lesson, segment: Segment): Double = when (segment) {
        Segment.QUICK -> lesson.quickMinutes.toDouble()
        Segment.PRACTICE -> lesson.questions.size * PlanItem.REVIEW_MINUTES
        Segment.EXERCISE -> PlanItem.EXERCISE_MINUTES
        Segment.DEEP -> lesson.deepMinutes.toDouble()
    }

    /** First lesson in path order that is unfinished and whose prerequisites are complete. */
    fun nextLesson(c: Curriculum, p: ProgressSnapshot, exclude: Set<String> = emptySet()): Lesson? {
        val ordered = c.orderedLessons.filter { it.id !in exclude }
        ordered.firstOrNull { p.lesson(it.id).inProgress }?.let { return it }
        return ordered.firstOrNull { l ->
            !p.lesson(l.id).completed && l.prerequisites.all { p.lesson(it).completed }
        }
    }

    fun pendingCheckpoint(c: Curriculum, p: ProgressSnapshot): String? =
        c.units.firstOrNull { u ->
            u.id !in p.checkpointsDone && c.lessonsByUnit[u.id].orEmpty().all { p.lesson(it.id).completed } &&
                c.lessonsByUnit[u.id].orEmpty().any { !p.lesson(it.id).placed }
        }?.id

    fun plan(
        c: Curriculum,
        p: ProgressSnapshot,
        prefs: UserPrefs,
        minutes: Int,
        today: Long,
        doneInterviewIds: Set<String> = emptySet(),
    ): SessionPlan {
        val items = mutableListOf<PlanItem>()
        val notes = mutableListOf<String>()
        var remaining = minutes.toDouble()
        fun fits(cost: Double) = cost <= remaining + TOLERANCE
        fun add(item: PlanItem) { items += item; remaining -= item.minutes }

        val due = p.dueReviews(today)
            .filter { c.questionById.containsKey(it.questionId) }
            .sortedWith(compareBy({ it.dueDay }, { it.box }))
        val gap = Mastery.gaps(c, p, today).firstOrNull()
        val next = nextLesson(c, p)
        val short = minutes <= 6

        // 1. Reviews: in short sessions they can fill the slot; otherwise capped at ~35%.
        if (due.isNotEmpty()) {
            val cap = if (short && (due.size >= 3 || next == null)) remaining else maxOf(2 * PlanItem.REVIEW_MINUTES, remaining * 0.35)
            val n = (cap / PlanItem.REVIEW_MINUTES).toInt().coerceIn(1, minOf(due.size, 12))
            add(PlanItem.Review(due.take(n).map { it.questionId }))
            notes += if (due.size > n) "${due.size} reviews are due — doing the $n most overdue now, the rest stay queued (nothing is lost)."
            else "$n review${if (n > 1) "s" else ""} due to keep earlier concepts fresh."
        }

        // 2. Prerequisite gap.
        if (gap != null && remaining >= 2.5) {
            val qs = c.lessonById[gap.prerequisiteId]?.questions?.take(3)?.map { it.id }.orEmpty()
            if (qs.isNotEmpty()) {
                add(PlanItem.GapRefresh(gap.prerequisiteId, gap.becauseOfLessonId, qs))
                notes += "Quick refresher on “${c.lessonById[gap.prerequisiteId]?.title}” — it's a building block for “${c.lessonById[gap.becauseOfLessonId]?.title}”, where you've been missing questions."
            }
        }

        // 3. Unit challenge once a unit's lessons are complete.
        pendingCheckpoint(c, p)?.let { unitId ->
            val cp = c.checkpoints.getValue(unitId)
            if (fits(cp.minutes.toDouble())) {
                add(PlanItem.CheckpointChallenge(unitId, cp.minutes.toDouble()))
                notes += "You finished “${c.unitById[unitId]?.title}” — a cumulative challenge ties those concepts together."
            }
        }

        // 4. New learning, part by part.
        val includeDeep = minutes >= 25
        var lesson = next
        var newLessons = 0
        val planned = mutableSetOf<String>()
        while (lesson != null && newLessons < prefs.pace.maxNewLessons) {
            val current: Lesson = lesson
            val state = p.lesson(current.id)
            val segments = listOfNotNull(
                Segment.QUICK, Segment.PRACTICE, Segment.EXERCISE, if (includeDeep) Segment.DEEP else null,
            ).filterNot { state.isDone(it) }
            var addedCount = 0
            for (segment in segments) {
                val cost = segmentMinutes(current, segment)
                if (!fits(cost)) break
                add(PlanItem.LessonPart(current.id, segment, cost))
                addedCount++
            }
            if (addedCount == 0) break
            planned += current.id
            if (state.inProgress) notes += "Resuming “${current.title}” where you left off."
            newLessons++
            if (addedCount < segments.size) {
                notes += "“${current.title}” continues next session — split at a natural break."
                break
            }
            // Only take a further new lesson when the remaining time can hold its core.
            lesson = nextLesson(c, p, exclude = planned)?.takeIf { it.prerequisites.all { pr -> pr in planned || p.lesson(pr).completed } }
                ?.takeIf { remaining >= it.quickMinutes + it.questions.size * PlanItem.REVIEW_MINUTES }
        }

        // 5. Depth with spare time: interview practice and cases first (variety), then deep dives of finished lessons.
        val completedIds = c.orderedLessons.filter { p.lesson(it.id).completed }.map { it.id }.toSet() + planned
        if (remaining >= PlanItem.INTERVIEW_MINUTES - TOLERANCE) {
            c.interview.filter { q -> q.id !in doneInterviewIds && q.lessonIds.all { it in completedIds } }
                .maxByOrNull { q -> q.lessonIds.maxOf { id -> c.orderedLessons.indexOfFirst { it.id == id } } }
                ?.let {
                    add(PlanItem.Interview(it.id))
                    notes += "Interview practice linked to what you've learned."
                }
        }
        if (remaining >= 6) {
            c.cases.filter { cs -> cs.id !in p.casesDone && cs.lessonIds.all { it in completedIds } }
                .firstOrNull { fits(it.minutes.toDouble()) }
                ?.let {
                    add(PlanItem.Case(it.id, it.minutes.toDouble()))
                    notes += "A case study to apply several concepts together."
                }
        }

        if (remaining >= 6) {
            c.orderedLessons.lastOrNull { p.lesson(it.id).completed && !p.lesson(it.id).deepDone && it.id !in planned }
                ?.takeIf { fits(it.deepMinutes.toDouble()) && includeDeep }
                ?.let {
                    add(PlanItem.LessonPart(it.id, Segment.DEEP, it.deepMinutes.toDouble()))
                    notes += "Extra time → going deeper on “${it.title}” instead of repeating basics."
                }
        }
        // 6. Nothing new left to fit: strengthen the weakest concepts.
        if (items.none { it is PlanItem.Review } && remaining >= 2) {
            val reviewIds = due.map { it.questionId }.toSet()
            val weakQs = Mastery.weakest(c, p, today, limit = 3)
                .flatMap { it.first.questions }
                .filter { it.id !in reviewIds }
                .sortedBy { p.reviews[it.id]?.box ?: 0 }
            val n = (remaining / PlanItem.REVIEW_MINUTES).toInt().coerceAtMost(minOf(6, weakQs.size))
            if (n > 0) {
                add(PlanItem.Review(weakQs.take(n).map { it.id }))
                notes += "Strengthening your weakest concepts."
            }
        }

        if (items.isEmpty()) {
            notes += if (next == null) "You've completed every lesson in this release. Use the Library, cases and interview studio to keep sharpening."
            else "Not enough time for a meaningful step — try 5 minutes or more."
        }
        return SessionPlan(items, notes)
    }
}
