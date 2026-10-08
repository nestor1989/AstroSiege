package com.nestordev.astrosiege

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Wraps all three AdMob formats used by the game, following the launch pipeline's ad
 * monetization defaults: a banner only on menu-ish screens, a heavily-spaced interstitial
 * (never mid-play), and two opt-in rewarded placements (free continue, bonus coins).
 *
 * Real AdMob ad unit IDs for Astro Siege (astro-siege-android app in AdMob console,
 * ca-app-pub-4930505659937183~6794385855). These are live — do not point them at a
 * debug/dev-signed build you'll open repeatedly yourself; register as a test device
 * instead (see ADMOB_SETUP.md section 6) to avoid invalid-traffic flags.
 */
class AdManager(private val context: Context, private val billing: BillingManager) {

    companion object {
        private const val TAG = "AdManager"

        const val BANNER_UNIT_ID = "ca-app-pub-4930505659937183/2789535540"
        const val INTERSTITIAL_UNIT_ID = "ca-app-pub-4930505659937183/1506400158"
        const val REWARDED_UNIT_ID = "ca-app-pub-4930505659937183/8812763270"

        /** Show an interstitial roughly every N game-overs — "heavily spaced", per the
         *  pipeline's ad defaults, not on every single death. Raised from 3 to 5 at the
         *  user's request ("hay demasiada publicidad") to lower overall ad intensity. */
        private const val INTERSTITIAL_EVERY_N_GAME_OVERS = 5

        private const val PREFS = "astro_siege_ads"
        private const val KEY_GAME_OVER_COUNT = "game_over_count"
    }

    private var bannerView: AdView? = null
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var interstitialLoading = false
    private var rewardedLoading = false

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------- banner ----------------

    fun attachBanner(container: FrameLayout, activity: Activity) {
        if (bannerView != null) return
        val view = AdView(activity).apply {
            adUnitId = BANNER_UNIT_ID
            setAdSize(AdSize.BANNER)
        }
        bannerView = view
        container.removeAllViews()
        container.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { gravity = android.view.Gravity.CENTER }
        )
        view.loadAd(AdRequest.Builder().build())
    }

    /** container is shown/hidden as a whole; the AdView inside keeps its own loaded state. */
    fun setBannerVisible(container: FrameLayout, visible: Boolean) {
        val shouldShow = visible && !billing.isAdsRemoved()
        container.visibility = if (shouldShow) ViewGroup.VISIBLE else ViewGroup.GONE
    }

    fun destroyBanner() {
        bannerView?.destroy()
        bannerView = null
    }

    fun pauseBanner() { bannerView?.pause() }
    fun resumeBanner() { bannerView?.resume() }

    // ---------------- interstitial ----------------

    fun preloadInterstitial() {
        if (billing.isAdsRemoved() || interstitialAd != null || interstitialLoading) return
        interstitialLoading = true
        InterstitialAd.load(
            context, INTERSTITIAL_UNIT_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialLoading = false
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialLoading = false
                    interstitialAd = null
                    Log.d(TAG, "interstitial failed to load: ${error.message}")
                }
            }
        )
    }

    /** Call once per game-over event (win or lose a run) to advance the spacing counter. */
    fun registerGameOver() {
        val next = prefs.getInt(KEY_GAME_OVER_COUNT, 0) + 1
        prefs.edit().putInt(KEY_GAME_OVER_COUNT, next).apply()
    }

    /**
     * Call when the player leaves the Game Over screen (retry / change ship). Shows the
     * preloaded interstitial only if it's "due" per the spacing counter, and only if ads
     * haven't been removed. Fire-and-forget: the ad (if any) just overlays the activity and
     * the WebView underneath is untouched, so the caller doesn't need to await anything.
     */
    fun showInterstitialIfDue(activity: Activity) {
        if (billing.isAdsRemoved()) return
        val count = prefs.getInt(KEY_GAME_OVER_COUNT, 0)
        val due = count > 0 && count % INTERSTITIAL_EVERY_N_GAME_OVERS == 0
        val ad = interstitialAd
        if (!due || ad == null) {
            preloadInterstitial()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                preloadInterstitial()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                preloadInterstitial()
            }
        }
        ad.show(activity)
    }

    // ---------------- rewarded ----------------

    fun preloadRewarded() {
        if (rewardedAd != null || rewardedLoading) return
        rewardedLoading = true
        RewardedAd.load(
            context, REWARDED_UNIT_ID, AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedLoading = false
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedLoading = false
                    rewardedAd = null
                    Log.d(TAG, "rewarded failed to load: ${error.message}")
                }
            }
        )
    }

    /**
     * Shows a rewarded video if one is ready. [onGranted] fires only if the player watched it
     * to completion (real AdMob reward callback); [onUnavailable] fires immediately if no ad
     * is ready yet (nothing was shown — the caller should fall back gracefully, e.g. treat it
     * like the player declined).
     */
    fun showRewarded(activity: Activity, onGranted: () -> Unit, onUnavailable: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            onUnavailable()
            preloadRewarded()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                preloadRewarded()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                preloadRewarded()
                onUnavailable()
            }
        }
        ad.show(activity) { onGranted() }
    }
}
