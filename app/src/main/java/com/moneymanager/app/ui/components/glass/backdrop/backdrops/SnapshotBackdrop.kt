package com.moneymanager.app.ui.components.glass.backdrop.backdrops

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.moneymanager.app.ui.components.glass.backdrop.Backdrop
import com.moneymanager.app.ui.components.glass.backdrop.internal.InverseLayerScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Vendored addition: a backdrop that is a still of its source rather than the
 * source live.
 *
 * [LayerBackdrop] keeps a recording that is the source by reference — every
 * animated thing inside it, a progress bar's tick or a drifting mesh, makes
 * whatever samples it render the whole source again and re-run its effects on
 * the next frame. For a panel that is up for a few seconds over content the
 * person isn't looking at, that is a full-screen redraw and a blur per frame
 * bought for nothing. This takes one picture when [capture] is called — small,
 * since only a blur will ever see it — and the effects run on that, again only
 * when the panel itself moves.
 *
 * Nothing is recorded until [capture]: the source draws exactly as it would
 * without this. [release] lets go of the picture once nothing samples it.
 */
@Stable
class SnapshotBackdrop internal constructor() : Backdrop {

    internal var image: ImageBitmap? by mutableStateOf(null)
    internal var sourceCoordinates: LayoutCoordinates? = null
    /** Read while drawing, so bumping it is what asks for a frame to record. */
    internal var requests by mutableIntStateOf(0)
    internal var handled = 0
    /** Bumped by [release], so a still that lands after it is dropped. */
    internal var generation = 0

    // Automatic capture state. These are intentionally plain counters: the source may
    // request a new still from inside its draw pass, and a snapshot-state write there
    // would invalidate the source again and keep the page redrawing while idle.
    internal var autoRequests = 0
    internal var capturing = false
    /** True only during the synchronous source recording pass. */
    internal var snapshotCaptureInProgress: Boolean = false
    internal var skipAutoCapture = false
    internal var lastAutoCaptureNanos = 0L

    private var inverseLayerScope: InverseLayerScope? = null

    /** Takes a still of the source on its next frame. */
    fun capture() {
        requests++
    }

    /** Whether a still is in — for a panel that would rather not open on none. */
    val hasStill: Boolean get() = image != null

    /** Lets the still go; until the next [capture] this draws nothing. */
    fun release() {
        image = null
        generation++
    }

    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val image = image ?: return
        val coordinates = coordinates ?: return
        val source = sourceCoordinates?.takeIf { it.isAttached } ?: return
        withTransform({
            if (layerBlock != null) {
                with(obtainInverseLayerScope()) { inverseTransform(density, layerBlock) }
            }
            val (offset, panelScale) =
                try {
                    // Where the panel's origin, and a point a step along from it,
                    // land in the source: their distance is the scale anything
                    // above the panel has it at — a menu growing in on a spring.
                    val origin = source.localPositionOf(coordinates)
                    val step = source.localPositionOf(coordinates, Offset(SCALE_PROBE_PX, 0f))
                    origin to ((step.x - origin.x) / SCALE_PROBE_PX).takeIf { it > 0f }
                } catch (_: Exception) {
                    (coordinates.positionInWindow() - source.positionInWindow()) to null
                }
            // Undone, so the still stays where it is on screen while the panel
            // grows over it, rather than zooming in and out with the panel.
            if (panelScale != null && panelScale != 1f) scale(1f / panelScale, pivot = Offset.Zero)
            translate(-offset.x, -offset.y)
        }) {
            scale(1f / SNAPSHOT_SCALE, pivot = Offset.Zero) { drawImage(image) }
        }
    }

    private fun obtainInverseLayerScope(): InverseLayerScope {
        return inverseLayerScope?.apply { reset() }
            ?: InverseLayerScope().also { inverseLayerScope = it }
    }
}

@Composable
/**
 * A still belongs to one navigation entry, not to the lifetime of the app root.
 * Keying it by the active back-stack entry prevents the first frame of a newly
 * opened destination from sampling the previous destination's cached image.
 */
