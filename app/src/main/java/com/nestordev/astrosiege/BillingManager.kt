package com.nestordev.astrosiege

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * One-time, non-consumable "remove ads" purchase via Google Play Billing.
 *
 * SETUP NEEDED IN PLAY CONSOLE before this works for real:
 *   Play Console > [Astro Siege] > Monetize > Products > In-app products
 *   → create a product with the exact id "remove_ads" (see PRODUCT_ID below).
 * Until that product exists, queryProductDetails() simply returns nothing and the
 * "remove ads" button in the hangar can be hidden (GameBridge.isAdsRemoved() stays false
 * and purchaseRemoveAds() will just fail silently with a toast) — the app still works fine
 * without it, ads just can't be turned off yet.
 */
class BillingManager(private val context: Context) {

    companion object {
        const val PRODUCT_ID = "remove_ads"
        private const val TAG = "BillingManager"
        private const val PREFS = "astro_siege_billing"
        private const val KEY_ADS_REMOVED = "ads_removed"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main)

    var onAdsRemovedChanged: ((Boolean) -> Unit)? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            com.android.billingclient.api.PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    private var connected = false

    fun isAdsRemoved(): Boolean = prefs.getBoolean(KEY_ADS_REMOVED, false)

    fun start() {
        if (billingClient.isReady) return
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connected = result.responseCode == BillingClient.BillingResponseCode.OK
                if (connected) restorePurchases()
            }

            override fun onBillingServiceDisconnected() {
                connected = false
                // The next purchase/restore call will retry startConnection().
            }
        })
    }

    /** Re-queries owned in-app products and re-applies "remove ads" if it's there. */
    fun restorePurchases() {
        ensureConnected {
            scope.launch {
                val result = billingClient.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
                result.purchasesList.forEach { handlePurchase(it) }
            }
        }
    }

    fun launchPurchaseRemoveAds(activity: android.app.Activity, onResult: (success: Boolean) -> Unit) {
        ensureConnected {
            scope.launch {
                val productDetailsResult = billingClient.queryProductDetails(
                    QueryProductDetailsParams.newBuilder()
                        .setProductList(
                            listOf(
                                QueryProductDetailsParams.Product.newBuilder()
                                    .setProductId(PRODUCT_ID)
                                    .setProductType(BillingClient.ProductType.INAPP)
                                    .build()
                            )
                        )
                        .build()
                )
                val details = productDetailsResult.productDetailsList?.firstOrNull()
                if (details == null) {
                    Log.w(TAG, "remove_ads product not found — create it in Play Console first.")
                    onResult(false)
                    return@launch
                }
                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(details)
                                .build()
                        )
                    )
                    .build()
                val launchResult = billingClient.launchBillingFlow(activity, flowParams)
                onResult(launchResult.responseCode == BillingClient.BillingResponseCode.OK)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.products.contains(PRODUCT_ID) &&
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        ) {
            prefs.edit().putBoolean(KEY_ADS_REMOVED, true).apply()
            onAdsRemovedChanged?.invoke(true)
            if (!purchase.isAcknowledged) {
                scope.launch {
                    billingClient.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                    )
                }
            }
        }
    }

    private fun ensureConnected(block: () -> Unit) {
        if (billingClient.isReady) {
            block()
        } else {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connected = result.responseCode == BillingClient.BillingResponseCode.OK
                    if (connected) block()
                }

                override fun onBillingServiceDisconnected() {
                    connected = false
                }
            })
        }
    }

    fun endConnection() {
        if (billingClient.isReady) billingClient.endConnection()
    }
}
