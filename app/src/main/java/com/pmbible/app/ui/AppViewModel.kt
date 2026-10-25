package com.pmbible.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pmbible.app.PmBibleApp
import com.pmbible.app.data.AnswerContext
import com.pmbible.app.data.ContentRepository
import com.pmbible.app.data.db.AttemptEntity
import com.pmbible.app.data.db.ExerciseAnswerEntity
import com.pmbible.app.data.db.InterviewAttemptEntity
import com.pmbible.app.notifications.Notifications
import com.pmbible.core.model.InterviewQuestion
import com.pmbible.core.model.ProgressSnapshot
import com.pmbible.core.model.Question
import com.pmbible.core.model.Segment
import com.pmbible.core.model.UserPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** App-wide state shared by all screens: content, preferences and learner progress. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as PmBibleApp).container

    private val _library = MutableStateFlow<ContentRepository.Library?>(null)
    val library: StateFlow<ContentRepository.Library?> = _library.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    /** Null until DataStore has been read, so the UI never flashes onboarding for returning users. */
    val prefs: StateFlow<UserPrefs?> = container.prefs.prefs
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val loadedSnapshot: StateFlow<ProgressSnapshot?> = container.progress.snapshot
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** True once stored progress has been read at least once. */
    val snapshotReady: StateFlow<Boolean> = loadedSnapshot.map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val snapshot: StateFlow<ProgressSnapshot> = loadedSnapshot.map { it ?: ProgressSnapshot() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProgressSnapshot())

    val bookmarks: StateFlow<Set<String>> = container.progress.bookmarks.map { list -> list.map { it.key }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val notes: StateFlow<Map<String, String>> = container.progress.notes.map { list -> list.associate { it.key to it.text } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val interviewAttempts: StateFlow<List<InterviewAttemptEntity>> = container.progress.interviewAttempts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val exerciseAnswers: StateFlow<List<ExerciseAnswerEntity>> = container.progress.exerciseAnswers
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val attemptLog: StateFlow<List<AttemptEntity>> = container.progress.attemptLog
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** (day, minutes) the learner chose for a session; only applies on that day, else the daily default. */
    private val _todayMinutes = MutableStateFlow<Pair<Long, Int>?>(null)
    val todayMinutes: StateFlow<Pair<Long, Int>?> = _todayMinutes.asStateFlow()

    init {
        loadContent()
        // Re-arm reminders on every launch: covers app updates, restored backups and missed broadcasts.
        viewModelScope.launch { container.reminders.reschedule(container.prefs.prefs.first()) }
    }

    fun loadContent() {
        _loadError.value = null
        viewModelScope.launch {
            runCatching { container.content.library() }
                .onSuccess { _library.value = it }
                .onFailure { _loadError.value = it.message ?: "Couldn't load the lesson library." }
        }
    }

    fun today(): Long = LocalDate.now().toEpochDay()

    fun setTodayMinutes(minutes: Int?) { _todayMinutes.value = minutes?.let { today() to it } }

    fun completeOnboarding(newPrefs: UserPrefs) = viewModelScope.launch {
        val day = today()
        val final = newPrefs.copy(onboarded = true, startDay = day)
        container.prefs.update { final }
        container.content.library().let { container.progress.applyPlacement(it.curriculum, final.startingPoint, day) }
        container.reminders.reschedule(final)
    }

    fun updatePrefs(transform: (UserPrefs) -> UserPrefs) = viewModelScope.launch {
        container.prefs.update(transform)
        container.reminders.reschedule(container.prefs.current())
    }

    fun markSegment(lessonId: String, segment: Segment) = viewModelScope.launch {
        container.progress.markSegment(lessonId, segment, today())
    }

    fun openLesson(lessonId: String) = viewModelScope.launch { container.progress.openLesson(lessonId, today()) }

    fun recordAnswer(q: Question, correct: Boolean, confident: Boolean, context: AnswerContext, sourceId: String) =
        viewModelScope.launch { container.progress.recordAnswer(q, correct, confident, context, sourceId, today()) }

    fun recordExercise(lessonId: String, answer: String, score: Double) =
        viewModelScope.launch { container.progress.recordExercise(lessonId, answer, score, today()) }

    fun completeCheckpoint(unitId: String, score: Double) =
        viewModelScope.launch { container.progress.completeCheckpoint(unitId, score, today()) }

    fun completeCase(caseId: String, score: Double) =
        viewModelScope.launch { container.progress.completeCase(caseId, score, today()) }

    fun recordInterview(q: InterviewQuestion, answer: String, score: Double) =
        viewModelScope.launch { container.progress.recordInterview(q.id, q.category, answer, score, q.lessonIds, today()) }

    /** Logs study time; any logged time counts as learning today, which also silences today's reminders. */
    fun addMinutes(minutes: Int) = viewModelScope.launch {
        if (minutes <= 0) return@launch
        container.progress.addMinutes(today(), minutes)
        Notifications.clearLearningReminders(getApplication())
    }

    fun setBookmark(key: String, on: Boolean) = viewModelScope.launch { container.progress.setBookmark(key, on) }

    fun saveNote(key: String, text: String) = viewModelScope.launch { container.progress.saveNote(key, text) }

    /** Clears learner data, then re-applies the chosen starting point so the path starts where they placed. */
    fun resetProgress() = viewModelScope.launch {
        container.progress.resetAll()
        _todayMinutes.value = null
        val prefs = container.prefs.current()
        container.progress.applyPlacement(container.content.library().curriculum, prefs.startingPoint, today())
    }

    fun rescheduleReminders() = viewModelScope.launch { container.reminders.reschedule(container.prefs.current()) }
}
