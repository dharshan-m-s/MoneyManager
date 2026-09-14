package com.moneymanager.app.ui.categories

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneymanager.app.data.local.entity.CategoryEntity
import com.moneymanager.app.data.repository.CategoryImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun Color.iconLighten(amount: Float): Color = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha
)

/**
 * One UI-inspired category visual.
 *
 * The icon system intentionally avoids the previous glossy full-color tiles.
 * It uses a neutral tonal squircle with a restrained semantic icon color and
 * only a very small amount of depth/highlight so the category remains simple
 * and recognizable at small sizes.
 */
@Composable
fun CategoryVisual(
    category: CategoryEntity,
    imageStorage: CategoryImageStorage,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val bitmapState = produceState<ImageBitmap?>(initialValue = null, category.imageUri) {
        value = withContext(Dispatchers.IO) {
            imageStorage.getBitmap(category.imageUri)?.asImageBitmap()
        }
    }
    val bitmap = bitmapState.value
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val style = oneUiCategoryStyle(category.name, category.iconKey)

    val iconColor = if (isDark) style.baseColor.iconLighten(0.10f) else style.baseColor
    val surfaceTop = if (isDark) Color(0xFF25292D) else Color(0xFFF7F9FA)
    val surfaceBottom = if (isDark) Color(0xFF1C2024) else Color(0xFFE9EEF1)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.065f) else Color.Black.copy(alpha = 0.045f)
    val corner = (size * 0.29f).coerceAtMost(18.dp)
    val shape = RoundedCornerShape(corner)

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = if (isDark) 2.dp else 1.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.14f else 0.07f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.18f else 0.09f)
            )
            .clip(shape)
            .background(Brush.linearGradient(listOf(surfaceTop, surfaceBottom)))
            .border(0.8.dp, borderColor, shape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = category.name,
                modifier = Modifier
                    .size(size * 0.72f)
                    .clip(RoundedCornerShape(corner * 0.74f)),
                contentScale = ContentScale.Crop
            )
        } else {
            CategoryGlyph(
                kind = style.kind,
                color = iconColor,
                size = size * 0.44f
            )
        }
    }
}
