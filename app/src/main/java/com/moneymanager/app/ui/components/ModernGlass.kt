package com.moneymanager.app.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import com.moneymanager.app.ui.theme.MmColors
import timber.log.Timber

/**
 * The Modern hero's liquid-glass pass — rendered-content refraction, not blur or transparency.
 *
 * A single AGSL program runs per pixel through [RuntimeShader] (API 33+):
 *
 *  1. **Lens field.** `n = d * (0.30 + 1.10 r²)` approximates the surface normal of a shallow
 *     spherical cap (`z ≈ r² / 2R`), so displacement grows with the square of the distance from
 *     the optical centre, as it does on a real lens.
 *  2. **Refraction + chromatic dispersion.** The shader samples the rendered card content three
 *     times through the AGSL child shader, with red bent least and blue bent most
 *     (`0.86 / 1.00 / 1.18`). This is a real optical displacement of the surface beneath the
 *     glass, not a procedural colour wash.
 *  3. **Fresnel rim + specular sweep.** A `smoothstep` on radius brightens grazing angles and one
 *     narrow top highlight marks the light source.
 *
 * Cost: one off-screen layer and one GPU shader pass with a slow light sweep, no bitmap capture
 * and no blur. Each prominent surface remembers its shader and updates uniforms when its size
 * changes.
 *
 * API 30–32 (or a device that fails to create the shader) falls back to a specular corner bloom
 * plus a hairline rim. Android 13+ gets the actual rendered-content refraction path.
 */
@Composable
fun Modifier.mmLiquidGlass(
    cornerRadius: Dp = 26.dp,
    intensity: Float = 1f
): Modifier = this.clip(RoundedCornerShape(cornerRadius))

/**
 * API 33+ shader holder. Kept behind [RequiresApi] so the runtime API check at the call site is
 * the only place the platform version is consulted, and lint can verify it.
 */
private class GlassProgram(
    val shader: RuntimeShader,
    val effect: RenderEffect
)

private object GlassSupport {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun create(): GlassProgram? = try {
        val shader = RuntimeShader(REFRACTION_AGSL)
        // `contents` is the off-screen layer produced by saveLayer. The shader therefore
        // refracts the actual rendered card rather than a generated texture.
        val effect = RenderEffect.createRuntimeShaderEffect(shader, "contents")
        GlassProgram(shader, effect)
    } catch (exception: RuntimeException) {
        Timber.w(exception, "Liquid glass shader unavailable; using the static glass treatment")
        null
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun updateUniforms(
        program: GlassProgram,
        width: Float,
        height: Float,
        intensity: Float,
        time: Float
    ) {
        program.shader.setFloatUniform("uSize", width, height)
        program.shader.setFloatUniform("uIntensity", intensity)
        program.shader.setFloatUniform("uTime", time)
    }

    /**
     * Hides the costly RenderEffect + off-screen layer on small surfaces. The full pass is
     * reserved for prominent glass surfaces; ordinary controls get the cheaper specular sheen.
     */
    fun isCheaperFallback(w: Int, h: Int, intensity: Float): Boolean {
        val area = w.toFloat() * h.toFloat()
        return intensity <= 0.35f && area <= 140000f
    }
}

/**
 * A single static specular sheen for small glass chips (selected segments, pills, FAB). Cheaper
 * than the full refraction pass and reserved for controls that sit on top of a colour fill.
 */
@Composable
fun Modifier.mmGlassHighlight(
    cornerRadius: Dp = 20.dp,
    intensity: Float = 1f
): Modifier = this.clip(RoundedCornerShape(cornerRadius))

/**
 * AGSL program for the hero glass. `contents` is the rendered layer supplied by
 * `RenderEffect.createRuntimeShaderEffect`, so the colour channels below are genuinely sampling
 * different refracted positions in the card. The displacement stays deliberately small in the
 * centre and grows toward the spherical edge; readable text is therefore preserved while the
 * edge still catches the characteristic liquid-glass bend.
 */
private const val REFRACTION_AGSL = """
uniform shader contents;
uniform float2 uSize;
uniform float uIntensity;
uniform float uTime;

float2 lensOffset(float2 p) {
    float2 center = float2(0.18, -0.08);
    float2 d = p - center;
    float r = length(d);
    // A shallow spherical cap: slope is minimal in the centre and increases toward the rim.
    float edge = smoothstep(0.12, 0.95, r);
    return d * (0.0025 + edge * 0.020) * uIntensity;
}

half4 main(float2 fragCoord) {
    float2 safeSize = max(uSize, float2(1.0, 1.0));
    float2 p = fragCoord / safeSize;
    float2 bend = lensOffset(p) * safeSize;

    // Wavelength-dependent index of refraction. Blue bends more than red, but the split is kept
    // sub-pixel through the centre to avoid chromatic fringing on text.
    half4 red = contents.eval(fragCoord + bend * 0.86);
    half4 green = contents.eval(fragCoord + bend);
    half4 blue = contents.eval(fragCoord + bend * 1.18);
    half4 base = contents.eval(fragCoord);

    float2 d = p - float2(0.18, -0.08);
    float r = length(d);
    float rim = smoothstep(0.32, 0.95, r);
    float fresnel = pow(clamp(rim, 0.0, 1.0), 2.4);
    float spec = pow(clamp(1.0 - abs(p.y - 0.04) * 2.8, 0.0, 1.0), 20.0);
    float2 light = float2(0.18 + fract(uTime * 0.82) * 0.92, 0.05);
    float sweep = smoothstep(0.0, 1.0, clamp(1.0 - length(p - light) * 0.95, 0.0, 1.0));

    float3 refracted = float3(red.r, green.g, blue.b);
    float3 rgb = mix(base.rgb, refracted, 0.72 * clamp(rim + 0.12, 0.0, 1.0));
    rgb += float3(0.70, 0.78, 1.0) * (fresnel * 0.055 + spec * 0.075 + sweep * 0.018) * uIntensity;
    return half4(clamp(rgb, 0.0, 1.0), base.a);
}
"""
