package com.pmbible.core

import com.pmbible.core.content.ContentValidator
import com.pmbible.core.model.QuestionKind
import com.pmbible.core.model.QuestionSourceType
import com.pmbible.core.model.ScenarioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentTest {
    private val c = TestContent.curriculum

    @Test
    fun contentPassesEditorialValidation() {
        val errors = ContentValidator.validate(c)
        assertTrue("Content errors:\n" + errors.joinToString("\n"), errors.isEmpty())
    }

    @Test
    fun coverageMatchesManifest() {
        assertEquals(14, c.units.size)
        assertTrue("Expected at least 50 lessons, got ${c.lessons.size}", c.lessons.size >= 50)
        assertTrue(c.cases.size >= 10)
        assertTrue(c.interview.size >= 35)
        assertTrue(c.glossary.size >= 60)
        assertTrue(c.updates.isNotEmpty())
    }

    @Test
    fun everyLessonMixesRecallAndApplication() {
        c.lessons.forEach { l ->
            assertTrue("${l.id} lacks recall questions", l.questions.any { it.kind == QuestionKind.RECALL })
            assertTrue("${l.id} lacks application questions", l.questions.any { it.kind == QuestionKind.APPLICATION })
        }
    }

    @Test
    fun companyQuestionsAreNeverPresentedAsVerified() {
        c.interview.filter { it.company != null }.forEach {
            assertEquals("${it.id} names a company but isn't labelled representative", QuestionSourceType.REPRESENTATIVE, it.sourceType)
            assertTrue("${it.id} prompt must say it's practice in context", it.prompt.startsWith("Practice in the context of"))
        }
    }

    @Test
    fun casesAreLabelledHypothetical() {
        c.cases.forEach {
            assertEquals(ScenarioType.HYPOTHETICAL, it.scenarioType)
            assertTrue(it.label.contains("Hypothetical", ignoreCase = true))
        }
    }

    @Test
    fun lessonQuestionsCarryTheirLessonId() {
        c.lessons.forEach { l -> l.questions.forEach { assertEquals(l.id, it.lessonId) } }
    }

    @Test
    fun updatesAreSortedNewestFirst() {
        assertEquals(c.updates.sortedByDescending { it.date }, c.updates)
    }
}
