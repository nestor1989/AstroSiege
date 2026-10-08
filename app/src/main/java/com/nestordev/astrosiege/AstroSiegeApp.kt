package com.nestordev.astrosiege

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.nestordev.astrosiege.reminder.ReminderScheduler

/**
 * Application entry point. Two jobs only:
 *  - kick off Mobile Ads SDK initialization as early as possible (it's async and can take
 *    a moment on a cold start, so starting it here instead of in onCreate() of the Activity
 *    shaves a little latency off the first ad request);
 *  - make sure the notification channel used by the daily reminder exists (channel creation
 *    is idempotent and cheap, safe to call on every process start).
 */
class AstroSiegeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)
        ReminderScheduler.ensureChannel(this)
    }
}
