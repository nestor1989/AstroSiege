package com.nestordev.astrosiege

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast

/**
 * The JS <-> native bridge, exposed in the WebView as `window.AndroidBridge` (see
 * MainActivity.setupWebView). Every method here can be called directly from the game's
 * JavaScript; see astro-siege.html's `Native` helper object for the JS-side half of each call.
 *
 * WebView invokes @JavascriptInterface methods on a background thread, never the UI thread —
 * every method that touches a View, shows a Toast, or calls back into the WebView wraps its
 * work in `runOnUi { ... }`.
 */
class GameBridge(
    private val activity: MainActivity,
    private val webView: WebView,
    private val adManager: AdManager,
    private val billing: BillingManager
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private fun runOnUi(block: () -> Unit) = mainHandler.post(block)

    private fun callJs(fn: String) = runOnUi {
        webView.evaluateJavascript("(function(){ if(typeof $fn === 'function'){ $fn(); } })();", null)
    }

    private fun callJsWithArg(fn: String, jsArg: String) = runOnUi {
        webView.evaluateJavascript("(function(){ if(typeof $fn === 'function'){ $fn($jsArg); } })();", null)
    }

    // ---------------- screen / banner ----------------

    @JavascriptInterface
    fun onModeChange(mode: String) = runOnUi {
        // Banner only makes sense on menu-ish screens — never mid-play, never on the
        // attract screen's single big call-to-action, per the pipeline's ad defaults.
        val showBanner = mode == "hangar" || mode == "gameover"
        activity.setBannerVisible(showBanner)
    }

    // ---------------- interstitial ----------------

    @JavascriptInterface
    fun onGameOver() = runOnUi {
        adManager.registerGameOver()
    }

    @JavascriptInterface
    fun requestInterstitialIfDue() = runOnUi {
        adManager.showInterstitialIfDue(activity)
    }

    // ---------------- rewarded: free continue ----------------

    @JavascriptInterface
    fun offerContinue() = runOnUi {
        adManager.showRewarded(
            activity,
            onGranted = { callJs("window.onContinueGranted") },
            onUnavailable = {
                Toast.makeText(activity, R.string.toast_no_reward_available, Toast.LENGTH_SHORT).show()
                callJs("window.onContinueDeclined")
            }
        )
    }

    // ---------------- rewarded: bonus coins ----------------

    @JavascriptInterface
    fun watchAdForCoins() = runOnUi {
        adManager.showRewarded(
            activity,
            onGranted = { callJsWithArg("window.onCoinsRewarded", "50") },
            onUnavailable = {
                Toast.makeText(activity, R.string.toast_no_reward_available, Toast.LENGTH_SHORT).show()
            }
        )
    }

    // ---------------- remove-ads purchase ----------------

    @JavascriptInterface
    fun isAdsRemoved(): Boolean = billing.isAdsRemoved()

    @JavascriptInterface
    fun purchaseRemoveAds() = runOnUi {
        billing.launchPurchaseRemoveAds(activity) { started ->
            if (!started) {
                Toast.makeText(activity, R.string.toast_purchase_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JavascriptInterface
    fun restorePurchases() = runOnUi {
        billing.restorePurchases()
    }

    // ---------------- haptics ----------------

    @JavascriptInterface
    fun haptic(kind: String) = runOnUi {
        val ms: Long = when (kind) {
            "hit" -> 40
            "explode" -> 25
            "pickup" -> 15
            "boss" -> 60
            else -> 20
        }
        vibrate(ms)
    }

    private fun vibrate(ms: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            activity.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(ms)
        }
    }
}
