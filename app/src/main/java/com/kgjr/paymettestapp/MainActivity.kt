package com.kgjr.paymettestapp

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.ProductDetails
import com.kgjr.paymettestapp.managers.BillingViewModel
import java.text.DateFormat
import java.util.Date

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
    val ownedIds by viewModel.ownedProductIds.collectAsState()
    val history by viewModel.purchaseHistory.collectAsState()

    val context = LocalContext.current
    val activity = context as ComponentActivity

    LaunchedEffect(Unit) {
        viewModel.queryPurchases()
        viewModel.fetchPurchaseHistory()
    }

    // Use LazyColumn as the root scrollable container
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp) // Extra space at the bottom
    ) {
        // 1. Header Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Premium Store",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 2. Loading State
        if (products.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxHeight(0.7f) // Occupies 70% of the viewport height
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Connecting to Play Store...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            // 3. Product List
            items(products) { product ->
                ProductCard(
                    product = product,
                    isOwned = ownedIds.contains(product.productId),
                    historyRecord = history.find { it.products.contains(product.productId) },
                    onBuyClick = { viewModel.buyProduct(activity, product) }
                )
            }
        }
    }
}

@Composable
fun ProductCard(
    product: ProductDetails,
    isOwned: Boolean,
    historyRecord: com.android.billingclient.api.PurchaseHistoryRecord?,
    onBuyClick: () -> Unit
) {
    // Check if this is your specific consumable item
    val isConsumable = product.productId == "consumable_product_test_1"

    // Logic: If it's consumable, you can always buy it.
    // If not, you can only buy if not owned.
    val canPurchase = isConsumable || !isOwned

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = product.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Button(
                    onClick = onBuyClick,
                    enabled = canPurchase,
                    colors = if (!canPurchase)
                        ButtonDefaults.buttonColors(containerColor = Color.LightGray)
                    else ButtonDefaults.buttonColors()
                ) {
                    if (!canPurchase) {
                        Text("Owned", color = Color.DarkGray)
                    } else {
                        val price = product.oneTimePurchaseOfferDetails?.formattedPrice
                            ?: product.subscriptionOfferDetails?.firstOrNull()
                                ?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
                            ?: "Buy"
                        Text(price)
                    }
                }
            }

            // Show History Section if a previous purchase exists
            historyRecord?.let { record ->
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        val dateLabel = DateFormat.getDateTimeInstance().format(Date(record.purchaseTime))

                        Text(
                            text = "PREVIOUS PURCHASE",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp
                        )

                        Text(
                            text = "Date: $dateLabel",
                            style = MaterialTheme.typography.bodySmall
                        )

                        // Using SelectionContainer so user can copy the token for support
                        SelectionContainer {
                            Text(
                                text = "Token: ${record.purchaseToken}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.DarkGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}