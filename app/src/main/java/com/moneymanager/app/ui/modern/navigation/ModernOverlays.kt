package com.moneymanager.app.ui.modern.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneymanager.app.ui.components.pressableClick
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.navigation.Destination

private val moreDestinations = listOf(
    Destination.Bills,
    Destination.Cash,
    Destination.SpendSummary,
    Destination.Categories,
    Destination.Reimbursements,
    Destination.Settings
)

internal data class AddAction(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val description: String
)

/**
 * The Add menu is a focused create sheet: five transaction actions, one per row, with the
 * amount direction stated in words. It deliberately looks different from [ModernMorePopover],
 * which is a two-column tool grid - the two surfaces should never be mistaken for each other.
 */
@Composable
internal fun ModernAddActionSurface(
    actions: List<AddAction>,
    modifier: Modifier = Modifier,
    title: String = "New transaction",
    subtitle: String = "Choose how money moved",
    onSelect: (Int) -> Unit
) {
    OverlayCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MmColors.accentFill),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = MmColors.onAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(title, style = MmType.sectionTitle, color = MmColors.textPrimary)
                    Text(subtitle, style = MmType.caption, color = MmColors.textSecondary)
                }
            }
            Spacer(Modifier.height(2.dp))
            actions.forEachIndexed { index, action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .pressableClick { onSelect(index) }
                        .semantics { contentDescription = action.description }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(action.tint.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            action.icon,
                            contentDescription = null,
                            tint = action.tint,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            action.label,
                            style = MmType.body,
                            fontWeight = FontWeight.Medium,
                            color = MmColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            action.description,
                            style = MmType.caption,
                            color = MmColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MmColors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * The More menu belongs to the navigation side of the app: a compact grid of destinations that
 * are not first-class dock tabs. Different geometry and a different header from the Add sheet.
 */
@Composable
internal fun ModernMorePopover(
    modifier: Modifier = Modifier,
    onSelect: (Destination) -> Unit
) {
    OverlayCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("More", style = MmType.sectionTitle, color = MmColors.textPrimary)
                    Text(
                        "Tools, planning and settings",
                        style = MmType.caption,
                        color = MmColors.textSecondary
                    )
                }
                Text(
                    "${moreDestinations.size}",
                    style = MmType.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = MmColors.textTertiary
                )
            }
            moreDestinations.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { destination ->
                        val tint = tintFor(destination)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MmColors.surfaceMuted)
                                .clickable { onSelect(destination) }
                                .semantics { contentDescription = destination.label }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(tint.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    destination.icon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(9.dp))
                            Text(
                                destination.label,
                                style = MmType.caption,
                                fontWeight = FontWeight.SemiBold,
                                color = MmColors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun OverlayCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.widthIn(max = 400.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MmColors.surface,
        border = BorderStroke(1.dp, MmColors.outline),
        elevation = 12.dp
    ) {
        content()
    }
}

@Composable
private fun tintFor(destination: Destination): Color = when (destination) {
    Destination.Bills -> Color(0xFFB45309)
    Destination.Cash -> Color(0xFF047857)
    Destination.SpendSummary -> Color(0xFF6D28D9)
    Destination.Categories -> Color(0xFF0E7490)
    Destination.Reimbursements -> Color(0xFF0F766E)
    Destination.Settings -> MmColors.textSecondary
    else -> MmColors.accent
}
