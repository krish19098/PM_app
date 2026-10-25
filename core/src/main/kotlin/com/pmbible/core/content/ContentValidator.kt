package com.pmbible.core.content

import com.pmbible.core.model.Curriculum
import com.pmbible.core.model.Question
import com.pmbible.core.model.QuestionType

/**
 * Editorial quality gate for content. Run in tests so broken references, duplicate ids,
 * prerequisite cycles or badly formed questions never ship.
 */
object ContentValidator {

    fun validate(c: Curriculum): List<String> {
        val errors = mutableListOf<String>()
        val lessonIds = c.lessonById.keys

        fun dup(kind: String, ids: List<String>) =
            ids.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { errors += "Duplicate $kind id: $it" }

        dup("lesson", c.lessons.map { it.id })
        dup("unit", c.units.map { it.id })
        dup("question", c.lessons.flatMap { l -> l.questions.map { it.id } } +
            c.checkpoints.values.flatMap { cp -> cp.steps.map { it.id } } +
            c.cases.flatMap { cs -> cs.decisions.map { it.id } })
        dup("case", c.cases.map { it.id })
        dup("interview", c.interview.map { it.id })
        dup("glossary", c.glossary.map { it.id })
        dup("update", c.updates.map { it.id })
        dup("lesson title", c.lessons.map { it.title.lowercase() })

        c.units.forEach { u ->
            u.prerequisites.filter { it !in c.unitById }.forEach { errors += "Unit ${u.id}: unknown prerequisite unit $it" }
            if (c.lessonsByUnit[u.id].isNullOrEmpty()) errors += "Unit ${u.id} has no lessons"
        }

        c.lessons.forEach { l ->
            if (l.unitId !in c.unitById) errors += "Lesson ${l.id}: unknown unit ${l.unitId}"
            (l.prerequisites + l.related).filter { it !in lessonIds }
                .forEach { errors += "Lesson ${l.id}: unknown lesson reference $it" }
            if (l.id in l.prerequisites) errors += "Lesson ${l.id} lists itself as prerequisite"
            if (l.objectives.isEmpty()) errors += "Lesson ${l.id}: no objectives"
            if (l.deep.size < 3) errors += "Lesson ${l.id}: deep dive needs at least 3 sections"
            if (l.questions.size < 3) errors += "Lesson ${l.id}: needs at least 3 questions"
            if (l.quick.explanation.ta.isBlank()) errors += "Lesson ${l.id}: missing Thanglish explanation"
            if (l.mistakes.isEmpty()) errors += "Lesson ${l.id}: no common mistakes"
            if (l.exercise.checklist.size < 2) errors += "Lesson ${l.id}: exercise checklist too short"
            l.questions.forEach { checkQuestion("Lesson ${l.id}", it, errors) }
        }

        // Prerequisites must point backwards in path order, which also rules out cycles.
        val position = c.orderedLessons.withIndex().associate { it.value.id to it.index }
        c.lessons.forEach { l ->
            l.prerequisites.forEach { p ->
                val pp = position[p] ?: return@forEach
                if (pp >= position.getValue(l.id)) errors += "Lesson ${l.id}: prerequisite $p comes later in the path"
            }
        }

        c.units.forEach { u ->
            val cp = c.checkpoints[u.id]
            if (cp == null) {
                errors += "Unit ${u.id}: missing checkpoint"
                return@forEach
            }
            if (cp.steps.size < 3) errors += "Checkpoint ${cp.id}: needs at least 3 steps"
            cp.steps.forEach { s ->
                checkQuestion("Checkpoint ${cp.id}", s, errors)
                if (s.lessonId !in lessonIds) errors += "Checkpoint ${cp.id} step ${s.id}: unknown lessonId '${s.lessonId}'"
            }
        }

        c.cases.forEach { cs ->
            cs.lessonIds.filter { it !in lessonIds }.forEach { errors += "Case ${cs.id}: unknown lesson $it" }
            if (cs.label.isBlank()) errors += "Case ${cs.id}: scenario label required"
            cs.decisions.forEach { d ->
                checkQuestion("Case ${cs.id}", d, errors)
                if (d.lessonId !in lessonIds) errors += "Case ${cs.id} decision ${d.id}: unknown lessonId '${d.lessonId}'"
            }
        }
        c.interview.forEach { q ->
            if (q.lessonIds.isEmpty()) errors += "Interview ${q.id}: must link to at least one lesson"
            q.lessonIds.filter { it !in lessonIds }.forEach { errors += "Interview ${q.id}: unknown lesson $it" }
            if (q.rubric.isEmpty() || q.approach.isEmpty()) errors += "Interview ${q.id}: needs approach and rubric"
        }
        c.glossary.forEach { g ->
            if (g.lessonId != null && g.lessonId !in lessonIds) errors += "Glossary ${g.id}: unknown lesson ${g.lessonId}"
        }
        c.updates.forEach { u ->
            if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(u.date)) errors += "Update ${u.id}: date must be yyyy-MM-dd"
            if (!u.source.url.startsWith("https://")) errors += "Update ${u.id}: source must be an https link"
            u.lessonIds.filter { it !in lessonIds }.forEach { errors += "Update ${u.id}: unknown lesson $it" }
        }
        return errors
    }

    private fun checkQuestion(where: String, q: Question, errors: MutableList<String>) {
        val correct = q.options.count { it.correct }
        if (q.options.size < 2) errors += "$where question ${q.id}: needs at least 2 options"
        if (q.type == QuestionType.SINGLE && correct != 1) errors += "$where question ${q.id}: SINGLE needs exactly 1 correct option"
        if (q.type == QuestionType.MULTI && correct < 1) errors += "$where question ${q.id}: MULTI needs a correct option"
        if (q.options.any { it.why.isBlank() }) errors += "$where question ${q.id}: every option needs feedback"
        if (q.explanation.isBlank()) errors += "$where question ${q.id}: missing explanation"
    }
}
