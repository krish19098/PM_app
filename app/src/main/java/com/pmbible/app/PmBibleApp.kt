package com.pmbible.app

import android.app.Application
import android.content.Context
import com.pmbible.app.data.ContentRepository
import com.pmbible.app.data.PrefsRepository
import com.pmbible.app.data.ProgressRepository
import com.pmbible.app.data.db.AppDatabase
import com.pmbible.app.notifications.Notifications
import com.pmbible.app.notifications.ReminderScheduler

/** Manual dependency container: one instance of each repository for the whole process. */
class AppContainer(context: Context) {
    val content = ContentRepository(context)
    val prefs = PrefsRepository(context)
    val progress = ProgressRepository(AppDatabase.create(context))
    val reminders = ReminderScheduler(context)
}

class PmBibleApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannel(this)
    }
}
