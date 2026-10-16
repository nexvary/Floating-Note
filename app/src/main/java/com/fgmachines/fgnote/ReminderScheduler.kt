package com.fgmachines.fgnote

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Inexact reminders work without special exact-alarm privileges. */
object ReminderScheduler {
    const val CHANNEL = "note_reminders"
    const val ACTION_REMIND = "com.fgmachines.fgnote.REMIND"
    const val EXTRA_NOTE_ID = "reminder_note_id"

    private fun alarmIntent(context: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, id.toInt(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ACTION_REMIND)
                .putExtra(EXTRA_NOTE_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun schedule(context: Context, note: Note) {
        cancel(context, note.id)
        val time = note.reminderAt ?: return
        if (time <= System.currentTimeMillis()) return
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, alarmIntent(context, note.id))
    }

    fun cancel(context: Context, id: Long) {
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
            .cancel(alarmIntent(context, id))
    }

    fun reschedule(context: Context) {
        val store = NoteStore(context)
        try {
            store.all().forEach { schedule(context, it) }
        } finally {
            store.close()
        }
    }

    fun channel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL, context.getString(R.string.reminders),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE }
            )
        }
    }
}

/** Non-exported alarm receiver. Can display a reminder when overlay is stopped. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, -1L)
        val store = NoteStore(context)
        val note = try { store.get(id) } finally { store.close() } ?: return
        val time = note.reminderAt ?: return
        if (time > System.currentTimeMillis() + 60_000) return

        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        ReminderScheduler.channel(context)
        val pending = PendingIntent.getActivity(
            context, id.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_NOTE, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(context, ReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(note.title.ifBlank { context.getString(R.string.untitled) })
            .setContentText(NoteText.preview(note.body))
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(pending)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(id.toInt(), notification)
    }
}

/** Re-arms only user-created future reminders, after reboot or app update. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) ReminderScheduler.reschedule(context)
    }
}
