package de.transio.hiuni.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(NotificationScheduler.EXTRA_EVENT_ID, -1L)
        val title = intent.getStringExtra(NotificationScheduler.EXTRA_EVENT_TITLE)
        Timber.d("NotificationReceiver fired for event=$eventId title=$title (Phase 2 will post the notification)")
    }
}
