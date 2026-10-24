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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneymanager.app.ui.modern.theme.MmColors
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

internal data class AddAction(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val description: String
)

@Composable
internal fun ModernMorePopover(
    modifier: Modifier = Modifier,
    onSelect: (Destination) -> Unit
) {
    Surface(
        modifier = modifier.width(356.dp),
        shape = RoundedCornerShape(28.dp),
        color = MmColors.surface,
        border = BorderStroke(1.dp, MmColors.outline),
        elevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(
                        Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF7C3AED)))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                }
                Column {
                    Text("More", style = MmType.sectionTitle, color = MmColors.textPrimary)
                    Text("All tools in one place", style = MmType.caption, color = MmColors.textSecondary)
                }
            }
            val rows = moreDestinations.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { destination ->
                            val tint = when (destination) {
                                Destination.Bills -> Color(0xFFF59E0B)
                                Destination.Cash -> Color(0xFF22C55E)
                                Destination.SpendSummary -> Color(0xFF8B5CF6)
                                Destination.Categories -> Color(0xFF06B6D4)
                                Destination.Reimbursements -> Color(0xFF14B8A6)
                                Destination.Settings -> Color(0xFF64748B)
                                else -> MmColors.accent
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onSelect(destination) }
                                    .semantics { contentDescription = destination.label }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = tint.copy(alpha = 0.16f)
                                ) {
                                    Icon(destination.icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp).padding(12.dp))
                                }
                                Text(destination.label, style = MmType.caption, fontWeight = FontWeight.SemiBold, color = MmColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ModernAddActionSurface(
    actions: List<AddAction>,
    modifier: Modifier = Modifier,
    title: String = "Create",
    subtitle: String = "Pick what to add",
    onSelect: (Int) -> Unit
) {
    Surface(
        modifier = modifier.width(356.dp),
        shape = RoundedCornerShape(28.dp),
        color = MmColors.surface,
        border = BorderStroke(1.dp, MmColors.outline),
        elevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(MmColors.accentFill, Color(0xFF1D4ED8)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                }
                Column {
                    Text(title, style = MmType.sectionTitle, color = MmColors.textPrimary)
                    Text(subtitle, style = MmType.caption, color = MmColors.textSecondary)
                }
            }
            val rows = actions.chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { rowIndex, action ->
                            val actionIndex = actions.indexOf(action)
                            val idx = if (actionIndex >= 0) actionIndex else rowIndex
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onSelect(idx) }
                                    .semantics { contentDescription = action.description }
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(shape = RoundedCornerShape(16.dp), color = action.tint.copy(alpha = 0.14f)) {
                                    Icon(action.icon, contentDescription = null, tint = action.tint, modifier = Modifier.size(48.dp).padding(12.dp))
                                }
                                Text(action.label, style = MmType.caption, fontWeight = FontWeight.SemiBold, color = MmColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
