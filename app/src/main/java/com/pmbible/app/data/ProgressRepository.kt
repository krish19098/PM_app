package com.pmbible.app.data

import androidx.room.withTransaction
import com.pmbible.app.data.db.ActivityDayEntity
import com.pmbible.app.data.db.AppDatabase
import com.pmbible.app.data.db.AppScoreEntity
import com.pmbible.app.data.db.AttemptEntity
import com.pmbible.app.data.db.BookmarkEntity
import com.pmbible.app.data.db.CompletionEntity
import com.pmbible.app.data.db.ExerciseAnswerEntity
import com.pmbible.app.data.db.InterviewAttemptEntity
import com.pmbible.app.data.db.LessonStateEntity
import com.pmbible.app.data.db.NoteEntity
import com.pmbible.app.data.db.ReviewEntity
import com.pmbible.core.domain.Placement
import com.pmbible.core.domain.SpacedRepetition
import com.pmbible.core.model.ApplicationScore
import com.pmbible.core.model.Attempt
import com.pmbible.core.model.Curriculum
import com.pmbible.core.model.InterviewCategory
import com.pmbible.core.model.InterviewScore
import com.pmbible.core.model.LessonState
import com.pmbible.core.model.ProgressSnapshot
import com.pmbible.core.model.Question
import com.pmbible.core.model.ReviewState
import com.pmbible.core.model.Segment
import com.pmbible.core.model.StartingPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/** Where an answer was given; decides whether it feeds spaced repetition or application evidence. */
enum class AnswerContext { PRACTICE, REVIEW, CHECKPOINT, CASE }

/**
 * Single writer for learner progress. All mutations are small, transactional upserts so
 * progress survives process death at any point in a session.
 */
class ProgressRepository(private val db: AppDatabase) {
    private val dao = db.progressDao()

    val snapshot: Flow<ProgressSnapshot> = combine(
        combine(dao.lessonStates(), dao.reviews(), dao.appScores()) { a, b, c -> Triple(a, b, c) },
        combine(dao.attempts(), dao.interviewAttempts()) { a, b -> Pair(a, b) },
        dao.completions(),
        dao.activityDays(),
    ) { (lessons, reviews, scores), (attempts, interviews), completions, days ->
        ProgressSnapshot(
            lessons = lessons.associate { it.lessonId to it.toModel() },
            reviews = reviews.associate { it.questionId to it.toModel() },
            appScores = scores.map { ApplicationScore(it.lessonId, it.sourceId, it.score, it.day) },
            attempts = attempts.map { Attempt(it.questionId, it.lessonId, it.correct, it.day) },
            interviewScores = interviews.mapNotNull { a ->
                InterviewCategory.entries.firstOrNull { it.name == a.category }?.let { InterviewScore(a.questionId, it, a.score, a.day) }
            },
            checkpointsDone = completions.filter { it.key.startsWith(CP) }.map { it.key.removePrefix(CP) }.toSet(),
            casesDone = completions.filter { it.key.startsWith(CASE) }.map { it.key.removePrefix(CASE) }.toSet(),
            minutesByDay = days.associate { it.day to it.minutes },
        )
    }

    val bookmarks: Flow<List<BookmarkEntity>> = dao.bookmarks()
    val notes: Flow<List<NoteEntity>> = dao.notes()
    val interviewAttempts: Flow<List<InterviewAttemptEntity>> = dao.interviewAttempts()
    val exerciseAnswers: Flow<List<ExerciseAnswerEntity>> = dao.exerciseAnswers()
    val attemptLog: Flow<List<AttemptEntity>> = dao.attempts()

    suspend fun openLesson(lessonId: String, today: Long) {
        val s = dao.lessonState(lessonId) ?: LessonStateEntity(lessonId)
        dao.upsertLessonState(s.copy(lastOpenedDay = today))
    }

    suspend fun markSegment(lessonId: String, segment: Segment, today: Long) = db.withTransaction {
        val s = dao.lessonState(lessonId) ?: LessonStateEntity(lessonId)
        val updated = when (segment) {
            Segment.QUICK -> s.copy(quickDone = true)
            Segment.PRACTICE -> s.copy(practiceDone = true)
            Segment.EXERCISE -> s.copy(exerciseDone = true)
            Segment.DEEP -> s.copy(deepDone = true)
        }.copy(lastOpenedDay = today)
        val nowComplete = updated.quickDone && updated.practiceDone
        dao.upsertLessonState(if (nowComplete && s.completedDay == null) updated.copy(completedDay = today) else updated)
    }

