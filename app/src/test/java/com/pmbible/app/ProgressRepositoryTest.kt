package com.pmbible.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pmbible.app.data.AnswerContext
import com.pmbible.app.data.ContentRepository
import com.pmbible.app.data.ProgressRepository
import com.pmbible.app.data.db.AppDatabase
import com.pmbible.core.model.Segment
import com.pmbible.core.model.StartingPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgressRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ProgressRepository
    private val today = 20_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = ProgressRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun bundledContentLoadsFromAssets() = runTest {
        val lib = ContentRepository(ApplicationProvider.getApplicationContext()).library()
        assertEquals(14, lib.curriculum.units.size)
        assertTrue(lib.search.search("north star").isNotEmpty())
    }

    @Test
    fun completingQuickAndPracticeCompletesLesson() = runTest {
        repo.markSegment("l_pm_role", Segment.QUICK, today)
        assertFalse(repo.snapshot.first().lesson("l_pm_role").completed)
        repo.markSegment("l_pm_role", Segment.PRACTICE, today)
        val state = repo.snapshot.first().lesson("l_pm_role")
        assertTrue(state.completed)
        assertEquals(today, state.completedDay)
    }

    @Test
    fun answersScheduleReviewsAndLogAttempts() = runTest {
        val c = ContentRepository(ApplicationProvider.getApplicationContext()).library().curriculum
        val q = c.lessonById.getValue("l_pm_role").questions.first()
        repo.recordAnswer(q, correct = true, confident = true, context = AnswerContext.PRACTICE, sourceId = q.lessonId, today = today)
        val snap = repo.snapshot.first()
        assertEquals(1, snap.reviews.getValue(q.id).box)
        assertEquals(today + 1, snap.reviews.getValue(q.id).dueDay)
        assertEquals(1, snap.attempts.size)
    }

    @Test
    fun checkpointAnswersCountAsApplicationNotReviews() = runTest {
        val c = ContentRepository(ApplicationProvider.getApplicationContext()).library().curriculum
        val step = c.checkpoints.getValue("u01").steps.first()
        repo.recordAnswer(step, correct = true, confident = true, context = AnswerContext.CHECKPOINT, sourceId = "cp:u01", today = today)
        repo.completeCheckpoint("u01", 1.0, today)
        val snap = repo.snapshot.first()
        assertTrue(snap.reviews.isEmpty())
        assertEquals(1, snap.appScores.size)
        assertTrue("u01" in snap.checkpointsDone)
    }

    @Test
    fun placementMarksLessonsAndQueuesReviews() = runTest {
        val c = ContentRepository(ApplicationProvider.getApplicationContext()).library().curriculum
        repo.applyPlacement(c, StartingPoint.BASICS, today)
        val snap = repo.snapshot.first()
        assertTrue(snap.lesson("l_pm_role").placed)
        assertTrue(snap.reviews.isNotEmpty())
    }

    @Test
    fun minutesAccumulatePerDay() = runTest {
        repo.addMinutes(today, 5)
        repo.addMinutes(today, 7)
        assertEquals(12, repo.snapshot.first().minutesByDay[today])
    }

    @Test
    fun notesAndBookmarksPersistAndClear() = runTest {
        repo.setBookmark("LESSON:l_rice", true)
        repo.saveNote("LESSON:l_rice", "  RICE = reach × impact × confidence ÷ effort ")
        assertEquals("LESSON:l_rice", repo.bookmarks.first().single().key)
        assertEquals("RICE = reach × impact × confidence ÷ effort", repo.notes.first().single().text)
        repo.saveNote("LESSON:l_rice", "")
        repo.setBookmark("LESSON:l_rice", false)
        assertTrue(repo.notes.first().isEmpty())
        assertTrue(repo.bookmarks.first().isEmpty())
    }
}
