@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.datastore.preferences.core

import androidx.datastore.core.DataStore

abstract class Preferences {
    class Key<T>(val name: String)
    abstract operator fun <T> get(key: Key<T>): T?
}

class MutablePreferences : Preferences() {
    override fun <T> get(key: Key<T>): T? = null
    operator fun <T> set(key: Key<T>, value: T) {}
    fun clear() {}
}

fun booleanPreferencesKey(name: String) = Preferences.Key<Boolean>(name)
fun stringPreferencesKey(name: String) = Preferences.Key<String>(name)
fun intPreferencesKey(name: String) = Preferences.Key<Int>(name)
fun longPreferencesKey(name: String) = Preferences.Key<Long>(name)
fun floatPreferencesKey(name: String) = Preferences.Key<Float>(name)
fun stringSetPreferencesKey(name: String) = Preferences.Key<Set<String>>(name)
fun emptyPreferences(): Preferences = MutablePreferences()

suspend fun DataStore<Preferences>.edit(transform: suspend (MutablePreferences) -> Unit): Preferences = TODO()
