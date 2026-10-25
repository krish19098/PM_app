@file:Suppress("unused", "UNUSED_PARAMETER")

package androidx.core.app

import android.app.Notification
import android.app.PendingIntent
import android.content.Context

class NotificationCompat {
    abstract class Style
    class BigTextStyle : Style() {
        fun bigText(cs: CharSequence?): BigTextStyle = this
    }
    class Builder(context: Context, channelId: String) {
        fun setSmallIcon(icon: Int): Builder = this
        fun setContentTitle(title: CharSequence?): Builder = this
        fun setContentText(text: CharSequence?): Builder = this
        fun setStyle(style: Style?): Builder = this
        fun setContentIntent(intent: PendingIntent?): Builder = this
        fun setAutoCancel(autoCancel: Boolean): Builder = this
        fun setOnlyAlertOnce(onlyAlertOnce: Boolean): Builder = this
        fun build(): Notification = TODO()
    }
}

class NotificationManagerCompat private constructor() {
    fun notify(id: Int, notification: Notification) {}
    fun cancel(id: Int) {}
    fun areNotificationsEnabled(): Boolean = true
    companion object {
        @JvmStatic fun from(context: Context): NotificationManagerCompat = NotificationManagerCompat()
    }
}
