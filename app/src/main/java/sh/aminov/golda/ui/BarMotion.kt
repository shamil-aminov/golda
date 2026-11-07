package sh.aminov.golda.ui

import android.database.ContentObserver
import android.graphics.RuntimeShader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import sh.aminov.golda.BuildConfig
import sh.aminov.golda.domain.tr

/** Dev is a build type in this project. Tests opt in explicitly to draw the experiments. */
internal val LocalBarExperiments = staticCompositionLocalOf { BuildConfig.BUILD_TYPE == "dev" }

internal enum class BarMotion {
    QUIET, WAVE, SHIMMER;

    val label: String get() = when (this) {
        QUIET -> tr("Тихая", "Quiet")
        WAVE -> tr("Волна", "Wave")
        SHIMMER -> tr("Перелив", "Shimmer")
    }

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: QUIET
    }
}

internal fun effectiveBarMotion(name: String, experiments: Boolean, api: Int, durationScale: Float): BarMotion {
    if (!experiments || durationScale <= 0f) return BarMotion.QUIET
    val motion = BarMotion.of(name)
    return if (motion == BarMotion.SHIMMER && api < 33) BarMotion.QUIET else motion
}

/** Observe changes as well as the initial value, including a change while Home is visible. */
@Composable
private fun animatorDurationScale(): Float {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    var scale by remember(resolver) { mutableFloatStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { scale = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        scale = read()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return scale
}

/** Decorative drawing only. The parent owns the bar's one TalkBack description. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GapMotion(name: String, start: Float, end: Float, color: Color, modifier: Modifier) {
    val experiments = LocalBarExperiments.current
    if (!experiments || name == BarMotion.QUIET.name || end <= start) return
    val motion = effectiveBarMotion(name, experiments, Build.VERSION.SDK_INT, animatorDurationScale())
    val clipped = modifier.clearAndSetSemantics {}.drawWithContent {
        clipRect(left = size.width * start, right = size.width * end) { this@drawWithContent.drawContent() }
    }
    when (motion) {
        BarMotion.QUIET -> Unit
        BarMotion.WAVE -> LinearWavyProgressIndicator(
            progress = { 1f },
            modifier = clipped,
            color = lerp(color, Color.White.copy(alpha = color.alpha), 0.16f),
            trackColor = Color.Transparent,
            stopSize = 0.dp,
            amplitude = { 0.15f },
            wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
            waveSpeed = WavyProgressIndicatorDefaults.LinearDeterminateWavelength / 3,
        )
        BarMotion.SHIMMER -> if (Build.VERSION.SDK_INT >= 33) GapShimmer(color, start, end, clipped)
    }
}

private const val ShimmerPeriodMillis = 3_000

// Float precision plus subpixel dither keeps the broad, low-contrast light free of visible bands.
private const val ShimmerShader = """
    uniform float2 resolution;
    uniform float2 segment;
    uniform float phase;
    uniform float strength;
    half4 main(float2 position) {
        float x = (position.x - segment.x) / segment.y;
        float diagonal = x + 0.16 * (position.y / resolution.y - 0.5);
        float distance = fract(diagonal - phase + 0.5) - 0.5;
        float light = exp(-distance * distance * 36.0);
        float noise = fract(sin(dot(position, float2(12.9898, 78.233))) * 43758.5453);
        float alpha = clamp(light * strength + (noise - 0.5) / 255.0, 0.0, 1.0);
        return half4(alpha, alpha, alpha, alpha);
    }
"""

@RequiresApi(33)
@Composable
private fun GapShimmer(color: Color, start: Float, end: Float, modifier: Modifier) {
    val shader = remember { RuntimeShader(ShimmerShader) }
    val brush = remember(shader) { ShaderBrush(shader) }
    val transition = rememberInfiniteTransition(label = "gap shimmer")
    val phase = transition.animateFloat(0f, 1f, infiniteRepeatable(tween(ShimmerPeriodMillis, easing = LinearEasing)), label = "light")
    Canvas(modifier) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("segment", size.width * start, size.width * (end - start))
        shader.setFloatUniform("phase", phase.value)
        shader.setFloatUniform("strength", color.alpha * 0.12f)
        drawRect(brush)
    }
}
