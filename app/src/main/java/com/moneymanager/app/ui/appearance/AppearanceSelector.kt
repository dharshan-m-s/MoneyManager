package com.moneymanager.app.ui.appearance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moneymanager.app.ui.theme.MmType

/**
 * Appearance picker: two full-width radio cards, each with a miniature of the look it selects.
 *
 * The component reads the ACTIVE theme, so it is visually native in either appearance: Default
 * renders it with the green identity, Modern with the indigo one. Selection is expressed by
 * border, tint, the radio dot and an explicit "Selected" label — never by colour alone.
 *
 * Accessibility: the group carries `selectableGroup()` semantics, each card is a single
 * `Role.RadioButton` target with a >= 96dp row height, and the preview is decorative (the text
 * labels carry the meaning).
 */
@Composable
fun AppearanceSelector(
    selected: MmAppearance,
    onSelect: (MmAppearance) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colors.primary
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppearanceOption(
            appearance = MmAppearance.DEFAULT,
            description = "The original Money Manager experience.",
            selected = selected == MmAppearance.DEFAULT,
            accent = accent,
            onClick = { onSelect(MmAppearance.DEFAULT) }
        )
        AppearanceOption(
            appearance = MmAppearance.MODERN,
            description = "A calm, clean layout with a floating dock and clear amounts.",
            selected = selected == MmAppearance.MODERN,
            accent = accent,
            onClick = { onSelect(MmAppearance.MODERN) }
        )
    }
}

@Composable
private fun AppearanceOption(
    appearance: MmAppearance,
    description: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val surface = MaterialTheme.colors.surface
    val border = if (selected) accent else MaterialTheme.colors.onSurface.copy(alpha = 0.14f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .border(if (selected) 1.5.dp else 1.dp, border, RoundedCornerShape(20.dp))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppearancePreview(appearance)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    appearance.label,
                    style = MmType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colors.onSurface
                )
                if (selected) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.16f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "Selected",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                description,
                style = MmType.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.66f),
                maxLines = 3
            )
        }
        Spacer(Modifier.width(10.dp))
        // Radio indicator: ring + dot, so selection survives a colour-vision difference.
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (selected) accent else MaterialTheme.colors.onSurface.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(Modifier.size(11.dp).clip(CircleShape).background(accent))
            }
        }
    }
}

/**
 * Miniature of the look. Purely illustrative: two tiny stacked surfaces, a hero card and rows, in
 * the exact palette the appearance uses. Drawn with Canvas so it costs no assets.
 */
@Composable
private fun AppearancePreview(appearance: MmAppearance) {
    val modern = appearance == MmAppearance.MODERN
    Box(
        Modifier
            .width(58.dp)
            .height(76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (modern) Color(0xFFF7F8FA) else Color(0xFFF3F7F5))
            .border(
                1.dp,
                if (modern) Color(0xFFD8DCE3) else Color(0xFFDCE7E1),
                RoundedCornerShape(12.dp)
            )
    ) {
        Canvas(Modifier.size(58.dp, 76.dp)) {
            if (modern) drawModernPreview() else drawDefaultPreview()
        }
    }
}

