package com.pmbible.core.domain

import com.pmbible.core.model.Curriculum
import com.pmbible.core.model.ReviewState
import com.pmbible.core.model.StartingPoint

/**
 * Lets experienced learners skip ahead without pretending they've mastered anything:
 * placed lessons count as complete for the path, but their questions are queued as
 * reviews (spread over a week) so the learner proves what they already know.
 */
object Placement {

    fun placedLessonIds(c: Curriculum, start: StartingPoint): List<String> =
        c.units.sortedBy { it.order }.take(start.placedUnits).flatMap { u -> c.lessonsByUnit[u.id].orEmpty().map { it.id } }

    fun initialReviews(c: Curriculum, start: StartingPoint, today: Long): List<ReviewState> =
        placedLessonIds(c, start).flatMap { id -> c.lessonById.getValue(id).questions }
            .mapIndexed { i, q ->
                ReviewState(
                    questionId = q.id, lessonId = q.lessonId, box = 0, dueDay = today + (i % 7),
                    lastDay = today, correctCount = 0, wrongCount = 0, lastCorrect = true,
                )
            }
}
