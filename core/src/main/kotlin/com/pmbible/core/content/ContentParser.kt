package com.pmbible.core.content

import com.pmbible.core.model.CaseStudy
import com.pmbible.core.model.Curriculum
import com.pmbible.core.model.GlossaryTerm
import com.pmbible.core.model.IndustryUpdate
import com.pmbible.core.model.InterviewQuestion
import com.pmbible.core.model.Manifest
import com.pmbible.core.model.UnitFile
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Parses the bundled content files. [read] maps a path relative to the content
 * root (e.g. "units/u01.json") to its text, so the same parser serves Android
 * assets and JVM tests.
 */
object ContentParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(read: (String) -> String): Curriculum {
        val manifest = json.decodeFromString(Manifest.serializer(), read("manifest.json"))
        val unitFiles = manifest.unitFiles.map { json.decodeFromString(UnitFile.serializer(), read(it)) }
        // Lesson questions carry their lesson id implicitly; stamp it so mastery can attribute answers.
        val lessons = unitFiles.flatMap { uf ->
            uf.lessons.map { lesson -> lesson.copy(questions = lesson.questions.map { it.copy(lessonId = lesson.id) }) }
        }
        return Curriculum(
            manifest = manifest,
            units = unitFiles.map { it.unit }.sortedBy { it.order },
            lessons = lessons,
            checkpoints = unitFiles.associate { it.unit.id to it.checkpoint },
            cases = json.decodeFromString(ListSerializer(CaseStudy.serializer()), read("cases.json")),
            interview = json.decodeFromString(ListSerializer(InterviewQuestion.serializer()), read("interview.json")),
            glossary = json.decodeFromString(ListSerializer(GlossaryTerm.serializer()), read("glossary.json")),
            updates = json.decodeFromString(ListSerializer(IndustryUpdate.serializer()), read("updates.json"))
                .sortedByDescending { it.date },
        )
    }
}