fun rememberSnapshotBackdrop(key: Any? = Unit): SnapshotBackdrop =
    remember(key) { SnapshotBackdrop() }

/**
 * Makes this what [backdrop] takes its stills of. Draws as if it weren't here,
 * except on the one frame after [SnapshotBackdrop.capture], when it also
 * records itself small and turns that into the picture.
 *
 * Page chrome can set [autoCapture] so the still follows content that changes
 * while the page is drawing (scrolling, collapsing headers, animations). It is
 * throttled and stops when the source stops drawing, which keeps a still-based
 * backdrop close to the live page without turning an isolated glass surface into
 * a page -> glass -> page RenderNode cycle.
 */
@Composable
fun Modifier.snapshotBackdropSource(
    backdrop: SnapshotBackdrop,
    autoCapture: Boolean = false
): Modifier {
    val graphicsContext = LocalGraphicsContext.current
    val scope = rememberCoroutineScope()
    return this
        .onGloballyPositioned { backdrop.sourceCoordinates = it }
        .drawWithContent {
            drawContent()

            if (autoCapture) {
                if (backdrop.skipAutoCapture) {
                    backdrop.skipAutoCapture = false
                } else {
                    val now = System.nanoTime()
                    val due = now - backdrop.lastAutoCaptureNanos >= AUTO_CAPTURE_INTERVAL_NS
                    if (!backdrop.capturing && due) {
                        backdrop.lastAutoCaptureNanos = now
                        backdrop.autoRequests++
                    }
                }
            }

            val request = backdrop.requests + backdrop.autoRequests
            if (request == backdrop.handled || backdrop.capturing) return@drawWithContent
            backdrop.handled = request
            if (size.width < 1f || size.height < 1f) return@drawWithContent
            val layer = graphicsContext.createGraphicsLayer()
            val width = (size.width * SNAPSHOT_SCALE).roundToInt().coerceAtLeast(1)
            val height = (size.height * SNAPSHOT_SCALE).roundToInt().coerceAtLeast(1)
            val generation = backdrop.generation
            backdrop.capturing = true
            backdrop.snapshotCaptureInProgress = true
            com.moneymanager.app.ui.components.glass.backdrop.BackdropCaptureState.begin()
            try {
                layer.record(size = IntSize(width, height)) {
                    scale(SNAPSHOT_SCALE, pivot = Offset.Zero) { this@drawWithContent.drawContent() }
                }
            } catch (_: RuntimeException) {
                backdrop.capturing = false
                try {
                    graphicsContext.releaseGraphicsLayer(layer)
                } catch (_: RuntimeException) {
                    // Keep the source page drawable if the renderer discarded this layer.
                }
                return@drawWithContent
            } finally {
                backdrop.snapshotCaptureInProgress = false
                com.moneymanager.app.ui.components.glass.backdrop.BackdropCaptureState.end()
            }
            scope.launch {
                try {
                    // Only if nothing asked again, or let go, in the meantime.
                    val still = layer.toImageBitmap()
                    if (backdrop.handled == request && backdrop.generation == generation) {
                        backdrop.image = still
                        // The image update redraws the sampling surfaces. Do not let that
                        // redraw immediately request another still while the page is idle.
                        backdrop.skipAutoCapture = true
                    }
                } finally {
                    backdrop.capturing = false
                    graphicsContext.releaseGraphicsLayer(layer)
                }
            }
        }
}

/**
 * The still's resolution against its source's. Half resolution matches the resolution the
 * glass effect records its own backdrop at, so blur and lens refraction keep the same detail.
 */
private const val SNAPSHOT_SCALE = 0.5f

/** Minimum gap between automatic captures; ~30 fps is enough for scrolling page chrome. */
private const val AUTO_CAPTURE_INTERVAL_NS = 32_000_000L

/** How far along the panel the scale is read over, in its own pixels. */
private const val SCALE_PROBE_PX = 1000f
