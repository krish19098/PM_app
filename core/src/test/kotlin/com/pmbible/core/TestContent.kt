package com.pmbible.core

import com.pmbible.core.content.ContentParser
import com.pmbible.core.model.Curriculum
import java.io.File

/** Loads the real bundled content once for all tests. */
object TestContent {
    val dir = File(System.getProperty("pmbible.contentDir") ?: "../app/src/main/assets/content")
    val curriculum: Curriculum by lazy { ContentParser.load { File(dir, it).readText() } }
}
