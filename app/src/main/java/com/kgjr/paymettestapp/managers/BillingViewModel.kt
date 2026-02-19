package com.kgjr.paymettestapp.managers

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BillingViewModel(context: Context) : ViewModel(), PurchasesUpdatedListener {

    val userAuthId = "some_id_of_user"
    private val _productDetailsList = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetailsList = _productDetailsList.asStateFlow()


    private val _ownedProductIds = MutableStateFlow<Set<String>>(emptySet())
    val ownedProductIds = _ownedProductIds.asStateFlow()

    private val _purchaseHistory = MutableStateFlow<List<PurchaseHistoryRecord>>(emptyList())
    val purchaseHistory = _purchaseHistory.asStateFlow()

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    init {
        startConnection()
    }

    /**
     * @author Robin Kumar
     * @Info: This function si been created to a hand shak connection
     * b.w the Mobile Application and the google play store.
     *
     * */
    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d("Billing", "Connection Established!")


                    queryAllProducts()
                    fetchPurchaseHistory()
                } else {
                    Log.e("Billing", "Setup Failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w("Billing", "Service Disconnected. Retrying...")
                startConnection()
            }
        })
    }

    /**
     * @author Robin Kumar
     * @Info: This function is responsible for getting data form the google
     * play console for the specific products in the list the id's are use
     * to identify the exact product we want to know about.
     * */
    fun queryAllProducts() {
        val productIds = listOf(
            "consumable_product_test_1" to BillingClient.ProductType.INAPP,
            "com.kgjr.paymettestapp.product1" to BillingClient.ProductType.INAPP,
            "one_time_product_test_2" to BillingClient.ProductType.INAPP,
            "com.kgjr.paymettestapp.sub_product1" to BillingClient.ProductType.SUBS
        )

        val allFetchedDetails = mutableListOf<ProductDetails>()

        productIds.forEach { (id, type) ->
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(id)
                        .setProductType(type)
                        .build()
                )).build()
            billingClient.queryProductDetailsAsync(params) { result, list ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    allFetchedDetails.addAll(list)
                    _productDetailsList.value = allFetchedDetails.toList()
                }
            }
        }
    }

    /**
     * @author Robin Kumar
     * @Info: This function is used for starting the buying process
     * */
    fun buyProduct(activity: Activity, productDetails: ProductDetails) {
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            // For subscriptions, you'd normally select an offer token
            .apply {
                productDetails.subscriptionOfferDetails?.firstOrNull()?.let {
                    setOfferToken(it.offerToken)
                }
            }
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { confirmPurchase(it) }
        }
    }

    private fun confirmPurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {

            // Check if the product is your consumable one
            val isConsumable = purchase.products.contains("consumable_product_test_1")

            if (isConsumable) {
                // Buy again and again logic
                consumePurchase(purchase)
            } else if (!purchase.isAcknowledged) {
                // One-time "permanent" purchase logic
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(params) { /* Handle success */ }
            }
        }
    }

    /**
     * @author Robin Kumar
     * @Info: Fetches currently active purchases (what the user owns right now)
     */
    fun queryPurchases() {
        if (!billingClient.isReady) return
        val types = listOf(BillingClient.ProductType.INAPP, BillingClient.ProductType.SUBS)

        types.forEach { type ->
            val params = QueryPurchasesParams.newBuilder().setProductType(type).build()
            billingClient.queryPurchasesAsync(params) { result, purchasesList ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val currentOwned = _ownedProductIds.value.toMutableSet()
                    purchasesList.forEach { currentOwned.addAll(it.products) }
                    _ownedProductIds.value = currentOwned
                }
            }
        }
    }

    private fun consumePurchase(purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(consumeParams) { result, outToken ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                // SUCCESS: The item is now "gone" from Google Play
                // This is where you actually give the user their coins/gems
                println("Purchase consumed successfully. Granting reward now!")
            }
        }
    }

    /**
     * @author Robin Kumar
     * @Info: Fetches the last record of purchase (even if expired/consumed)
     */
    fun fetchPurchaseHistory() {
        if (!billingClient.isReady) {
            Log.e("Billing", "BillingClient is not ready")
            return
        }

        val types = listOf(BillingClient.ProductType.INAPP, BillingClient.ProductType.SUBS)

        types.forEach { type ->
            val params = QueryPurchaseHistoryParams.newBuilder().setProductType(type).build()

            billingClient.queryPurchaseHistoryAsync(params) { result, records ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (records.isNullOrEmpty()) {
                        Log.d("Billing", "No history found for type: $type")
                    } else {
                        records.forEach { record ->
                            Log.d("Billing", """
                            History Found ($type):
                            Product IDs: ${record.products}
                            Purchase Time: ${record.purchaseTime}
                            Purchase Token: ${record.purchaseToken}
                        """.trimIndent())
                        }

                        val currentHistory = _purchaseHistory.value.toMutableList()
                        currentHistory.addAll(records)
                        _purchaseHistory.value = currentHistory.distinctBy { it.purchaseToken }
                    }
                } else {
                    Log.e("Billing", "Error fetching history: ${result.debugMessage} (Code: ${result.responseCode})")
                }
            }
        }
    }
}