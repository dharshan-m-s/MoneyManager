package com.moneymanager.app.ui.components

import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Direction-aware scroll signal shared by collapsing page chrome. */
data class ScrollChromeState(
    val collapsed: Boolean,
    val connection: NestedScrollConnection
)

@Composable
fun rememberScrollChromeState(): ScrollChromeState {
    var collapsed by rememberSaveable { mutableStateOf(false) }
    var accumulated by remember { mutableStateOf(0f) }
    val threshold = with(LocalDensity.current) { 18.dp.toPx() }
    val connection = remember(threshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0f) {
                    accumulated += -delta
                    if (accumulated >= threshold) {
                        collapsed = true
                        accumulated = 0f
                    }
                } else if (delta > 0f) {
                    accumulated += delta
                    if (accumulated >= threshold) {
                        collapsed = false
                        accumulated = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }
    return ScrollChromeState(collapsed, connection)
}
