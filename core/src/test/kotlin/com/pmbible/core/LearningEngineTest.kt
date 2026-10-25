package com.pmbible.core

import com.pmbible.core.domain.Gap
import com.pmbible.core.domain.Mastery
import com.pmbible.core.domain.MasteryLevel
import com.pmbible.core.domain.Placement
import com.pmbible.core.domain.PlanItem
import com.pmbible.core.domain.SessionPlanner
import com.pmbible.core.domain.SpacedRepetition
import com.pmbible.core.model.ApplicationScore
import com.pmbible.core.model.LessonState
import com.pmbible.core.model.Pace
import com.pmbible.core.model.ProgressSnapshot
import com.pmbible.core.model.ReviewState
import com.pmbible.core.model.Segment
import com.pmbible.core.model.StartingPoint
import com.pmbible.core.model.UserPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningEngineTest {
    private val c = TestContent.curriculum
    private val today = 20_000L
    private val prefs = UserPrefs(onboarded = true)

    private fun completeLesson(id: String, p: ProgressSnapshot, day: Long = today - 1, correct: Boolean = true): ProgressSnapshot {
        val lesson = c.lessonById.getValue(id)
        val reviews = p.reviews.toMutableMap()
        lesson.questions.forEach { q ->
            reviews[q.id] = SpacedRepetition.schedule(reviews[q.id], q.id, id, correct, true, day)
        }
        return p.copy(
            lessons = p.lessons + (id to LessonState(id, quickDone = true, practiceDone = true, completedDay = day)),
            reviews = reviews,
        )
    }

    // --- Spaced repetition ---

    @Test
    fun correctAnswersClimbBoxesOnlyWhenDue() {
        var r = SpacedRepetition.schedule(null, "q", "l", correct = true, confident = true, today = today)
        assertEquals(1, r.box)
        assertEquals(today + 1, r.dueDay)
        // Answering again the same day doesn't promote (no cramming).
        val sameDay = SpacedRepetition.schedule(r, "q", "l", true, true, today)
        assertEquals(1, sameDay.box)
        assertEquals(r.dueDay, sameDay.dueDay)
        r = SpacedRepetition.schedule(r, "q", "l", true, true, today + 1)
        assertEquals(2, r.box)
        assertEquals(today + 1 + 3, r.dueDay)
    }

    @Test
    fun wrongAnswerDemotesAndReturnsTomorrow() {
        val r = ReviewState("q", "l", box = 4, dueDay = today, lastDay = today - 16, correctCount = 4, wrongCount = 0, lastCorrect = true)
        val next = SpacedRepetition.schedule(r, "q", "l", correct = false, confident = true, today = today)
        assertEquals(2, next.box)
        assertEquals(today + 1, next.dueDay)
        assertFalse(next.lastCorrect)
    }

    @Test
    fun guessedCorrectAnswerDoesNotPromote() {
        val r = ReviewState("q", "l", box = 2, dueDay = today, lastDay = today - 3, correctCount = 2, wrongCount = 0, lastCorrect = true)
        val next = SpacedRepetition.schedule(r, "q", "l", correct = true, confident = false, today = today)
        assertEquals(2, next.box)
        assertEquals(today + 1, next.dueDay)
        assertEquals(3, next.correctCount)
    }

    // --- Mastery is distinct from completion ---

    @Test
    fun completingALessonIsNotMastery() {
        val p = completeLesson("l_pm_role", ProgressSnapshot())
        assertTrue(p.lesson("l_pm_role").completed)
        assertEquals(MasteryLevel.LEARNING, Mastery.level(c.lessonById.getValue("l_pm_role"), p, today))
    }

    @Test
    fun spacedSuccessAndApplicationReachMastery() {
        val lesson = c.lessonById.getValue("l_pm_role")
        val reviews = lesson.questions.associate {
            it.id to ReviewState(it.id, lesson.id, box = 5, dueDay = today + 30, lastDay = today, correctCount = 5, wrongCount = 0, lastCorrect = true)
        }
        val p = ProgressSnapshot(
            lessons = mapOf(lesson.id to LessonState(lesson.id, true, true, true, true)),
            reviews = reviews,
            appScores = listOf(ApplicationScore(lesson.id, "ex", 1.0, today)),
        )
        assertEquals(MasteryLevel.MASTERED, Mastery.level(lesson, p, today))
    }

    @Test
    fun overdueReviewsDecayMastery() {
        val lesson = c.lessonById.getValue("l_pm_role")
        fun snapshot(due: Long) = ProgressSnapshot(reviews = lesson.questions.associate {
            it.id to ReviewState(it.id, lesson.id, box = 3, dueDay = due, lastDay = due - 7, correctCount = 3, wrongCount = 0, lastCorrect = true)
        })
        val fresh = Mastery.score(lesson, snapshot(today), today)
        val stale = Mastery.score(lesson, snapshot(today - 30), today)
        assertTrue("stale $stale should be below fresh $fresh", stale < fresh)
    }

    // --- Prerequisite gap detection ---

    @Test
    fun strugglingWithALessonFlagsWeakPrerequisite() {
        var p = completeLesson("l_pm_role", ProgressSnapshot(), correct = false)
        p = completeLesson("l_product_thinking", p, correct = false)
        val gaps = Mastery.gaps(c, p, today)
        assertTrue(gaps.contains(Gap("l_pm_role", "l_product_thinking")))
    }

    @Test
    fun noGapWhenPrerequisiteIsStrong() {
        var p = completeLesson("l_pm_role", ProgressSnapshot(), correct = true)
        p = completeLesson("l_product_thinking", p, correct = false)
        assertTrue(Mastery.gaps(c, p, today).none { it.prerequisiteId == "l_pm_role" })
    }

    // --- Adaptive planner ---

    @Test
    fun fiveMinutesWithNothingDueGivesOneQuickLearn() {
        val plan = SessionPlanner.plan(c, ProgressSnapshot(), prefs, 5, today)
        assertEquals(listOf(PlanItem.LessonPart("l_pm_role", Segment.QUICK, 5.0)), plan.items)
    }

    @Test
    fun fifteenMinutesGivesALessonWithApplicationExercise() {
        val plan = SessionPlanner.plan(c, ProgressSnapshot(), prefs, 15, today)
        val segments = plan.items.filterIsInstance<PlanItem.LessonPart>().filter { it.lessonId == "l_pm_role" }.map { it.segment }
        assertEquals(listOf(Segment.QUICK, Segment.PRACTICE, Segment.EXERCISE), segments)
        assertTrue(plan.minutes <= 15 + 2)
    }

    @Test
    fun longSessionsAddDepthNotJustMoreBasics() {
        var p = ProgressSnapshot()
        listOf("l_pm_role", "l_product_thinking", "l_pm_vs_project").forEach { p = completeLesson(it, p, day = today - 10) }
        val plan = SessionPlanner.plan(c, p, prefs.copy(pace = Pace.RELAXED), 40, today)
        assertTrue("Expected a deep dive", plan.items.any { it is PlanItem.LessonPart && it.segment == Segment.DEEP })
        assertTrue("Expected interview or case practice", plan.items.any { it is PlanItem.Interview || it is PlanItem.Case })
        assertEquals("Relaxed pace allows one new lesson", 1,
            plan.items.filterIsInstance<PlanItem.LessonPart>().filter { it.segment == Segment.QUICK }.size)
    }

    @Test
    fun shortSessionWithBacklogFocusesOnReviews() {
        var p = ProgressSnapshot()
        listOf("l_pm_role", "l_product_thinking").forEach { p = completeLesson(it, p, day = today - 5) }
        val plan = SessionPlanner.plan(c, p, prefs, 5, today)
        assertTrue(plan.items.all { it is PlanItem.Review })
        assertTrue(plan.items.sumOf { it.minutes } <= 5.0 + 1e-9)
    }

    @Test
    fun reviewsAreCappedSoNewLearningStillHappens() {
        var p = ProgressSnapshot()
        listOf("l_pm_role", "l_product_thinking", "l_pm_vs_project", "l_lifecycle").forEach { p = completeLesson(it, p, day = today - 5) }
        val plan = SessionPlanner.plan(c, p, prefs, 15, today)
        val review = plan.items.filterIsInstance<PlanItem.Review>().single()
        assertTrue(review.minutes <= 15 * 0.35 + 1e-9)
        assertTrue("Notes should say the rest stay queued", plan.notes.any { "nothing is lost" in it })
    }

    @Test
    fun unfinishedLessonResumesAtNextPart() {
        val p = ProgressSnapshot(lessons = mapOf("l_pm_role" to LessonState("l_pm_role", quickDone = true)))
        val plan = SessionPlanner.plan(c, p, prefs, 10, today)
        val first = plan.items.filterIsInstance<PlanItem.LessonPart>().first()
        assertEquals("l_pm_role", first.lessonId)
        assertEquals(Segment.PRACTICE, first.segment)
    }

    @Test
    fun finishedUnitTriggersCumulativeChallenge() {
        var p = ProgressSnapshot()
        c.lessonsByUnit.getValue("u01").forEach { p = completeLesson(it.id, p, day = today - 10) }
        assertEquals("u01", SessionPlanner.pendingCheckpoint(c, p))
        val plan = SessionPlanner.plan(c, p, prefs, 20, today)
        assertTrue(plan.items.any { it is PlanItem.CheckpointChallenge && it.unitId == "u01" })
    }

    @Test
    fun nextLessonRespectsPrerequisites() {
        val next = SessionPlanner.nextLesson(c, ProgressSnapshot())
        assertNotNull(next)
        assertTrue(next!!.prerequisites.isEmpty())
    }

    @Test
    fun placementSkipsAheadButQueuesVerificationReviews() {
        val placed = Placement.placedLessonIds(c, StartingPoint.BASICS)
        assertEquals(c.lessonsByUnit.getValue("u01").size + c.lessonsByUnit.getValue("u02").size, placed.size)
        val reviews = Placement.initialReviews(c, StartingPoint.BASICS, today)
        assertTrue(reviews.all { it.box == 0 && it.dueDay in today..today + 6 })
        val p = ProgressSnapshot(
            lessons = placed.associateWith { LessonState(it, placed = true) },
            reviews = reviews.associateBy { it.questionId },
        )
        assertEquals("u03", c.lessonById.getValue(SessionPlanner.nextLesson(c, p)!!.id).unitId)
        assertEquals("Placed units don't trigger challenges", null, SessionPlanner.pendingCheckpoint(c, p))
    }

    @Test
    fun planningEveryLessonInSequenceNeverDeadlocks() {
        var p = ProgressSnapshot()
        var day = today
        repeat(c.lessons.size) {
            val next = SessionPlanner.nextLesson(c, p) ?: return@repeat
            p = completeLesson(next.id, p, day = day++)
        }
        assertEquals(c.lessons.size, p.lessons.values.count { it.completed })
        val plan = SessionPlanner.plan(c, p, prefs, 15, day)
        assertFalse(plan.isEmpty)
    }
}
