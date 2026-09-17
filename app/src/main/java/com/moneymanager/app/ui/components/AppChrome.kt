package com.moneymanager.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.ui.theme.MMGreenDark
import com.moneymanager.app.ui.theme.MMWhite
import com.moneymanager.app.ui.theme.MmSpacing

/**
 * The app's one top bar. It is deliberately shallow: back, title (with an optional second line)
 * and a small trailing action row, so toolbars never end up overcrowded (Apple HIG: purposeful
 * actions, concise navigation). The brand green is a fixed colour in both themes, so the white
 * foreground here is intentional and dark-mode safe.
 */
@Composable
fun MoneyManagerTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    navigationIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    onNavigationClick: (() -> Unit)? = onBack,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = MMGreenDark, elevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The brand surface runs under the status bar; the content sits below it.
                .statusBarsPadding()
                .height(64.dp)
                .background(MMGreenDark)
                .padding(horizontal = MmSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onNavigationClick != null) {
                IconButton(onClick = onNavigationClick) {
                    Icon(navigationIcon, contentDescription = "Back", tint = MMWhite)
                }
            } else {
                Spacer(Modifier.width(MmSpacing.md))
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = MmSpacing.xs)
            ) {
                Text(
                    title,
                    color = MMWhite,
                    fontSize = 20.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        color = MMWhite.copy(alpha = .75f),
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
                horizontalArrangement = Arrangement.spacedBy(MmSpacing.xs),
                modifier = Modifier.padding(end = MmSpacing.xs),
                content = actions
            )
        }
    }
}
