package com.kgjr.paymettestapp

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kgjr.paymettestapp.managers.BillingViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize ViewModel (In production, use a Factory or Hilt)
        val viewModel = BillingViewModel(applicationContext)

        setContent {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                PaymentScreen(viewModel, Modifier.padding(innerPadding))
            }
        }
    }
}

@SuppressLint("ContextCastToActivity")
@Composable
fun PaymentScreen(viewModel: BillingViewModel, modifier: Modifier = Modifier) {
    val products by viewModel.productDetailsList.collectAsState()
    val activity = LocalContext.current as ComponentActivity

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Store", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(20.dp))

        if (products.isEmpty()) {
            CircularProgressIndicator()
            Text("Fetching products from Play Store...")
        } else {
            LazyColumn {
                items(products) { product ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, style = MaterialTheme.typography.titleMedium)
                                Text(product.description, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = { viewModel.buyProduct(activity, product) }) {
                                // Dynamic price from Play Console
                                Text(product.oneTimePurchaseOfferDetails?.formattedPrice
                                    ?: product.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                                    ?: "Buy")
                            }
                        }
                    }
                }
            }
        }
    }
}