@file:OptIn(ExperimentalFoundationApi::class)

package com.blinds.os

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * The liquid glass material.
 *
 * Each panel draws its own blurred slice of the wallpaper (aligned to where the panel sits on screen),
 * then adds tint, a lens-like edge brighten, a specular bloom and a rim light that points at the
 * configured light angle (and shifts as the phone tilts).
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LocalSettings.current.cornerRadius.dp),
    strength: Float = 1f,
    tint: Color? = null,
    tintAlpha: Float? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val s = LocalSettings.current
    val bd = LocalBackdrop.current
    val dark = LocalDark.current
    val density = LocalDensity.current

    var pos by remember { mutableStateOf(Offset.Zero) }
    val blurRadius = max(s.glassBlur, 0.5f)
    val marginPx = with(density) { (blurRadius * 2f + 8f).dp.roundToPx() }
    val tintColor = tint ?: Color(s.glassTintArgb)
    val baseAlpha = tintAlpha ?: ((if (dark) s.glassTint else s.glassTint * 1.9f + 0.1f) * strength)
    val bodyAlpha = baseAlpha.coerceIn(0f, 0.92f)
    val edge = s.glassEdge
    val lightAngle = s.glassLight + (if (s.motionLight) (bd?.tilt?.x ?: 0f) * 28f else 0f)

    Box(
        modifier
            .shadow(
                elevation = (22f * s.glassDepth * strength.coerceAtMost(1.5f)).dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.45f),
            )
            .onGloballyPositioned { pos = it.positionInRoot() }
            .clip(shape)
            .drawWithContent {
                drawContent()
                drawGlassRim(shape, lightAngle, edge)
            }
    ) {
        // 1. blurred copy of the wallpaper, shifted so it lines up with what is really behind this panel
        if (bd != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .layout { measurable, c ->
                        val p = measurable.measure(
                            Constraints.fixed(c.maxWidth + 2 * marginPx, c.maxHeight + 2 * marginPx)
                        )
                        layout(c.maxWidth, c.maxHeight) { p.place(-marginPx, -marginPx) }
                    }
                    .blur(blurRadius.dp, BlurredEdgeTreatment.Unbounded)
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    translate(left = marginPx - pos.x, top = marginPx - pos.y) {
                        drawWallpaper(bd, s, dark)
                    }
                }
            }
        }

        // 2. body tint, glass thickness and the lens-like brighter edge
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(tintColor.copy(alpha = bodyAlpha))
                    drawRect(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.10f * edge),
                                Color.Transparent,
                                Color.Black.copy(alpha = if (dark) 0.14f else 0.04f),
                            )
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            0f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to Color.White.copy(alpha = 0.10f * edge),
                            center = center,
                            radius = hypot(size.width, size.height) / 2f,
                        )
                    )
                }
        )

        content()
    }
}

/** Specular bloom + a rim light that is bright on the side facing the light and dim opposite. */
private fun ContentDrawScope.drawGlassRim(shape: Shape, angleDeg: Float, edge: Float) {
    if (edge <= 0.01f || size.width <= 0f || size.height <= 0f) return
    val a = Math.toRadians(angleDeg.toDouble())
    val dx = cos(a).toFloat()
    val dy = sin(a).toFloat()
    val half = hypot(size.width, size.height) / 2f
    val c = center
    val lit = Offset(c.x + dx * half, c.y + dy * half)
    val shade = Offset(c.x - dx * half, c.y - dy * half)

    drawRect(
        Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.26f * edge), Color.Transparent),
            center = Offset(c.x + dx * half * 0.55f, c.y + dy * half * 0.55f),
            radius = max(size.width, size.height) * 0.75f,
        )
    )

    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(
        outline = outline,
        brush = Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.95f * edge),
            0.30f to Color.White.copy(alpha = 0.12f * edge),
            0.70f to Color.White.copy(alpha = 0.12f * edge),
            1f to Color.White.copy(alpha = 0.60f * edge),
            start = lit,
            end = shade,
        ),
        style = Stroke(width = 2.4.dp.toPx()),
    )
}

// ---------------------------------------------------------------------------------------------
// Interaction helpers
// ---------------------------------------------------------------------------------------------

/** iOS-style springy press: shrinks while held, bounces back on release. */
fun Modifier.bouncy(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    pressedScale: Float = 0.92f,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val sc by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "bouncy",
    )
    this
        .graphicsLayer {
            scaleX = sc
            scaleY = sc
        }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}

@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    color: Color = LocalInk.current,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Unspecified,
    shadow: Boolean = false,
    spacing: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            textAlign = align,
            letterSpacing = spacing,
            shadow = if (shadow) Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 6f) else null,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    GlassSurface(
        modifier = modifier.bouncy(onClick = onClick),
        shape = RoundedCornerShape(50),
        strength = 0.9f,
        tint = if (accent) Color(s.accent) else null,
        tintAlpha = if (accent) 0.62f else null,
    ) {
        Txt(
            text = text,
            color = if (accent) Color.White else ink,
            size = 14.sp,
            weight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val s = LocalSettings.current
    val knob by animateDpAsState(
        targetValue = if (checked) 22.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "switch",
    )
    GlassSurface(
        modifier = Modifier
            .size(width = 52.dp, height = 30.dp)
            .bouncy(onClick = { onChange(!checked) }, pressedScale = 0.96f),
        shape = RoundedCornerShape(50),
        strength = 0.8f,
        tint = if (checked) Color(s.accent) else null,
        tintAlpha = if (checked) 0.75f else null,
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .offset(x = knob)
                .size(24.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .drawBehind { drawRect(Color.White) }
        )
    }
}

@Composable
fun GlassSlider(
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    val update by rememberUpdatedState(onChange)
    var widthPx by remember { mutableIntStateOf(1) }
    val frac = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    val thumb = 26.dp

    fun fromX(x: Float, w: Int): Float {
        val f = (x / w.coerceAtLeast(1)).coerceIn(0f, 1f)
        return range.start + f * (range.endInclusive - range.start)
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(range) { detectTapGestures { o -> update(fromX(o.x, size.width)) } }
            .pointerInput(range) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    update(fromX(change.position.x, size.width))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            drawRoundRect(
                color = ink.copy(alpha = 0.18f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
            )
            drawRoundRect(
                color = Color(s.accent),
                size = androidx.compose.ui.geometry.Size(size.width * frac, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
            )
        }
        val thumbX = with(LocalDensity.current) { ((widthPx - thumb.toPx()) * frac).toInt() }
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(thumbX, 0) }
                .size(thumb)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .drawBehind { drawRect(Color.White) }
        )
    }
}

@Composable
fun Dot(color: Color, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).drawBehind { drawRect(color) })
}