    /** Records an answer; practice and review answers drive spaced repetition, others count as application. */
    suspend fun recordAnswer(
        question: Question,
        correct: Boolean,
        confident: Boolean,
        context: AnswerContext,
        sourceId: String,
        today: Long,
    ) = db.withTransaction {
        dao.insertAttempt(
            AttemptEntity(
                questionId = question.id, lessonId = question.lessonId, correct = correct,
                day = today, timestamp = System.currentTimeMillis(), context = context.name,
            ),
        )
        when (context) {
            AnswerContext.PRACTICE, AnswerContext.REVIEW -> {
                val prev = dao.review(question.id)?.toModel()
                dao.upsertReview(SpacedRepetition.schedule(prev, question.id, question.lessonId, correct, confident, today).toEntity())
            }
            AnswerContext.CHECKPOINT, AnswerContext.CASE ->
                dao.insertAppScore(AppScoreEntity(lessonId = question.lessonId, sourceId = sourceId, score = if (correct) 1.0 else 0.0, day = today))
        }
    }

    suspend fun recordExercise(lessonId: String, answer: String, score: Double, today: Long) = db.withTransaction {
        dao.insertExerciseAnswer(ExerciseAnswerEntity(lessonId = lessonId, answer = answer, score = score, timestamp = System.currentTimeMillis()))
        dao.insertAppScore(AppScoreEntity(lessonId = lessonId, sourceId = "exercise:$lessonId", score = score, day = today))
        markSegment(lessonId, Segment.EXERCISE, today)
    }

    suspend fun completeCheckpoint(unitId: String, score: Double, today: Long) =
        dao.upsertCompletion(CompletionEntity(CP + unitId, today, score))

    suspend fun completeCase(caseId: String, score: Double, today: Long) =
        dao.upsertCompletion(CompletionEntity(CASE + caseId, today, score))

    /** Interview self-ratings also count as (lighter) application evidence for the linked lessons. */
    suspend fun recordInterview(
        questionId: String,
        category: InterviewCategory,
        answer: String,
        score: Double,
        lessonIds: List<String>,
        today: Long,
    ) = db.withTransaction {
        dao.insertInterviewAttempt(
            InterviewAttemptEntity(
                questionId = questionId, category = category.name, answer = answer,
                score = score, day = today, timestamp = System.currentTimeMillis(),
            ),
        )
        lessonIds.forEach { dao.insertAppScore(AppScoreEntity(lessonId = it, sourceId = "interview:$questionId", score = score, day = today)) }
    }

    suspend fun addMinutes(today: Long, minutes: Int) {
        if (minutes <= 0) return
        db.withTransaction {
            val current = dao.activityDay(today)?.minutes ?: 0
            dao.upsertActivityDay(ActivityDayEntity(today, current + minutes))
        }
    }

    suspend fun applyPlacement(c: Curriculum, start: StartingPoint, today: Long) = db.withTransaction {
        val ids = Placement.placedLessonIds(c, start)
        if (ids.isEmpty()) return@withTransaction
        dao.upsertLessonStates(ids.map { LessonStateEntity(it, placed = true) })
        dao.upsertReviews(Placement.initialReviews(c, start, today).map { it.toEntity() })
    }

    suspend fun setBookmark(key: String, on: Boolean) =
        if (on) dao.insertBookmark(BookmarkEntity(key, System.currentTimeMillis())) else dao.deleteBookmark(key)

    suspend fun saveNote(key: String, text: String) =
        if (text.isBlank()) dao.deleteNote(key) else dao.upsertNote(NoteEntity(key, text.trim(), System.currentTimeMillis()))

    // clearAllTables is blocking, unlike the DAO's suspend functions.
    suspend fun resetAll() = withContext(Dispatchers.IO) { db.clearAllTables() }

    private fun LessonStateEntity.toModel() =
        LessonState(lessonId, quickDone, practiceDone, exerciseDone, deepDone, placed, completedDay, lastOpenedDay)

    private fun ReviewEntity.toModel() =
        ReviewState(questionId, lessonId, box, dueDay, lastDay, correctCount, wrongCount, lastCorrect)

    private fun ReviewState.toEntity() =
        ReviewEntity(questionId, lessonId, box, dueDay, lastDay, correctCount, wrongCount, lastCorrect)

    companion object {
        private const val CP = "cp:"
        private const val CASE = "case:"

        fun key(type: String, id: String) = "$type:$id"
    }
}
