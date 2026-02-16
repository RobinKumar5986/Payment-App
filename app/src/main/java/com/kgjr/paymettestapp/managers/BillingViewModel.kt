package com.kgjr.paymettestapp.managers

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BillingViewModel(context: Context) : ViewModel(), PurchasesUpdatedListener {

    private val _productDetailsList = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetailsList = _productDetailsList.asStateFlow()

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
                    queryAllProducts()
                }
            }
            override fun onBillingServiceDisconnected() {
                startConnection() // Simple retry logic
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
            "com.kgjr.paymettestapp.product1" to BillingClient.ProductType.INAPP,
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
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { /* Handle success */ }
        }
    }
}