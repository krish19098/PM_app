package com.pmbible.app.ui.learn

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pmbible.app.ui.components.AppCard
import com.pmbible.app.ui.components.BilingualText
import com.pmbible.app.ui.components.Callout
import com.pmbible.app.ui.components.SectionHeader
import com.pmbible.core.model.LanguageMode
import com.pmbible.core.model.Lesson

/** Quick Learn: explanation, analogy, one example and the takeaway. */
@Composable
fun QuickLearnContent(lesson: Lesson, mode: LanguageMode) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("You'll learn to")
        lesson.objectives.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
        SectionHeader("The idea")
        BilingualText(lesson.quick.explanation, mode)
        SectionHeader("Think of it like…")
        AppCard { BilingualText(lesson.quick.analogy, mode, MaterialTheme.typography.bodyMedium) }
        SectionHeader("Example")
        Text(lesson.example, style = MaterialTheme.typography.bodyMedium)
        SectionHeader("Takeaway")
        AppCard(container = MaterialTheme.colorScheme.primaryContainer) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Column { BilingualText(lesson.quick.takeaway, mode, MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

/** Deep Dive: sections revealed progressively, then counterexample, mistakes and key terms. */
@Composable
fun DeepDiveContent(lesson: Lesson) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Callout("Open each section when you're ready — depth is revealed step by step.")
        lesson.deep.forEachIndexed { i, section -> ExpandableSection(section.title, section.body, initiallyOpen = i == 0) }
        ExpandableSection("Counterexample — what this is not", lesson.counterexample)
        ExpandableSection("Common mistakes", lesson.mistakes.joinToString("\n") { "•  $it" })
        if (lesson.keyTerms.isNotEmpty()) {
            ExpandableSection("Key terms", lesson.keyTerms.joinToString("\n\n") { "${it.term}: ${it.meaning}" })
        }
    }
}

@Composable
fun ExpandableSection(title: String, body: String, initiallyOpen: Boolean = false) {
    var open by rememberSaveable(title) { mutableStateOf(initiallyOpen) }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(open, role = Role.Button) { open = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) "Collapse" else "Expand")
        }
        if (open) Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
        HorizontalDivider()
    }
}
