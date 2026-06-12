package com.chatmod.mobile.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

data class PlayBillingUiState(
    val isAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String? = "Play Billing not connected",
    val products: List<PlayBillingProduct> = emptyList(),
    val pendingPurchaseCount: Int = 0
)

data class PlayBillingProduct(
    val productId: String,
    val name: String,
    val title: String,
    val description: String,
    val price: String,
    val offerToken: String?
)

data class PlayBillingPurchase(
    val productId: String,
    val purchaseToken: String,
    val packageName: String,
    val alreadyAcknowledged: Boolean
)

class PlayBillingManager(context: Context) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val productDetailsById = linkedMapOf<String, ProductDetails>()
    private val unacknowledgedTokens = mutableSetOf<String>()
    private var connecting = false

    private val _state = MutableStateFlow(PlayBillingUiState())
    val state: StateFlow<PlayBillingUiState> = _state.asStateFlow()

    private val purchaseEvents = Channel<PlayBillingPurchase>(capacity = Channel.BUFFERED)
    val purchases: Flow<PlayBillingPurchase> = purchaseEvents.receiveAsFlow()

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        connectIfNeeded {
            queryProductsConnected()
            restorePurchasesConnected("Checking existing purchases")
        }
    }

    fun end() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
        connecting = false
        _state.update { current ->
            current.copy(isAvailable = false, isLoading = false, statusMessage = "Play Billing disconnected")
        }
    }

    fun refreshProducts() {
        connectIfNeeded {
            queryProductsConnected()
        }
    }

    fun restorePurchases() {
        connectIfNeeded {
            restorePurchasesConnected("Restoring purchases")
        }
    }

    fun launchPurchase(activity: Activity, productId: String) {
        connectIfNeeded {
            val productDetails = productDetailsById[productId]
            if (productDetails == null) {
                _state.update { current ->
                    current.copy(
                        isLoading = true,
                        statusMessage = "Refreshing Play products before purchase"
                    )
                }
                queryProductsConnected()
                return@connectIfNeeded
            }

            val offerToken = productDetails.subscriptionOfferDetails
                ?.firstOrNull()
                ?.offerToken

            val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
            if (!offerToken.isNullOrBlank()) {
                productParamsBuilder.setOfferToken(offerToken)
            }

            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParamsBuilder.build()))
                .build()

            val result = billingClient.launchBillingFlow(activity, params)
            _state.update { current ->
                current.copy(statusMessage = result.toLaunchStatus(productId))
            }
        }
    }

    fun acknowledgePurchase(purchaseToken: String) {
        if (purchaseToken !in unacknowledgedTokens) {
            return
        }

        connectIfNeeded {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build()

            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    unacknowledgedTokens.remove(purchaseToken)
                }
                _state.update { current ->
                    current.copy(
                        statusMessage = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            "Purchase acknowledged"
                        } else {
                            "Purchase validated, but acknowledgement failed: ${result.debugMessage}"
                        }
                    )
                }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty(), "Purchase received")
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _state.update { current -> current.copy(statusMessage = "Purchase cancelled") }
            }
            else -> {
                _state.update { current ->
                    current.copy(statusMessage = "Purchase failed: ${billingResult.debugMessage}")
                }
            }
        }
    }

    private fun connectIfNeeded(onReady: () -> Unit) {
        if (billingClient.isReady) {
            onReady()
            return
        }
        if (connecting) {
            _state.update { current ->
                current.copy(isLoading = true, statusMessage = "Connecting to Play Billing")
            }
            return
        }

        connecting = true
        _state.update { current ->
            current.copy(isLoading = true, statusMessage = "Connecting to Play Billing")
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                connecting = false
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.update { current ->
                        current.copy(
                            isAvailable = true,
                            isLoading = false,
                            statusMessage = "Play Billing connected"
                        )
                    }
                    onReady()
                } else {
                    _state.update { current ->
                        current.copy(
                            isAvailable = false,
                            isLoading = false,
                            statusMessage = "Play Billing unavailable: ${billingResult.debugMessage}"
                        )
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
                _state.update { current ->
                    current.copy(
                        isAvailable = false,
                        isLoading = false,
                        statusMessage = "Play Billing disconnected"
                    )
                }
            }
        })
    }

    private fun queryProductsConnected() {
        _state.update { current -> current.copy(isLoading = true, statusMessage = "Loading Play products") }
        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(ProProductId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(CreatorProductId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        statusMessage = "Could not load Play products: ${billingResult.debugMessage}"
                    )
                }
                return@queryProductDetailsAsync
            }

            productDetailsById.clear()
            queryResult.productDetailsList.forEach { details ->
                productDetailsById[details.productId] = details
            }

            val summaries = queryResult.productDetailsList
                .map { details -> details.toBillingProduct() }
                .sortedBy { product -> product.productId.productSortOrder() }
            val missingCount = queryResult.unfetchedProductList.size
            _state.update { current ->
                current.copy(
                    isAvailable = true,
                    isLoading = false,
                    products = summaries,
                    statusMessage = when {
                        summaries.isEmpty() -> "No Play products returned for this app build"
                        missingCount > 0 -> "Loaded ${summaries.size} product(s), $missingCount unavailable"
                        else -> "Loaded ${summaries.size} Play product(s)"
                    }
                )
            }
        }
    }

    private fun restorePurchasesConnected(statusMessage: String) {
        _state.update { current -> current.copy(isLoading = true, statusMessage = statusMessage) }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _state.update { current ->
                    current.copy(
                        isLoading = false,
                        statusMessage = "Could not restore purchases: ${billingResult.debugMessage}"
                    )
                }
                return@queryPurchasesAsync
            }

            processPurchases(purchases, "Restore checked")
        }
    }

    private fun processPurchases(purchases: List<Purchase>, statusPrefix: String) {
        var pendingCount = 0
        var purchasedCount = 0
        purchases.forEach { purchase ->
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    purchasedCount += 1
                    if (!purchase.isAcknowledged) {
                        unacknowledgedTokens.add(purchase.purchaseToken)
                    }
                    purchase.products.forEach { productId ->
                        purchaseEvents.trySend(
                            PlayBillingPurchase(
                                productId = productId,
                                purchaseToken = purchase.purchaseToken,
                                packageName = appContext.packageName,
                                alreadyAcknowledged = purchase.isAcknowledged
                            )
                        )
                    }
                }
                Purchase.PurchaseState.PENDING -> pendingCount += 1
                else -> Unit
            }
        }

        _state.update { current ->
            current.copy(
                isLoading = false,
                pendingPurchaseCount = pendingCount,
                statusMessage = when {
                    purchasedCount > 0 -> "$statusPrefix: validating $purchasedCount purchase(s)"
                    pendingCount > 0 -> "$pendingCount purchase(s) pending in Google Play"
                    purchases.isEmpty() -> "No active Play purchases found"
                    else -> "$statusPrefix: no completed purchases"
                }
            )
        }
    }

    private fun ProductDetails.toBillingProduct(): PlayBillingProduct {
        val offer = subscriptionOfferDetails?.firstOrNull()
        val paidPhase = offer?.pricingPhases?.pricingPhaseList
            ?.firstOrNull { phase -> phase.priceAmountMicros > 0L }
            ?: offer?.pricingPhases?.pricingPhaseList?.firstOrNull()
        return PlayBillingProduct(
            productId = productId,
            name = productId.productName(),
            title = title,
            description = description,
            price = paidPhase?.formattedPrice ?: "Price unavailable",
            offerToken = offer?.offerToken
        )
    }

    private fun BillingResult.toLaunchStatus(productId: String): String {
        return if (responseCode == BillingClient.BillingResponseCode.OK) {
            "Opening ${productId.productName()} purchase"
        } else {
            "Could not open purchase: $debugMessage"
        }
    }

    private fun String.productName(): String {
        return when (this) {
            ProProductId -> "Pro"
            CreatorProductId -> "Creator"
            else -> this
        }
    }

    private fun String.productSortOrder(): Int {
        return when (this) {
            ProProductId -> 0
            CreatorProductId -> 1
            else -> 2
        }
    }

    companion object {
        const val ProProductId = "chatmod_pro_monthly"
        const val CreatorProductId = "chatmod_creator_monthly"
    }
}
