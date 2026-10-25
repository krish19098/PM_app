package com.pmbible.app.ui.session

import com.pmbible.app.data.AnswerContext
import com.pmbible.core.domain.Mastery
import com.pmbible.core.domain.PlanItem
import com.pmbible.core.domain.SessionPlan
import com.pmbible.core.model.Bilingual
import com.pmbible.core.model.Curriculum
import com.pmbible.core.model.InterviewQuestion
import com.pmbible.core.model.Lesson
import com.pmbible.core.model.ProgressSnapshot
import com.pmbible.core.model.Question
import com.pmbible.core.model.Segment

/** One screen in the session player. */
sealed interface Step {
    data class Intro(val label: String, val title: String, val body: Bilingual, val bullets: List<String> = emptyList()) : Step
    data class Quick(val lesson: Lesson) : Step
    data class Deep(val lesson: Lesson) : Step
    /** [completesPracticeOf] is set on the last practice question of a lesson. */
    data class Ask(val question: Question, val context: AnswerContext, val sourceId: String, val completesPracticeOf: String? = null) : Step
    data class Exercise(val lesson: Lesson) : Step
    data class CheckpointEnd(val unitId: String) : Step
    data class CaseEnd(val caseId: String) : Step
    data class Interview(val question: InterviewQuestion) : Step
}

/** Session entry points. Encoded in the navigation route. */
enum class SessionMode { TODAY, LESSON, REVIEW, CHECKPOINT, CASE }

object SessionBuilder {

    fun fromPlan(c: Curriculum, plan: SessionPlan): List<Step> = plan.items.flatMap { item ->
        when (item) {
            is PlanItem.Review -> reviewSteps(c, item.questionIds)
            is PlanItem.GapRefresh -> {
                val lesson = c.lessonById.getValue(item.lessonId)
                val because = c.lessonById[item.becauseOf]?.title.orEmpty()
                listOf(Step.Intro("Refresher", lesson.title, lesson.quick.explanation, listOf("Why now: it's a building block for “$because”."))) +
                    item.questionIds.mapNotNull { c.questionById[it] }.map { Step.Ask(it, AnswerContext.REVIEW, it.lessonId) }
            }
            is PlanItem.LessonPart -> lessonPart(c.lessonById.getValue(item.lessonId), item.segment)
            is PlanItem.CheckpointChallenge -> checkpointSteps(c, item.unitId)
            is PlanItem.Case -> caseSteps(c, item.caseId)
            is PlanItem.Interview -> listOfNotNull(c.interviewById[item.questionId]?.let { Step.Interview(it) })
        }
    }

    fun forLesson(lesson: Lesson, p: ProgressSnapshot): List<Step> {
        val state = p.lesson(lesson.id)
        val remaining = Segment.entries.filterNot { state.isDone(it) }
        // A finished lesson replays practice and depth; spaced repetition ignores early repeats for promotion.
        val segments = remaining.ifEmpty { listOf(Segment.PRACTICE, Segment.EXERCISE) }
        return segments.flatMap { lessonPart(lesson, it) }
    }

    fun forReview(c: Curriculum, p: ProgressSnapshot, today: Long): List<Step> {
        val due = p.dueReviews(today).sortedWith(compareBy({ it.dueDay }, { it.box })).map { it.questionId }.take(20)
        if (due.isNotEmpty()) return reviewSteps(c, due)
        val weak = Mastery.weakest(c, p, today, limit = 3).flatMap { it.first.questions }.take(8).map { it.id }
        return if (weak.isEmpty()) emptyList()
        else listOf(Step.Intro("Strengthen", "Nothing is due", Bilingual("No reviews are due right now, so here are questions from your weakest concepts."))) +
            weak.mapNotNull { c.questionById[it] }.map { Step.Ask(it, AnswerContext.REVIEW, it.lessonId) }
    }

    fun reviewSteps(c: Curriculum, ids: List<String>): List<Step> =
        listOf(Step.Intro("Review", "Quick recall", Bilingual("Retrieving what you learned before it fades is what makes it stick.", "Marandhu poradhukku munnaadi nyaabagapaduthuradhu dhaan manasula nikka vaikkum."))) +
            ids.mapNotNull { c.questionById[it] }.map { Step.Ask(it, AnswerContext.REVIEW, it.lessonId) }

    fun lessonPart(lesson: Lesson, segment: Segment): List<Step> = when (segment) {
        Segment.QUICK -> listOf(Step.Quick(lesson))
        Segment.PRACTICE -> lesson.questions.mapIndexed { i, q ->
            Step.Ask(q, AnswerContext.PRACTICE, lesson.id, completesPracticeOf = if (i == lesson.questions.lastIndex) lesson.id else null)
        }
        Segment.EXERCISE -> listOf(Step.Exercise(lesson))
        Segment.DEEP -> listOf(Step.Deep(lesson))
    }

    fun checkpointSteps(c: Curriculum, unitId: String): List<Step> {
        val cp = c.checkpoints[unitId] ?: return emptyList()
        return listOf(Step.Intro("Unit challenge", cp.title, Bilingual(cp.scenario), listOf("Combines the concepts from “${c.unitById[unitId]?.title}”."))) +
            cp.steps.map { Step.Ask(it, AnswerContext.CHECKPOINT, "cp:$unitId") } +
            Step.CheckpointEnd(unitId)
    }

    fun caseSteps(c: Curriculum, caseId: String): List<Step> {
        val cs = c.caseById[caseId] ?: return emptyList()
        return listOf(Step.Intro(cs.label, cs.title, Bilingual(cs.context), cs.constraints.map { "Constraint: $it" })) +
            cs.decisions.map { Step.Ask(it, AnswerContext.CASE, "case:$caseId") } +
            Step.CaseEnd(caseId)
    }
}
