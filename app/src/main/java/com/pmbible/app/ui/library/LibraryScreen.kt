package com.pmbible.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pmbible.app.ui.AppViewModel
import com.pmbible.app.ui.components.AppCard
import com.pmbible.app.ui.components.EmptyState
import com.pmbible.app.ui.components.Pill
import com.pmbible.app.ui.components.ScreenScaffold
import com.pmbible.app.ui.components.SectionHeader
import com.pmbible.app.ui.components.block
import com.pmbible.core.domain.ResultType
import com.pmbible.core.model.GlossaryTerm

/** Search across every concept, term, case, interview question and update; browse when the query is empty. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    app: AppViewModel,
    onOpenLesson: (String) -> Unit,
    onOpenCase: (String) -> Unit,
    onOpenInterview: (String) -> Unit,
    onOpenUpdates: () -> Unit,
) {
    val library by app.library.collectAsState()
    val lib = library ?: return
    val c = lib.curriculum
    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var term by remember { mutableStateOf<GlossaryTerm?>(null) }
    val results = remember(query) { lib.search.search(query) }
    val shown = results.filter { typeFilter == null || it.type.name == typeFilter }

    fun open(type: ResultType, id: String) {
        when (type) {
            ResultType.LESSON -> onOpenLesson(id)
            ResultType.TERM -> term = c.glossary.firstOrNull { it.id == id }
            ResultType.CASE -> onOpenCase(id)
            ResultType.INTERVIEW -> onOpenInterview(id)
            ResultType.UPDATE -> onOpenUpdates()
        }
    }

    ScreenScaffold("PM Library") {
        block {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; typeFilter = null },
                placeholder = { Text("Search any PM topic — e.g. retention, RICE, RAG") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Clear, contentDescription = "Clear search") }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.isNotBlank()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text("All (${results.size})") })
                    ResultType.entries.forEach { t ->
                        val n = results.count { it.type == t }
                        if (n > 0) FilterChip(selected = typeFilter == t.name, onClick = { typeFilter = t.name }, label = { Text("${t.label} ($n)") })
                    }
                }
            }
        }

        if (query.isNotBlank()) {
            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.SearchOff, "No matches",
                        "Try a simpler word, or browse the glossary below by clearing the search.",
                        "Clear search" to { query = "" },
                    )
                }
            }
            shown.forEach { r ->
                item(key = "${r.type}-${r.id}") {
                    AppCard(onClick = { open(r.type, r.id) }) {
                        Pill(r.type.label)
                        Text(r.title, style = MaterialTheme.typography.titleMedium)
                        Text(r.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            item {
                AppCard(onClick = onOpenUpdates) {
                    Icon(Icons.Outlined.Newspaper, contentDescription = null)
                    Text("PM & AI developments", style = MaterialTheme.typography.titleMedium)
                    Text("A curated, dated and sourced snapshot — not a live feed.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item { SectionHeader("Concepts by unit") }
            c.units.forEach { u ->
                block(key = u.id) {
                    Text(u.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        c.lessonsByUnit[u.id].orEmpty().forEach { l ->
                            AssistChip(onClick = { onOpenLesson(l.id) }, label = { Text(l.title) })
                        }
                    }
                }
            }
            item { SectionHeader("Glossary A–Z (${c.glossary.size} terms)") }
            c.glossary.sortedBy { it.term.lowercase() }.forEach { g ->
                item(key = g.id) {
                    AppCard(onClick = { term = g }) {
                        Text(g.term, style = MaterialTheme.typography.titleSmall)
                        Text(g.definition, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                }
            }
        }
    }

    term?.let { g ->
        val lesson = g.lessonId?.let { c.lessonById[it] }
        AlertDialog(
            onDismissRequest = { term = null },
            title = { Text(g.term) },
            text = { Text(g.definition) },
            confirmButton = {
                if (lesson != null) TextButton(onClick = { term = null; onOpenLesson(lesson.id) }) { Text("Open “${lesson.title}”") }
            },
            dismissButton = { TextButton(onClick = { term = null }) { Text("Close") } },
        )
    }
}
