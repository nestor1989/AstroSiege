package com.nestordev.astrosiege.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nestordev.astrosiege.R
import java.util.Calendar

/**
 * Schedules the single daily "come back and play" local notification the launch pipeline
 * asks for as a lightweight retention hook. No backend, no push service — just an inexact
 * daily AlarmManager alarm that re-schedules itself each time it fires (and again on boot,
 * since exact/inexact alarms are cleared on reboot).
 *
 * This is opt-out at the OS level only: if the player denies the POST_NOTIFICATIONS runtime
 * permission (Android 13+) or turns the notification off from system settings, this silently
 * never posts anything — there's no separate in-game toggle to keep the wrapper minimal.
 */
object ReminderScheduler {
    private const val CHANNEL_ID = "daily_reminder"
    private const val REQUEST_CODE = 4201
    private const val REMINDER_HOUR_OF_DAY = 18 // 6pm local time — evening, not early morning

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    fun channelId(): String = CHANNEL_ID

    /** Call after the notification permission is granted (or already was) to start the cycle. */
    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = nextTriggerTimeMillis()
        val pendingIntent = reminderPendingIntent(context)
        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (_: SecurityException) {
            // Some OEM/managed-device policies restrict alarms; the reminder is a nice-to-have,
            // failing here should never crash the app.
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(reminderPendingIntent(context))
    }

    private fun reminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyReminderReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    private fun nextTriggerTimeMillis(): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, REMINDER_HOUR_OF_DAY)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1)
        return next.timeInMillis
    }
}
