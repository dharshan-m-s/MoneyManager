package com.moneymanager.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Scroll-linked chrome progress. Unlike a direction threshold, this tracks actual consumed
 * vertical distance, so rapid flings and interrupted reverse gestures continuously retarget.
 */
data class ScrollChromeState(
    val progress: State<Float>,
    val collapsed: State<Boolean>,
    val connection: NestedScrollConnection
)

@Composable
fun rememberScrollChromeState(collapseDistance: Dp = 112.dp): ScrollChromeState {
    val rangePx = with(LocalDensity.current) { collapseDistance.toPx().coerceAtLeast(1f) }
    var offsetPx by rememberSaveable { mutableFloatStateOf(0f) }

    val progress = remember(rangePx) {
        derivedStateOf { (offsetPx / rangePx).coerceIn(0f, 1f) }
    }
    val collapsed = remember(progress) {
        derivedStateOf { progress.value >= 0.88f }
    }
    val connection = remember(rangePx) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (consumed.y != 0f) {
                    // Scrolling down consumes a negative Y delta; scrolling back up reduces it.
                    offsetPx = (offsetPx - consumed.y).coerceIn(0f, rangePx)
                }
                return Offset.Zero
            }
        }
    }
    return ScrollChromeState(progress, collapsed, connection)
}

/**
 * Progressively removes an expanded heading from layout while translating/fading it upward.
 * The State is read in the layout and graphics-layer lambdas, avoiding a screen-wide
 * recomposition for every scroll pixel.
 */
fun Modifier.scrollCollapse(progress: State<Float>): Modifier =
    this
        .graphicsLayer {
            val fraction = progress.value.coerceIn(0f, 1f)
            alpha = 1f - fraction
            translationY = -12.dp.toPx() * fraction
            scaleY = 1f - 0.025f * fraction
        }
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val fraction = progress.value.coerceIn(0f, 1f)
            val visibleHeight = (placeable.height * (1f - fraction))
                .roundToInt()
                .coerceIn(0, placeable.height)
            layout(placeable.width, visibleHeight) {
                placeable.placeRelative(0, -((placeable.height - visibleHeight) / 2))
            }
        }
        .clipToBounds()


/** Smoothly reduces a toolbar's occupied height while keeping its controls vertically centred. */
fun Modifier.scrollCompactHeight(
    progress: State<Float>,
    expandedHeight: Dp = 72.dp,
    compactHeight: Dp = 54.dp
): Modifier =
    this
        .layout { measurable, constraints ->
            // Respect short/landscape/IME-constrained viewports instead of asking Compose
            // to measure a 72dp chrome row inside a smaller parent constraint.
            val expandedPx = maxOf(
                constraints.minHeight,
                minOf(expandedHeight.roundToPx(), constraints.maxHeight)
            )
            val compactPx = maxOf(
                constraints.minHeight,
                minOf(compactHeight.roundToPx(), expandedPx)
            )
            val placeable = measurable.measure(
                constraints.copy(minHeight = expandedPx, maxHeight = expandedPx)
            )
            val fraction = progress.value.coerceIn(0f, 1f)
            val targetHeight = (
                expandedPx + (compactPx - expandedPx) * fraction
            ).roundToInt().coerceIn(compactPx, expandedPx)
            layout(placeable.width, targetHeight) {
                placeable.placeRelative(0, -((placeable.height - targetHeight) / 2))
            }
        }
        .clipToBounds()


/** Slightly compacts a search control in both axes while its surrounding actions stay fixed. */
fun Modifier.scrollCompactField(progress: State<Float>): Modifier =
    this.graphicsLayer {
        val fraction = progress.value.coerceIn(0f, 1f)
        scaleX = 1f - 0.045f * fraction
        scaleY = 1f - 0.12f * fraction
    }
