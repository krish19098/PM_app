package com.pmbible.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * Learner data only. Content is bundled separately and referenced by stable ids,
 * so content updates never touch these tables.
 */

@Entity(tableName = "lesson_state")
data class LessonStateEntity(
    @PrimaryKey val lessonId: String,
    val quickDone: Boolean = false,
    val practiceDone: Boolean = false,
    val exerciseDone: Boolean = false,
    val deepDone: Boolean = false,
    val placed: Boolean = false,
    val completedDay: Long? = null,
    val lastOpenedDay: Long? = null,
)

@Entity(tableName = "review")
data class ReviewEntity(
    @PrimaryKey val questionId: String,
    val lessonId: String,
    val box: Int,
    val dueDay: Long,
    val lastDay: Long,
    val correctCount: Int,
    val wrongCount: Int,
    val lastCorrect: Boolean,
)

@Entity(tableName = "attempt")
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: String,
    val lessonId: String,
    val correct: Boolean,
    val day: Long,
    val timestamp: Long,
    /** Where the answer was given: practice, review, checkpoint or case. */
    val context: String,
)

@Entity(tableName = "app_score")
data class AppScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: String,
    val sourceId: String,
    val score: Double,
    val day: Long,
)

@Entity(tableName = "activity_day")
data class ActivityDayEntity(@PrimaryKey val day: Long, val minutes: Int)

/** Finished checkpoints ("cp:<unitId>") and cases ("case:<caseId>") with their score. */
@Entity(tableName = "completion")
data class CompletionEntity(@PrimaryKey val key: String, val day: Long, val score: Double)

@Entity(tableName = "interview_attempt")
data class InterviewAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: String,
    val category: String,
    val answer: String,
    val score: Double,
    val day: Long,
    val timestamp: Long,
)

@Entity(tableName = "exercise_answer")
data class ExerciseAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonId: String,
    val answer: String,
    val score: Double,
    val timestamp: Long,
)

/** key = "<TYPE>:<id>", e.g. "LESSON:l_rice". */
@Entity(tableName = "bookmark")
data class BookmarkEntity(@PrimaryKey val key: String, val createdAt: Long)

@Entity(tableName = "note")
data class NoteEntity(@PrimaryKey val key: String, val text: String, val updatedAt: Long)

@Dao
interface ProgressDao {
    @Query("SELECT * FROM lesson_state") fun lessonStates(): Flow<List<LessonStateEntity>>
    @Query("SELECT * FROM lesson_state WHERE lessonId = :id") suspend fun lessonState(id: String): LessonStateEntity?
    @Upsert suspend fun upsertLessonState(e: LessonStateEntity)
    @Upsert suspend fun upsertLessonStates(e: List<LessonStateEntity>)

    @Query("SELECT * FROM review") fun reviews(): Flow<List<ReviewEntity>>
    @Query("SELECT * FROM review WHERE questionId = :id") suspend fun review(id: String): ReviewEntity?
    @Upsert suspend fun upsertReview(e: ReviewEntity)
    @Upsert suspend fun upsertReviews(e: List<ReviewEntity>)

    @Insert suspend fun insertAttempt(e: AttemptEntity)
    @Query("SELECT * FROM attempt ORDER BY timestamp DESC") fun attempts(): Flow<List<AttemptEntity>>

    @Insert suspend fun insertAppScore(e: AppScoreEntity)
    @Query("SELECT * FROM app_score") fun appScores(): Flow<List<AppScoreEntity>>

    @Query("SELECT * FROM activity_day") fun activityDays(): Flow<List<ActivityDayEntity>>
    @Query("SELECT * FROM activity_day WHERE day = :day") suspend fun activityDay(day: Long): ActivityDayEntity?
    @Upsert suspend fun upsertActivityDay(e: ActivityDayEntity)

    @Upsert suspend fun upsertCompletion(e: CompletionEntity)
    @Query("SELECT * FROM completion") fun completions(): Flow<List<CompletionEntity>>

    @Insert suspend fun insertInterviewAttempt(e: InterviewAttemptEntity)
    @Query("SELECT * FROM interview_attempt ORDER BY timestamp DESC") fun interviewAttempts(): Flow<List<InterviewAttemptEntity>>

    @Insert suspend fun insertExerciseAnswer(e: ExerciseAnswerEntity)
    @Query("SELECT * FROM exercise_answer ORDER BY timestamp DESC") fun exerciseAnswers(): Flow<List<ExerciseAnswerEntity>>

    @Query("SELECT * FROM bookmark ORDER BY createdAt DESC") fun bookmarks(): Flow<List<BookmarkEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBookmark(e: BookmarkEntity)
    @Query("DELETE FROM bookmark WHERE `key` = :key") suspend fun deleteBookmark(key: String)

    @Query("SELECT * FROM note ORDER BY updatedAt DESC") fun notes(): Flow<List<NoteEntity>>
    @Upsert suspend fun upsertNote(e: NoteEntity)
    @Query("DELETE FROM note WHERE `key` = :key") suspend fun deleteNote(key: String)
}

@Database(
    entities = [
        LessonStateEntity::class, ReviewEntity::class, AttemptEntity::class, AppScoreEntity::class,
        ActivityDayEntity::class, CompletionEntity::class, InterviewAttemptEntity::class,
        ExerciseAnswerEntity::class, BookmarkEntity::class, NoteEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "pmbible.db").build()
    }
}
