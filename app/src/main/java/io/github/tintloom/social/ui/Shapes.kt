@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.tintloom.social.ui

import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import coil3.compose.AsyncImage

/** Scales any android Path to fill the box, optionally spinning it. */
private fun fitPath(path: android.graphics.Path, size: Size, rotation: Float): Outline {
    val bounds = RectF()
    path.computeBounds(bounds, true)
    val m = Matrix()
    m.setRectToRect(bounds, RectF(0f, 0f, size.width, size.height), Matrix.ScaleToFit.FILL)
    if (rotation != 0f) m.postRotate(rotation, size.width / 2f, size.height / 2f)
    path.transform(m)
    return Outline.Generic(path.asComposePath())
}

/** A Material 3 Expressive polygon (cookie, clover, burst…) as a Compose shape. */
class PolygonShape(private val polygon: RoundedPolygon, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        fitPath(polygon.toPath(android.graphics.Path()), size, rotation)
}

/** Smoothly morphs between two polygons as [progress] goes 0 → 1. */
class MorphShape(private val morph: Morph, private val progress: Float, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        fitPath(morph.toPath(progress, android.graphics.Path()), size, rotation)
}

/** Remembers a morph between two shapes, driven by an animated [target] (0 or 1). */
@Composable
fun rememberMorphShape(
    from: RoundedPolygon,
    to: RoundedPolygon,
    target: Float,
    rotateBy: Float = 0f,
): Shape {
    val morph = remember(from, to) { Morph(from, to) }
    val progress by animateFloatAsState(
        target,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "morph",
    )
    return MorphShape(morph, progress.coerceIn(0f, 1f), rotateBy * progress)
}

/** Slow, endless spin, like the shapes on Pixel's expressive screens. */
@Composable
fun rememberSpin(periodMs: Int = 24_000): Float {
    val t = rememberInfiniteTransition(label = "spin")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(periodMs, easing = LinearEasing)), label = "angle")
    return angle
}

/** Gentle breathing scale used behind hero icons. */
@Composable
fun rememberBreath(): Float {
    val t = rememberInfiniteTransition(label = "breath")
    val s by t.animateFloat(0.94f, 1.04f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "s")
    return s
}

/** A profile picture clipped to a slowly turning 12-sided cookie. */
@Composable
fun CookieAvatar(url: String?, size: Dp, modifier: Modifier = Modifier, spin: Boolean = true) {
    val angle = if (spin) rememberSpin() else 0f
    val shape = remember(angle) { PolygonShape(MaterialShapes.Cookie12Sided, angle) }
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        }
    }
}

/** An icon sitting on a turning, breathing expressive shape. */
@Composable
fun ShapeIcon(
    icon: ImageVector,
    size: Dp,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val angle = rememberSpin(30_000)
    val breath = rememberBreath()
    val shape = remember(angle, polygon) { PolygonShape(polygon, angle) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size * breath)
                .clip(shape)
                .background(container),
        )
        Icon(icon, null, Modifier.size(size * 0.45f), tint = content)
    }
}
