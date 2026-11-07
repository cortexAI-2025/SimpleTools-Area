package com.simpletools.area.core.billing

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.util.Base64
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.simpletools.area.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

internal const val AREA_PRO = "simpletools_area_pro_lifetime"
private const val OFFLINE_MS = 30L * 24 * 60 * 60 * 1000

internal data class BillingState(
    val active: Boolean = false,
    val pending: Boolean = false,
    val price: String? = null,
    val status: Status = Status.IDLE,
) {
    enum class Status { IDLE, CHECKING, UNAVAILABLE, NO_PURCHASE, CANCELLED }
}

internal fun verifyReceipt(data: String, signature: String, publicKey: String): Boolean = runCatching {
    val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)))
    Signature.getInstance("SHA1withRSA").run {
        initVerify(key)
        update(data.toByteArray(Charsets.UTF_8))
        verify(Base64.getDecoder().decode(signature))
    }
}.getOrDefault(false)

internal fun offlineReceiptIsFresh(confirmed: Long, now: Long) =
    confirmed > 0 && now >= confirmed && now - confirmed < OFFLINE_MS

/** Application-scoped Play client. The cache holds a signed receipt, never a Boolean entitlement. */
internal class AreaBilling(context: Context) {
    private val app = context.applicationContext
    private val cache = app.getSharedPreferences("area_play_receipt", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(BillingState(active = cachedPurchaseIsValid()))
    val state = _state.asStateFlow()
    private var details: ProductDetails? = null
    private var connecting = false
    private var generation = 0
    private val client = BillingClient.newBuilder(app)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .setListener { result, purchases ->
            scope.launch {
                when (result.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        if (purchases.orEmpty().any { AREA_PRO in it.products && it.purchaseState == Purchase.PurchaseState.PENDING }) {
                            _state.update { it.copy(pending = true) }
                        }
                        refresh()
                    }
                    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
                    BillingClient.BillingResponseCode.USER_CANCELED -> status(BillingState.Status.CANCELLED)
                    else -> status(BillingState.Status.UNAVAILABLE)
                }
            }
        }.build()

    init {
        scope.launch {
            while (true) {
                delay(60_000)
                _state.update { it.copy(active = cachedPurchaseIsValid()) }
            }
        }
    }

    private fun valid(p: Purchase) = AREA_PRO in p.products && p.packageName == app.packageName &&
        p.purchaseState == Purchase.PurchaseState.PURCHASED && p.purchaseToken.isNotBlank() &&
        verifyReceipt(p.originalJson, p.signature, BuildConfig.PLAY_PUBLIC_KEY)

    private fun cachedPurchaseIsValid(): Boolean {
        if (!offlineReceiptIsFresh(cache.getLong("confirmed", 0), System.currentTimeMillis())) return false
        return runCatching {
            valid(Purchase(cache.getString("data", "").orEmpty(), cache.getString("signature", "").orEmpty()))
        }.getOrDefault(false)
    }

    private fun online(): Boolean {
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return false
        return manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }

    private fun status(status: BillingState.Status) {
        _state.update { it.copy(active = cachedPurchaseIsValid(), status = status) }
    }

    fun hasPro() = cachedPurchaseIsValid()

    fun refresh() {
        if (BuildConfig.PLAY_PUBLIC_KEY.isBlank() || !online()) {
            status(BillingState.Status.UNAVAILABLE)
            return
        }
        status(BillingState.Status.CHECKING)
        if (client.isReady) {
            query()
        } else if (!connecting) {
            connecting = true
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingServiceDisconnected() {
                    scope.launch {
                        connecting = false
                        status(BillingState.Status.UNAVAILABLE)
                    }
                }

                override fun onBillingSetupFinished(result: BillingResult) {
                    scope.launch {
                        connecting = false
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) query()
                        else status(BillingState.Status.UNAVAILABLE)
                    }
                }
            })
        }
    }

    private fun query() {
        val request = ++generation
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(AREA_PRO).setProductType(BillingClient.ProductType.INAPP).build()
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()) { result, items ->
            scope.launch {
                if (request != generation) return@launch
                details = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    items.productDetailsList.firstOrNull { it.productId == AREA_PRO }
                } else null
                _state.update { it.copy(price = offer()?.formattedPrice) }
            }
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, items ->
            scope.launch {
                if (request != generation) return@launch
                if (result.responseCode != BillingClient.BillingResponseCode.OK || !online()) {
                    status(BillingState.Status.UNAVAILABLE)
                    return@launch
                }
                val purchases = items.filter { AREA_PRO in it.products }
                _state.update { it.copy(pending = purchases.any { p -> p.purchaseState == Purchase.PurchaseState.PENDING }) }
                val purchase = purchases.firstOrNull(::valid)
                if (purchase == null) {
                    cache.edit().clear().apply()
                    status(if (purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
                        BillingState.Status.UNAVAILABLE
                    } else BillingState.Status.NO_PURCHASE)
                } else if (purchase.isAcknowledged) {
                    remember(purchase)
                } else {
                    val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                    client.acknowledgePurchase(params) { confirmation ->
                        scope.launch {
                            if (request != generation) return@launch
                            if (confirmation.responseCode == BillingClient.BillingResponseCode.OK) remember(purchase)
                            else status(BillingState.Status.UNAVAILABLE)
                        }
                    }
                }
            }
        }
    }

    private fun remember(purchase: Purchase) {
        cache.edit().putString("data", purchase.originalJson).putString("signature", purchase.signature)
            .putLong("confirmed", System.currentTimeMillis()).apply()
        status(BillingState.Status.IDLE)
    }

    private fun offer() = details?.oneTimePurchaseOfferDetails

    fun buy(activity: Activity) {
        val product = details
        val offer = offer()
        if (!client.isReady || !online() || product == null || offer == null || hasPro() || state.value.pending) {
            refresh()
            return
        }
        val selection = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .apply { offer.offerToken?.takeIf { it.isNotBlank() }?.let { setOfferToken(it) } }
            .build()
        val result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(selection)).build())
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            else -> status(BillingState.Status.UNAVAILABLE)
        }
    }
}
