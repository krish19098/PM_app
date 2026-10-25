package com.pmbible.core.domain

import com.pmbible.core.model.Curriculum

enum class ResultType(val label: String) {
    LESSON("Concept"), TERM("Glossary"), CASE("Case study"), INTERVIEW("Interview"), UPDATE("Industry update")
}

data class SearchResult(val type: ResultType, val id: String, val title: String, val snippet: String, val score: Int)

/**
 * In-memory full-text index over the bundled library. Every query word must match
 * (prefix match on words); title and key-term hits rank above body hits.
 */
class SearchIndex(curriculum: Curriculum) {

    private class Doc(
        val type: ResultType,
        val id: String,
        val title: String,
        val snippet: String,
        val titleWords: Set<String>,
        val keyWords: Set<String>,
        val bodyWords: Map<String, Int>,
    )

    private val docs: List<Doc> = buildList {
        curriculum.lessons.forEach { l ->
            add(doc(ResultType.LESSON, l.id, l.title, l.quick.explanation.en,
                keys = l.tags + l.keyTerms.map { it.term },
                body = listOf(l.quick.explanation.en, l.quick.explanation.ta, l.quick.analogy.en, l.example) +
                    l.objectives + l.deep.flatMap { listOf(it.title, it.body) } + l.keyTerms.map { it.meaning }))
        }
        curriculum.glossary.forEach { g -> add(doc(ResultType.TERM, g.id, g.term, g.definition, emptyList(), listOf(g.definition))) }
        curriculum.cases.forEach { c ->
            add(doc(ResultType.CASE, c.id, c.title, c.context, listOf(c.label), listOf(c.context, c.takeaway) + c.constraints))
        }
        curriculum.interview.forEach { q ->
            add(doc(ResultType.INTERVIEW, q.id, q.prompt, q.category.label, listOfNotNull(q.category.label, q.company), q.approach))
        }
        curriculum.updates.forEach { u ->
            add(doc(ResultType.UPDATE, u.id, u.title, u.summary, listOf(u.source.publisher), listOf(u.summary, u.whyItMatters)))
        }
    }

    private fun doc(type: ResultType, id: String, title: String, snippet: String, keys: List<String>, body: List<String>) = Doc(
        type, id, title, snippet,
        titleWords = tokenize(title).toSet(),
        keyWords = keys.flatMap { tokenize(it) }.toSet(),
        bodyWords = body.flatMap { tokenize(it) }.groupingBy { it }.eachCount(),
    )

    fun search(query: String, limit: Int = 40): List<SearchResult> {
        val terms = tokenize(query)
        if (terms.isEmpty()) return emptyList()
        val phrase = query.trim().lowercase()
        return docs.mapNotNull { d ->
            var score = 0
            for (t in terms) {
                val title = d.titleWords.count { it.startsWith(t) }
                val exactTitle = if (t in d.titleWords) 5 else 0
                val key = d.keyWords.count { it.startsWith(t) }
                val body = d.bodyWords.entries.filter { it.key.startsWith(t) }.sumOf { it.value }.coerceAtMost(5)
                if (title + key + body == 0) return@mapNotNull null
                score += title * 10 + exactTitle + key * 6 + body
            }
            if (d.title.lowercase().contains(phrase)) score += 15
            SearchResult(d.type, d.id, d.title, d.snippet, score)
        }.sortedWith(compareByDescending<SearchResult> { it.score }.thenBy { it.type.ordinal }).take(limit)
    }

    companion object {
        private val STOP = setOf("a", "an", "the", "of", "to", "and", "or", "in", "is", "what", "how", "for", "on", "do", "i")

        fun tokenize(text: String): List<String> =
            text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() && it !in STOP }
    }
}
