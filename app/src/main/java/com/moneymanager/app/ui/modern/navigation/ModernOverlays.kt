package com.moneymanager.app.ui.modern.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneymanager.app.ui.components.mmMenuFrost
import com.moneymanager.app.ui.theme.MmColors
import com.moneymanager.app.ui.theme.MmType
import com.moneymanager.app.ui.navigation.Destination

private val moreDestinations = listOf(
    Destination.Bills,
    Destination.Cash,
    Destination.Income,
    Destination.Categories,
    Destination.Reimbursements,
    Destination.Settings
)

internal data class AddAction(
    val label: String,
    val icon: ImageVector,
    val tint: Color
)

/**
 * Add uses the same compact launcher as More: a 2-column x 3-row grid of one glyph and one
 * short label per tile. No descriptions, no chevrons and no count badge in the header.
 */
@Composable
internal fun ModernAddActionSurface(
    actions: List<AddAction>,
    modifier: Modifier = Modifier,
    title: String = "Add",
    subtitle: String = "Choose a transaction type",
    onSelect: (Int) -> Unit
) {
    OverlayCard(modifier = modifier) {
        val light = MaterialTheme.colors.isLight
        val separator = if (light) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.10f)
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MmType.sectionTitle, color = MmColors.textPrimary)
                    Text(subtitle, style = MmType.caption, color = MmColors.textSecondary)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(separator)
            )
            // The same 2-column x 3-row launcher grid as More: one glyph and one short label
            // per tile. No descriptions, no chevrons, no second line of information.
            actions.chunked(2).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEachIndexed { columnIndex, action ->
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (MaterialTheme.colors.isLight) {
                                        MmColors.surfaceMuted.copy(alpha = 0.76f)
                                    } else {
                                        Color.White.copy(alpha = 0.08f)
                                    }
                                )
                                .clickable { onSelect(rowIndex * 2 + columnIndex) }
                                .semantics { contentDescription = action.label }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(action.tint.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    action.icon,
                                    contentDescription = null,
                                    tint = action.tint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(9.dp))
                            Text(
                                action.label,
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

/**
 * The More menu belongs to the navigation side of the app: a compact grid of destinations that
 * are not first-class dock tabs. Different geometry and a different header from the Add sheet.
 */
@Composable
internal fun ModernMorePopover(
    currentRoute: String,
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
            }
            moreDestinations.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { destination ->
                        val tint = tintFor(destination)
                        val selected = destination.route == currentRoute
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (selected) {
                                        MmColors.accent.copy(alpha = 0.14f)
                                    } else if (MaterialTheme.colors.isLight) {
                                        MmColors.surfaceMuted.copy(alpha = 0.76f)
                                    } else {
                                        // BitChord's neutral row/pressed strength: a hairline of
                                        // light over the black frosted slab, not a grey plate.
                                        Color.White.copy(alpha = 0.08f)
                                    }
                                )
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
    val shape = RoundedCornerShape(30.dp)
    val rim = if (MaterialTheme.colors.isLight) {
        Color.Black.copy(alpha = 0.06f)
    } else {
        Color.White.copy(alpha = 0.10f)
    }
    // BitChord draws the menu shadow as a separate plate because its offscreen frosted
    // layer clips a shadow drawn on the panel itself. Same here: the shadow sits on the
    // wrapper, while the frosted Surface stays flat and rimmed.
    Box(
        modifier = modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth()
            .shadow(
                elevation = 24.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.30f)
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .mmMenuFrost(shape)
                .border(0.5.dp, rim, shape),
            shape = shape,
            color = Color.Transparent,
            elevation = 0.dp
        ) {
            content()
        }
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
