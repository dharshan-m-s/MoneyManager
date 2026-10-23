package com.moneymanager.app.ui.modern.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
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
import com.moneymanager.app.ui.modern.theme.MmColors
import com.moneymanager.app.ui.modern.theme.MmSpacing
import com.moneymanager.app.ui.modern.theme.MmType
import com.moneymanager.app.ui.navigation.Destination

private val moreDestinations = listOf(
    Destination.Bills,
    Destination.Cash,
    Destination.SpendSummary,
    Destination.Categories,
    Destination.Reimbursements,
    Destination.Settings
)

@Composable
internal fun ModernMorePopover(
    modifier: Modifier = Modifier,
    onSelect: (Destination) -> Unit
) {
    OverlayPanel(
        modifier = modifier.width(280.dp),
        title = "More",
        subtitle = "Quick access to tools"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            moreDestinations.forEach { destination ->
                OverlayDestinationItem(destination) { onSelect(destination) }
            }
        }
    }
}

internal data class AddAction(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val description: String
)

@Composable
internal fun ModernAddActionSurface(
    actions: List<AddAction>,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit
) {
    OverlayPanel(
        modifier = modifier.width(300.dp),
        title = "Add transaction",
        subtitle = "Choose a transaction type"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            actions.forEachIndexed { index, action ->
                OverlayActionItem(action) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun OverlayPanel(
    modifier: Modifier,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MmColors.surface,
        border = BorderStroke(1.dp, MmColors.outline),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MmType.sectionTitle, color = MmColors.textPrimary)
                Text(subtitle, style = MmType.caption, color = MmColors.textSecondary)
            }
            content()
        }
    }
}

@Composable
private fun OverlayDestinationItem(
    destination: Destination,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .semantics { contentDescription = destination.label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MmColors.surfaceContainer,
            border = BorderStroke(1.dp, MmColors.outline)
        ) {
            Icon(
                destination.icon,
                contentDescription = null,
                tint = MmColors.textPrimary,
                modifier = Modifier.size(36.dp).padding(8.dp)
            )
        }
        Text(
            destination.label,
            style = MmType.body,
            color = MmColors.textPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OverlayActionItem(
    action: AddAction,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .semantics { contentDescription = action.description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = action.tint.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, action.tint.copy(alpha = 0.25f))
        ) {
            Icon(
                action.icon,
                contentDescription = null,
                tint = action.tint,
                modifier = Modifier.size(36.dp).padding(8.dp)
            )
        }
        Text(
            action.label,
            style = MmType.body,
            color = MmColors.textPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}
