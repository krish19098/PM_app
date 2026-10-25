package com.pmbible.core

import com.pmbible.core.domain.AnswerReview
import com.pmbible.core.domain.Insights
import com.pmbible.core.domain.ResultType
import com.pmbible.core.domain.SearchIndex
import com.pmbible.core.model.Attempt
import com.pmbible.core.model.InterviewCategory
import com.pmbible.core.model.InterviewScore
import com.pmbible.core.model.ProgressSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SearchAndInsightsTest {
    private val c = TestContent.curriculum
    private val index = SearchIndex(c)

    @Test
    fun exactConceptRanksFirst() {
        assertEquals("l_north_star", index.search("north star").first().id)
        assertEquals("l_rice", index.search("RICE").first().id)
    }

    @Test
    fun searchFindsGlossaryAndAiConcepts() {
        val results = index.search("hallucination")
        assertTrue(results.any { it.type == ResultType.TERM })
        assertTrue(results.any { it.id == "l_ai_evaluation" })
    }

    @Test
    fun prefixAndThanglishTermsMatch() {
        assertTrue(index.search("retent").any { it.id == "l_retention_cohorts" })
        assertTrue(index.search("velai").isNotEmpty())
    }

    @Test
    fun allWordsMustMatchAndEmptyQueryIsEmpty() {
        assertTrue(index.search("zzqx nonsense").isEmpty())
        assertTrue(index.search("   ").isEmpty())
    }

    @Test
    fun answerReviewDetectsStructuralSignals() {
        val q = c.interviewById.getValue("iq_orders_drop")
        val answer = "First I'd clarify the metric, then check tracking data, segment by platform and region, " +
            "and compare conversion. The risk is a recent release."
        val checks = AnswerReview.review(answer, q).associate { it.signal.label to it.found }
        assertEquals(true, checks["Checks data validity"])
        assertEquals(true, checks["Segments the drop"])
        assertEquals(true, checks["Discusses trade-offs or risks"])
    }

    @Test
    fun weeklyRecapCountsOnlyThisWeek() {
        val today = LocalDate.of(2026, 10, 21).toEpochDay()
        val monday = LocalDate.of(2026, 10, 19).toEpochDay()
        val p = ProgressSnapshot(
            minutesByDay = mapOf(monday to 12, monday - 1 to 30, today to 8),
            attempts = listOf(Attempt("q1", "l_pm_role", true, today), Attempt("q2", "l_pm_role", false, today), Attempt("q3", "l_pm_role", true, monday - 3)),
            interviewScores = listOf(
                InterviewScore("a", InterviewCategory.METRICS, 0.4, monday - 10),
                InterviewScore("a", InterviewCategory.METRICS, 0.8, today),
            ),
        )
        val recap = Insights.weeklyRecap(c, p, goal = 5, today = today)
        assertEquals(2, recap.daysActive)
        assertEquals(20, recap.minutes)
        assertEquals(2, recap.answers)
        assertEquals(0.5, recap.accuracy!!, 1e-9)
        assertEquals("Latest attempt per question counts", 0.8, recap.competency.getValue(InterviewCategory.METRICS), 1e-9)
    }
}
