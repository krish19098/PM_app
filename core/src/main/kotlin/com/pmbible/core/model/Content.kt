package com.pmbible.core.model

import kotlinx.serialization.Serializable

/*
 * Bundled, versioned learning content. Everything here is read-only at runtime;
 * learner progress lives separately and references content only by stable ids,
 * so content can be revised without losing progress.
 */

@Serializable
enum class Level { BEGINNER, INTERMEDIATE, ADVANCED }

/** A piece of text available in simple English and Thanglish (Tamil-English). */
@Serializable
data class Bilingual(val en: String, val ta: String = "")

@Serializable
data class Manifest(
    val contentVersion: Int,
    val reviewedOn: String,
    val unitFiles: List<String>,
    val coverageNote: String,
)

@Serializable
data class UnitFile(
    val unit: CurriculumUnit,
    val lessons: List<Lesson>,
    val checkpoint: Checkpoint,
)

@Serializable
data class CurriculumUnit(
    val id: String,
    val order: Int,
    val title: String,
    val summary: String,
    val level: Level,
    /** Units whose lessons should be completed first. */
    val prerequisites: List<String> = emptyList(),
)

@Serializable
data class Lesson(
    val id: String,
    val unitId: String,
    val title: String,
    val level: Level,
    val objectives: List<String>,
    /** Lesson ids that must be understood first. */
    val prerequisites: List<String> = emptyList(),
    /** Lesson ids that are conceptually connected but not required. */
    val related: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val quickMinutes: Int = 5,
    val deepMinutes: Int = 8,
    val quick: QuickLearn,
    val deep: List<DeepSection>,
    val example: String,
    val counterexample: String,
    val mistakes: List<String>,
    val keyTerms: List<KeyTerm> = emptyList(),
    val questions: List<Question>,
    val exercise: Exercise,
)

@Serializable
data class QuickLearn(
    val explanation: Bilingual,
    val analogy: Bilingual,
    val takeaway: Bilingual,
)

@Serializable
data class DeepSection(val title: String, val body: String)

@Serializable
data class KeyTerm(val term: String, val meaning: String)

@Serializable
enum class QuestionType { SINGLE, MULTI }

/** RECALL checks understanding of the concept; APPLICATION asks for a decision in context. */
@Serializable
enum class QuestionKind { RECALL, APPLICATION }

@Serializable
data class Question(
    val id: String,
    val type: QuestionType = QuestionType.SINGLE,
    val kind: QuestionKind = QuestionKind.RECALL,
    val prompt: String,
    val options: List<AnswerOption>,
    val explanation: String,
    /** Lesson this question assesses; filled in by the parser for lesson questions. */
    val lessonId: String = "",
)

@Serializable
data class AnswerOption(val text: String, val correct: Boolean = false, val why: String)

/** An open-ended application task, self-assessed against a checklist. */
@Serializable
data class Exercise(
    val scenario: String,
    val prompt: String,
    val modelAnswer: String,
    val checklist: List<String>,
)

/** A cumulative, multi-step challenge at the end of a unit. */
@Serializable
data class Checkpoint(
    val id: String,
    val title: String,
    val scenario: String,
    val steps: List<Question>,
    val debrief: String,
    val minutes: Int = 8,
)

@Serializable
enum class ScenarioType { HYPOTHETICAL, PUBLIC_FACTS }

@Serializable
data class CaseStudy(
    val id: String,
    val title: String,
    val scenarioType: ScenarioType = ScenarioType.HYPOTHETICAL,
    val label: String,
    val level: Level,
    val context: String,
    val constraints: List<String>,
    val decisions: List<Question>,
    val alternatives: String,
    val takeaway: String,
    val lessonIds: List<String>,
    val sources: List<Source> = emptyList(),
    val minutes: Int = 10,
)

@Serializable
enum class InterviewCategory(val label: String) {
    PRODUCT_SENSE("Product sense"),
    EXECUTION("Execution"),
    METRICS("Metrics & analytics"),
    STRATEGY("Strategy"),
    PRIORITISATION("Prioritisation"),
    BEHAVIOURAL("Behavioural"),
    AI_PM("AI product"),
    ESTIMATION("Estimation"),
}

/**
 * ORIGINAL: written for this app. REPRESENTATIVE: typical of a company's interview
 * style or domain, NOT a verified question that company asked.
 */
@Serializable
enum class QuestionSourceType(val label: String) {
    ORIGINAL("Original practice question"),
    REPRESENTATIVE("Representative practice question — not a verified company question"),
}

@Serializable
data class InterviewQuestion(
    val id: String,
    val category: InterviewCategory,
    val level: Level,
    val roles: List<String>,
    val company: String? = null,
    val sourceType: QuestionSourceType,
    val prompt: String,
    val lessonIds: List<String>,
    val approach: List<String>,
    val rubric: List<String>,
    val signals: List<Signal> = emptyList(),
    val pitfalls: List<String>,
    val followUps: List<String>,
)

/** Keyword groups used by the local self-review helper (not AI grading). */
@Serializable
data class Signal(val label: String, val keywords: List<String>)

@Serializable
data class GlossaryTerm(
    val id: String,
    val term: String,
    val definition: String,
    val lessonId: String? = null,
)

@Serializable
data class Source(val title: String, val url: String, val publisher: String)

@Serializable
data class IndustryUpdate(
    val id: String,
    val title: String,
    val date: String,
    val summary: String,
    val whyItMatters: String,
    val lessonIds: List<String>,
    val source: Source,
)

/** The complete, indexed content library. */
data class Curriculum(
    val manifest: Manifest,
    val units: List<CurriculumUnit>,
    val lessons: List<Lesson>,
    val checkpoints: Map<String, Checkpoint>,
    val cases: List<CaseStudy>,
    val interview: List<InterviewQuestion>,
    val glossary: List<GlossaryTerm>,
    val updates: List<IndustryUpdate>,
) {
    val lessonById: Map<String, Lesson> = lessons.associateBy { it.id }
    val unitById: Map<String, CurriculumUnit> = units.associateBy { it.id }
    val lessonsByUnit: Map<String, List<Lesson>> = lessons.groupBy { it.unitId }
    val questionById: Map<String, Question> = buildMap {
        lessons.forEach { l -> l.questions.forEach { put(it.id, it) } }
        checkpoints.values.forEach { c -> c.steps.forEach { put(it.id, it) } }
        cases.forEach { c -> c.decisions.forEach { put(it.id, it) } }
    }
    val caseById: Map<String, CaseStudy> = cases.associateBy { it.id }
    val interviewById: Map<String, InterviewQuestion> = interview.associateBy { it.id }

    /** Lessons in learning-path order (unit order, then file order). */
    val orderedLessons: List<Lesson> = units.sortedBy { it.order }.flatMap { lessonsByUnit[it.id].orEmpty() }

    /** Lessons that list [lessonId] as a prerequisite (the "unlocks" edge of the graph). */
    fun dependents(lessonId: String): List<Lesson> = lessons.filter { lessonId in it.prerequisites }
}
