package com.moneymanager.app.ui.categories

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SmokeFree
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Work
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.ui.theme.MMGreen

@Composable
fun CategoryVisual(
    category: CategoryEntity,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    val image = rememberImageBitmap(category.imageUri)
    Box(
        modifier = modifier
            .size(size)
            .shadow(6.dp, RoundedCornerShape(18.dp), clip = false, ambientColor = com.moneymanager.app.ui.theme.MMIconShadow, spotColor = com.moneymanager.app.ui.theme.MMIconShadow)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = .14f),
                        parseColor(category.colorHex).copy(alpha = .97f),
                        parseColor(category.colorHex).copy(alpha = .78f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = category.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            GlossHighlight()
        } else {
            Icon(
                imageVector = categoryIcon(category.name, category.iconKey),
                contentDescription = category.name,
                tint = Color.White,
                modifier = Modifier.size(size * .48f)
            )
            GlossHighlight()
        }
    }
}

@Composable
private fun BoxScope.GlossHighlight() {
    Box(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = 5.dp, y = 4.dp)
            .size(18.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = .30f))
    )
}

@Composable
private fun rememberImageBitmap(uriString: String?): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, uriString) {
        value = uriString?.let { uriStringValue ->
            runCatching {
                withContext(Dispatchers.IO) {
                    val uri = android.net.Uri.parse(uriStringValue)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri).use { input ->
                        BitmapFactory.decodeStream(input, null, bounds)
                    }
                    val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 512, 512)
                    val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
                    context.contentResolver.openInputStream(uri).use { input ->
                        BitmapFactory.decodeStream(input, null, options)?.asImageBitmap()
                    }
                }
            }.getOrNull()
        }
    }.value
}

private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
    if (width <= 0 || height <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= reqWidth && height / (sample * 2) >= reqHeight) {
        sample *= 2
    }
    return sample
}

fun categoryIcon(name: String, key: String = ""): androidx.compose.ui.graphics.vector.ImageVector {
    val n = "$name $key".lowercase()
    return when {
        "air ticket" in n || "flight" in n -> Icons.Filled.Flight
        "a/c to a/c" in n || "transfer" in n -> Icons.Filled.AccountBalance
        "beauty" in n || "fitness" in n -> Icons.Filled.Spa
        "bike" in n || "cycle" in n -> Icons.Filled.TwoWheeler
        "bill" in n || "utility" in n -> Icons.Filled.ReceiptLong
        "book" in n -> Icons.Filled.MenuBook
        "breakfast" in n -> Icons.Filled.Cake
        "bus" in n -> Icons.Filled.DirectionsBus
        "business" in n -> Icons.Filled.BusinessCenter
        "cable" in n -> Icons.Filled.Devices
        "car" in n -> Icons.Filled.DirectionsCar
        "cash" in n || "wallet" in n -> Icons.Filled.AccountBalanceWallet
        "credit card" in n || n.contains("cc") -> Icons.Filled.CreditCard
        "cigarette" in n -> Icons.Filled.SmokeFree
        "cloth" in n -> Icons.Filled.Work
        "coffee" in n -> Icons.Filled.LocalDrink
        "cookie" in n -> Icons.Filled.Cake
        "courier" in n -> Icons.Filled.LocalShipping
        "daily care" in n -> Icons.Filled.Favorite
        "dining" in n || "food" in n -> Icons.Filled.Restaurant
        "dinner" in n -> Icons.Filled.LocalDining
        "disco" in n || "nightlife" in n -> Icons.Filled.Nightlife
        "donation" in n -> Icons.Filled.VolunteerActivism
        "drink" in n -> Icons.Filled.LocalDrink
        "education" in n -> Icons.Filled.School
        "electric" in n -> Icons.Filled.Bolt
        "electronic" in n -> Icons.Filled.Devices
        "emi" in n -> Icons.Filled.Percent
        "entertain" in n -> Icons.Filled.Movie
        "finance" in n || "interest" in n || "salary" in n || "income" in n -> Icons.Filled.Paid
        "grocery" in n -> Icons.Filled.ShoppingCart
        "health" in n -> Icons.Filled.HealthAndSafety
        "insurance" in n -> Icons.Filled.Security
        "investment" in n || "mutual fund" in n || "stock" in n -> Icons.Filled.ShowChart
        "loan" in n -> Icons.Filled.AccountBalanceWallet
        "rent" in n || "mortgage" in n -> Icons.Filled.Home
        "shopping" in n -> Icons.Filled.ShoppingBag
        "travel" in n -> Icons.Filled.Luggage
        "music" in n -> Icons.Filled.MusicNote
        "self" in n -> Icons.Filled.SelfImprovement
        else -> Icons.Filled.Store
    }
}

private fun parseColor(value: String): Color = runCatching { Color(android.graphics.Color.parseColor(value)) }
    .getOrDefault(MMGreen.copy(alpha = .85f))
