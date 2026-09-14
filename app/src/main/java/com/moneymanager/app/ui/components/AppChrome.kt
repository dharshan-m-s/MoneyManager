package com.moneymanager.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMOutline
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.PageColors

@Composable
fun MoneyManagerTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    navigationIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    onNavigationClick: (() -> Unit)? = onBack,
    pageColors: PageColors? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val gradientStart = pageColors?.gradientStart ?: MMGreenDark
    val gradientEnd = pageColors?.gradientEnd ?: MMGreenDark

    Box(Modifier.fillMaxWidth()) {
        // Gradient background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientStart)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onNavigationClick != null) {
                    IconButton(onClick = onNavigationClick) {
                        Icon(navigationIcon, contentDescription = "Back", tint = MMWhite)
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        title,
                        color = MMWhite,
                        fontSize = 22.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = (-0.3).sp
                    )
                    subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            color = MMWhite.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(end = 8.dp),
                    content = actions
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(gradientEnd.copy(alpha = 0.42f))
            )
        }
    }
}

@Composable
fun RowScope.OneUiTopBarAction(
    icon: ImageVector,
    contentDescription: String,
    showDot: Boolean = false,
    onClick: () -> Unit
) {
    // One UI 9 style: frosted 42dp squircle, centered 22dp icon, generous
    // separation so adjacent actions never visually overlap.
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MMWhite.copy(alpha = 0.13f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = MMWhite,
            modifier = Modifier.size(22.dp)
        )
        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(9.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MMWhite)
            )
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colors.primary,
    onSeeAll: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = 1.dp,
        backgroundColor = MaterialTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MMOutline.copy(alpha = 0.3f))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Accent dot before title
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.subtitle1,
                    modifier = Modifier.weight(1f)
                )
                onSeeAll?.let {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { it() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "See all",
                            color = accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = "See all",
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            content()
        }
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = animatedColor.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, animatedColor.copy(alpha = 0.15f))
    ) {
        Text(
            text,
            color = animatedColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun IconBadge(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    tint: Color = MaterialTheme.colors.primary,
    backgroundColor: Color = Color.Transparent,
    size: Int = 44
) {
    val bgColor = if (backgroundColor == Color.Transparent) tint.copy(alpha = 0.11f) else backgroundColor
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(size.dp / 3))
            .background(bgColor)
            .border(1.dp, tint.copy(alpha = 0.08f), RoundedCornerShape(size.dp / 3)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.5).dp))
    }
}

@Composable
fun ClickableSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        )
    )
    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.large,
        elevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MMOutline.copy(alpha = 0.4f))
    ) {
        content()
    }
}
