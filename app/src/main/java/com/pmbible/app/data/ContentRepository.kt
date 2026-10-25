package com.pmbible.app.data

import android.content.Context
import com.pmbible.core.content.ContentParser
import com.pmbible.core.domain.SearchIndex
import com.pmbible.core.model.Curriculum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Loads the bundled curriculum from assets once and caches it with its search index. */
class ContentRepository(private val context: Context) {

    data class Library(val curriculum: Curriculum, val search: SearchIndex)

    private val mutex = Mutex()
    @Volatile private var cached: Library? = null

    suspend fun library(): Library = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            val curriculum = ContentParser.load { path ->
                context.assets.open("content/$path").bufferedReader().use { it.readText() }
            }
            Library(curriculum, SearchIndex(curriculum))
        }.also { cached = it }
    }
}
