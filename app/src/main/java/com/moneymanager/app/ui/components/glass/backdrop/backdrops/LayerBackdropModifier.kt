/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
package com.moneymanager.app.ui.components.glass.backdrop.backdrops

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import com.moneymanager.app.ui.components.glass.backdrop.internal.recordLayer

fun Modifier.layerBackdrop(backdrop: LayerBackdrop): Modifier =
    this then LayerBackdropElement(backdrop)

private class LayerBackdropElement(
    val backdrop: LayerBackdrop
) : ModifierNodeElement<LayerBackdropNode>() {

    override fun create(): LayerBackdropNode {
        return LayerBackdropNode(backdrop)
    }

    override fun update(node: LayerBackdropNode) {
        if (node.backdrop != backdrop) {
            node.backdrop.layerCoordinates = null
            node.backdrop = backdrop
        }
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "layerBackdrop"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayerBackdropElement) return false

        if (backdrop != other.backdrop) return false

        return true
    }

    override fun hashCode(): Int {
        return backdrop.hashCode()
    }
}

private class LayerBackdropNode(
    var backdrop: LayerBackdrop
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    override fun ContentDrawScope.draw() {
        if (size.width < 1f || size.height < 1f ||
            backdrop.isRecording || backdrop.captureUnavailable
        ) {
            // Always keep normal page content visible when capture is unavailable or nested.
            drawContent()
            return
        }

        // Refresh the source BEFORE drawing the visible page. Glass surfaces inside the
        // capture omit themselves in DrawBackdropNode while isRecording is true; the normal
        // draw below then samples this exact frame instead of the previous scroll frame.
        // The captured layer is never composited here, so the RenderNode graph stays acyclic.
        // Enter the shared guard before recording this live page layer.
        backdrop.beginRecording()
        try {
            recordLayer(this@LayerBackdropNode, backdrop.graphicsLayer) {
                backdrop.onDraw(this@draw)
            }
        } catch (_: RuntimeException) {
            // GPU/renderer-specific capture failures should disable the effect, not take down
            // navigation or selection. The regular page draw below remains available.
            backdrop.markCaptureUnavailable()
        } finally {
            backdrop.endRecording()
        }

        // Draw once to the window after the current page layer has been refreshed.
        drawContent()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.layerCoordinates = coordinates
        }
    }

    override fun onDetach() {
        backdrop.layerCoordinates = null
    }
}
