package com.mutazyounes.prayerathan.audio

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build

/** Android 7 has no startForegroundService; a plain start is allowed there. */
internal fun Context.startForegroundServiceCompat(intent: Intent) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(intent)
    } else {
        startService(intent)
    }
}

/** Channels arrived in Android 8. Before that, priority lives on the builder. */
internal fun notificationBuilder(
    context: Context,
    channelId: String,
    highPriority: Boolean,
): Notification.Builder {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        return Notification.Builder(context, channelId)
    }
    @Suppress("DEPRECATION")
    return Notification.Builder(context)
        .setPriority(if (highPriority) Notification.PRIORITY_HIGH else Notification.PRIORITY_LOW)
}
