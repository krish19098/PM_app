@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.room

import android.content.Context
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS) annotation class Entity(val tableName: String = "")
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER) annotation class PrimaryKey(val autoGenerate: Boolean = false)
@Target(AnnotationTarget.CLASS) annotation class Dao
@Target(AnnotationTarget.FUNCTION) annotation class Query(val value: String)
@Target(AnnotationTarget.FUNCTION) annotation class Insert(val onConflict: Int = OnConflictStrategy.ABORT)
@Target(AnnotationTarget.FUNCTION) annotation class Upsert
@Target(AnnotationTarget.CLASS) annotation class Database(val entities: Array<KClass<*>>, val version: Int, val exportSchema: Boolean = true)

annotation class OnConflictStrategy {
    companion object {
        const val REPLACE = 1
        const val ABORT = 3
    }
}

abstract class RoomDatabase {
    abstract fun clearAllTables()
    class Builder<T : RoomDatabase> {
        fun build(): T = TODO()
    }
}

object Room {
    @JvmStatic fun <T : RoomDatabase> databaseBuilder(context: Context, klass: Class<T>, name: String?): RoomDatabase.Builder<T> = RoomDatabase.Builder()
}

suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R = block()