private fun DrawScope.drawModernPreview() {
    // Quiet header on a soft background.
    drawRoundRect(
        color = Color(0xFF1C1C1E),
        topLeft = Offset(5.dp.toPx(), 6.dp.toPx()),
        size = Size(22.dp.toPx(), 3.dp.toPx()),
        cornerRadius = CornerRadius(1.5.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFF8E8E93),
        topLeft = Offset(5.dp.toPx(), 12.dp.toPx()),
        size = Size(14.dp.toPx(), 2.dp.toPx()),
        cornerRadius = CornerRadius(1.dp.toPx())
    )
    // Hero surface with a single accent keyline.
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(5.dp.toPx(), 20.dp.toPx()),
        size = Size(48.dp.toPx(), 26.dp.toPx()),
        cornerRadius = CornerRadius(6.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFF6C6C70),
        topLeft = Offset(9.dp.toPx(), 25.dp.toPx()),
        size = Size(12.dp.toPx(), 2.dp.toPx()),
        cornerRadius = CornerRadius(1.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFF1C1C1E),
        topLeft = Offset(9.dp.toPx(), 30.dp.toPx()),
        size = Size(22.dp.toPx(), 4.dp.toPx()),
        cornerRadius = CornerRadius(2.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFF2563EB),
        topLeft = Offset(9.dp.toPx(), 38.dp.toPx()),
        size = Size(16.dp.toPx(), 3.dp.toPx()),
        cornerRadius = CornerRadius(1.5.dp.toPx())
    )
    // Two metric tiles.
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(5.dp.toPx(), 50.dp.toPx()),
        size = Size(23.dp.toPx(), 11.dp.toPx()),
        cornerRadius = CornerRadius(4.dp.toPx())
    )
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(30.dp.toPx(), 50.dp.toPx()),
        size = Size(23.dp.toPx(), 11.dp.toPx()),
        cornerRadius = CornerRadius(4.dp.toPx())
    )
    drawCircle(Color(0xFFDC2626), radius = 1.6.dp.toPx(), center = Offset(9.dp.toPx(), 55.dp.toPx()))
    drawCircle(Color(0xFF047857), radius = 1.6.dp.toPx(), center = Offset(34.dp.toPx(), 55.dp.toPx()))
    // Floating dock.
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(5.dp.toPx(), 65.dp.toPx()),
        size = Size(48.dp.toPx(), 8.dp.toPx()),
        cornerRadius = CornerRadius(4.dp.toPx())
    )
    drawCircle(Color(0xFF2563EB), radius = 4.dp.toPx(), center = Offset(29.dp.toPx(), 69.dp.toPx()))
}

private fun DrawScope.drawDefaultPreview() {
    // Brand header band.
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF0B6B4A), Color(0xFF0A6E4B))),
        size = Size(size.width, 20.dp.toPx())
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.85f),
        topLeft = Offset(5.dp.toPx(), 6.dp.toPx()),
        size = Size(22.dp.toPx(), 3.dp.toPx()),
        cornerRadius = CornerRadius(1.5.dp.toPx())
    )
    // Hero number block on the band.
    drawRoundRect(
        color = Color.White.copy(alpha = 0.12f),
        topLeft = Offset(5.dp.toPx(), 12.dp.toPx()),
        size = Size(48.dp.toPx(), 12.dp.toPx()),
        cornerRadius = CornerRadius(4.dp.toPx())
    )
    // White cards with hairline edges.
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(5.dp.toPx(), 26.dp.toPx()),
        size = Size(48.dp.toPx(), 17.dp.toPx()),
        cornerRadius = CornerRadius(5.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFFF0F4F2),
        topLeft = Offset(9.dp.toPx(), 30.dp.toPx()),
        size = Size(14.dp.toPx(), 3.dp.toPx()),
        cornerRadius = CornerRadius(1.5.dp.toPx())
    )
    drawRoundRect(
        color = Color(0xFF067A4C),
        topLeft = Offset(9.dp.toPx(), 36.dp.toPx()),
        size = Size(20.dp.toPx(), 3.dp.toPx()),
        cornerRadius = CornerRadius(1.5.dp.toPx())
    )
    // Two stat tiles.
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(5.dp.toPx(), 47.dp.toPx()),
        size = Size(23.dp.toPx(), 11.dp.toPx()),
        cornerRadius = CornerRadius(4.dp.toPx())
    )
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(30.dp.toPx(), 47.dp.toPx()),
        size = Size(23.dp.toPx(), 11.dp.toPx()),
        cornerRadius = CornerRadius(4.dp.toPx())
    )
    drawCircle(Color(0xFF047857), radius = 1.6.dp.toPx(), center = Offset(9.dp.toPx(), 52.dp.toPx()))
    drawCircle(Color(0xFFDC2626), radius = 1.6.dp.toPx(), center = Offset(34.dp.toPx(), 52.dp.toPx()))
    // List card.
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(5.dp.toPx(), 62.dp.toPx()),
        size = Size(48.dp.toPx(), 10.dp.toPx()),
        cornerRadius = CornerRadius(4.dp.toPx())
    )
}
