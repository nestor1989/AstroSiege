package com.nestordev.astrosiege.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** AlarmManager alarms are wiped on reboot — put the daily reminder back. */
class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.ensureChannel(context)
            ReminderScheduler.scheduleNext(context)
        }
    }
}
