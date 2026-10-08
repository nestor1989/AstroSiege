package com.nestordev.astrosiege

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import com.nestordev.astrosiege.databinding.ActivityMainBinding
import com.nestordev.astrosiege.reminder.ReminderScheduler

/**
 * The entire native surface of the app: one Activity, one WebView loading the game's bundled
 * HTML, plus the bits Google Play's minimum-functionality policy expects beyond "a website in
 * an icon" — haptics, ads, a real purchase flow, and a local daily reminder. All actual game
 * logic/UI lives in assets/www/astro-siege.html; this class just wires the native pieces to it
 * via GameBridge.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var billing: BillingManager
    private lateinit var adManager: AdManager

    private val notificationPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) ReminderScheduler.scheduleNext(this)
            // If declined, we just never post the reminder — no further prompting.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        billing = BillingManager(this)
        adManager = AdManager(this, billing)

        setupWebView()
        billing.start()
        adManager.preloadInterstitial()
        adManager.preloadRewarded()
        requestNotificationPermissionIfNeeded()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Single-page game, nothing to navigate "back" out of — back just backgrounds
                // the app instead of killing the WebView/game state.
                moveTaskToBack(true)
            }
        })
    }

    private fun setupWebView() {
        val webView = binding.gameWebView
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
        }

        webView.addJavascriptInterface(
            GameBridge(this, webView, adManager, billing),
            "AndroidBridge"
        )

        billing.onAdsRemovedChanged = { removed ->
            runOnUiThread {
                if (removed) {
                    adManager.setBannerVisible(binding.bannerContainer, false)
                    Toast.makeText(this, R.string.toast_ads_removed, Toast.LENGTH_SHORT).show()
                    webView.evaluateJavascript(
                        "(function(){ if(typeof window.onAdsRemoved === 'function'){ window.onAdsRemoved(); } })();",
                        null
                    )
                }
            }
        }

        webView.loadUrl("https://appassets.androidplatform.net/assets/www/astro-siege.html")
    }

    fun setBannerVisible(visible: Boolean) {
        if (visible && binding.bannerContainer.childCount == 0) {
            adManager.attachBanner(binding.bannerContainer, this)
        }
        adManager.setBannerVisible(binding.bannerContainer, visible)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            ReminderScheduler.scheduleNext(this)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            ReminderScheduler.scheduleNext(this)
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.gameWebView.onResume()
        adManager.resumeBanner()
    }

    override fun onPause() {
        binding.gameWebView.onPause()
        adManager.pauseBanner()
        super.onPause()
    }

    override fun onDestroy() {
        adManager.destroyBanner()
        billing.endConnection()
        binding.gameWebView.destroy()
        super.onDestroy()
    }
}
