package com.pmbible.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pmbible.core.model.Reminder
import com.pmbible.core.model.ReminderKind
import com.pmbible.core.model.UserPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** Persists [UserPrefs] in DataStore; unknown or corrupt values fall back to defaults. */
class PrefsRepository(private val context: Context) {

    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val name = stringPreferencesKey("name")
        val goal = stringPreferencesKey("goal")
        val startingPoint = stringPreferencesKey("starting_point")
        val dailyMinutes = intPreferencesKey("daily_minutes")
        val studyDays = stringSetPreferencesKey("study_days")
        val weeklyGoal = intPreferencesKey("weekly_goal")
        val preferredMinute = intPreferencesKey("preferred_minute")
        val remindersEnabled = booleanPreferencesKey("reminders_enabled")
        // Encoded as "id|minuteOfDay|KIND" entries.
        val reminders = stringSetPreferencesKey("reminders")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val weeklyRecap = booleanPreferencesKey("weekly_recap")
        val language = stringPreferencesKey("language")
        val pace = stringPreferencesKey("pace")
        val tone = stringPreferencesKey("tone")
        val creativeWarnings = booleanPreferencesKey("creative_warnings")
        val interests = stringSetPreferencesKey("interests")
        val themeMode = stringPreferencesKey("theme_mode")
        val textScale = floatPreferencesKey("text_scale")
        val startDay = longPreferencesKey("start_day")
    }

    val prefs: Flow<UserPrefs> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(::decode)

    suspend fun current(): UserPrefs = prefs.first()

    suspend fun update(transform: (UserPrefs) -> UserPrefs) {
        context.dataStore.edit { p -> encode(transform(decode(p)), p) }
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String?, default: T): T =
        value?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default

    private fun decode(p: Preferences): UserPrefs {
        val d = UserPrefs()
        return UserPrefs(
            onboarded = p[K.onboarded] ?: d.onboarded,
            name = p[K.name] ?: d.name,
            goal = p[K.goal] ?: d.goal,
            startingPoint = enumOr(p[K.startingPoint], d.startingPoint),
            dailyMinutes = p[K.dailyMinutes] ?: d.dailyMinutes,
            studyDays = p[K.studyDays]?.mapNotNull { s -> DayOfWeek.entries.firstOrNull { it.name == s } }?.toSet() ?: d.studyDays,
            weeklyGoal = p[K.weeklyGoal] ?: d.weeklyGoal,
            preferredMinuteOfDay = p[K.preferredMinute] ?: d.preferredMinuteOfDay,
            remindersEnabled = p[K.remindersEnabled] ?: d.remindersEnabled,
            reminders = p[K.reminders]?.mapNotNull(::decodeReminder)?.sortedBy { it.minuteOfDay } ?: d.reminders,
            quietStartMinute = p[K.quietStart] ?: d.quietStartMinute,
            quietEndMinute = p[K.quietEnd] ?: d.quietEndMinute,
            weeklyRecap = p[K.weeklyRecap] ?: d.weeklyRecap,
            language = enumOr(p[K.language], d.language),
            pace = enumOr(p[K.pace], d.pace),
            tone = enumOr(p[K.tone], d.tone),
            creativeWarnings = p[K.creativeWarnings] ?: d.creativeWarnings,
            interests = p[K.interests] ?: d.interests,
            themeMode = enumOr(p[K.themeMode], d.themeMode),
            textScale = p[K.textScale] ?: d.textScale,
            startDay = p[K.startDay] ?: d.startDay,
        )
    }

    private fun decodeReminder(s: String): Reminder? {
        val parts = s.split("|")
        if (parts.size != 3) return null
        val id = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull()?.takeIf { it in 0 until 24 * 60 } ?: return null
        val kind = ReminderKind.entries.firstOrNull { it.name == parts[2] } ?: return null
        return Reminder(id, minute, kind)
    }

    private fun encode(u: UserPrefs, p: MutablePreferences) {
        p[K.onboarded] = u.onboarded
        p[K.name] = u.name
        p[K.goal] = u.goal
        p[K.startingPoint] = u.startingPoint.name
        p[K.dailyMinutes] = u.dailyMinutes
        p[K.studyDays] = u.studyDays.map { it.name }.toSet()
        p[K.weeklyGoal] = u.weeklyGoal
        p[K.preferredMinute] = u.preferredMinuteOfDay
        p[K.remindersEnabled] = u.remindersEnabled
        p[K.reminders] = u.reminders.map { "${it.id}|${it.minuteOfDay}|${it.kind.name}" }.toSet()
        p[K.quietStart] = u.quietStartMinute
        p[K.quietEnd] = u.quietEndMinute
        p[K.weeklyRecap] = u.weeklyRecap
        p[K.language] = u.language.name
        p[K.pace] = u.pace.name
        p[K.tone] = u.tone.name
        p[K.creativeWarnings] = u.creativeWarnings
        p[K.interests] = u.interests
        p[K.themeMode] = u.themeMode.name
        p[K.textScale] = u.textScale
        p[K.startDay] = u.startDay
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
