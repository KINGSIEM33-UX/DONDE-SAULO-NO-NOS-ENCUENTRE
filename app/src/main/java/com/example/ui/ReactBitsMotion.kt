package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================================
// REACT BITS MOTION SYSTEM FOR ADORA (Native Jetpack Compose 60/120fps)
// Curated ports tuned to Obsidian & Warm Ivory + Nocturnal Luxury aesthetics.
// ============================================================================

/**
 * React Bits — Split Text:
 * Staggered word-by-word rise & fade reveal for editorial screen headers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitTextReveal(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    staggerDelayMs: Int = 55,
    riseDistanceDp: Dp = 10.dp
) {
    val words = remember(text) { text.split(" ").filter { it.isNotEmpty() } }
    val risePx = with(LocalDensity.current) { riseDistanceDp.toPx() }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        words.forEachIndexed { index, word ->
            val progress = remember(text, index) { Animatable(0f) }
            LaunchedEffect(text, index) {
                delay((index * staggerDelayMs).toLong())
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
                )
            }
            Text(
                text = word,
                style = style,
                color = color,
                modifier = Modifier.graphicsLayer {
                    val p = progress.value
                    alpha = p
                    translationY = (1f - p) * risePx
                }
            )
        }
    }
}

/**
 * React Bits — Shiny Text:
 * Ultra-restrained metallic light sweep over editorial typography (brushed gold/champagne).
 */
@Composable
fun ShinyEditorialText(
    text: String,
    style: TextStyle,
    baseColor: Color,
    shineColor: Color = MaterialTheme.colorScheme.secondary,
    modifier: Modifier = Modifier,
    durationMs: Int = 3600
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ShinyEditorialText")
    val phase by infiniteTransition.animateFloat(
        initialValue = -0.45f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShinyTextPhase"
    )

    val brush = remember(phase, baseColor, shineColor) {
        val center = phase
        val band = 0.24f
        Brush.linearGradient(
            colorStops = arrayOf(
                (center - band).coerceIn(0f, 1f) to baseColor,
                center.coerceIn(0f, 1f) to shineColor,
                (center + band).coerceIn(0f, 1f) to baseColor
            ),
            start = Offset(0f, 0f),
            end = Offset(420f, 60f)
        )
    }

    Text(
        text = text,
        style = style.copy(brush = brush),
        modifier = modifier
    )
}

/**
 * React Bits — Count Up:
 * Smoothly interpolates integer metrics with tabular numerals (zero horizontal jitter).
 */
@Composable
fun CountUpText(
    targetValue: Int,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    durationMs: Int = 620
) {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(targetValue) {
        animatable.animateTo(
            targetValue = targetValue.toFloat(),
            animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing)
        )
    }

    Text(
        text = "$prefix${animatable.value.roundToInt()}$suffix",
        style = style.copy(fontFeatureSettings = "tnum"),
        color = color,
        modifier = modifier
    )
}

/**
 * React Bits — Animated Content / Animated List:
 * Subtle staggered vertical entrance (rise + fade) bounded to avoid sluggish tails.
 */
@Composable
fun AnimatedStaggeredItem(
    index: Int,
    modifier: Modifier = Modifier,
    staggerMs: Int = 45,
    riseDp: Dp = 14.dp,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val risePx = with(LocalDensity.current) { riseDp.toPx() }
    val clampedDelay = (index.coerceIn(0, 6) * staggerMs).toLong()

    LaunchedEffect(Unit) {
        if (clampedDelay > 0L) delay(clampedDelay)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = modifier.graphicsLayer {
            val p = progress.value
            alpha = p
            translationY = (1f - p) * risePx
        }
    ) {
        content()
    }
}

/**
 * React Bits — Spotlight Card (Mobile Touch-Flare Edition):
 * Editorial hairline card illuminated by a soft radial champagne/gold spotlight that
 * tracks passive touch contact and gently catches the 1dp border edge without consuming scroll.
 */
@Composable
fun SpotlightEditorialCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    spotlightColor: Color = MaterialTheme.colorScheme.secondary,
    spotlightIntensity: Float = 0.14f,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    var touchOffset by remember { mutableStateOf<Offset?>(null) }
    var isTouched by remember { mutableStateOf(false) }

    val glowAlpha by animateFloatAsState(
        targetValue = if (isTouched) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isTouched) 140 else 420,
            easing = FastOutSlowInEasing
        ),
        label = "SpotlightAlpha"
    )

    val spotlightModifier = Modifier
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touchOffset = down.position
                isTouched = true
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val first = event.changes.firstOrNull()
                    if (first != null && first.pressed) {
                        touchOffset = first.position
                    } else {
                        break
                    }
                } while (true)
                isTouched = false
            }
        }
        .drawWithContent {
            drawContent()
            val center = touchOffset
            if (center != null && glowAlpha > 0.005f) {
                val radius = size.maxDimension * 0.62f
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            spotlightColor.copy(alpha = spotlightIntensity * glowAlpha),
                            spotlightColor.copy(alpha = spotlightIntensity * 0.35f * glowAlpha),
                            Color.Transparent
                        ),
                        center = center,
                        radius = radius.coerceAtLeast(1f)
                    )
                )
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            spotlightColor.copy(alpha = 0.55f * glowAlpha),
                            Color.Transparent
                        ),
                        center = center,
                        radius = (radius * 0.72f).coerceAtLeast(1f)
                    ),
                    cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                    style = Stroke(width = 1.25.dp.toPx())
                )
            }
        }

    val border = BorderStroke(1.dp, borderColor)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.then(spotlightModifier),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = border,
            content = content
        )
    } else {
        Card(
            modifier = modifier.then(spotlightModifier),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = border,
            content = content
        )
    }
}

data class RubberSegmentOption(
    val id: String,
    val label: String,
    val activeColor: Color? = null,
    val activeContentColor: Color? = null
)

/**
 * React Bits Micro 2.0 — Rubber Segment:
 * Segmented control whose thumb glides with spring physics and subtle elastic stretch
 * across slots before settling cleanly into place.
 */
@Composable
fun RubberSegmentedBar(
    options: List<RubberSegmentOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    defaultThumbColor: Color = MaterialTheme.colorScheme.primary,
    defaultActiveTextColor: Color = MaterialTheme.colorScheme.onPrimary,
    idleTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    height: Dp = 40.dp,
    cornerRadius: Dp = 12.dp,
    inset: Dp = 3.dp
) {
    if (options.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val selectedIndex = options.indexOfFirst { it.id == selectedId }
    val hasSelection = selectedIndex >= 0

    // Track previous index to create React Bits' elastic stretch-and-settle feel
    var previousIndex by remember { mutableIntStateOf(selectedIndex.coerceAtLeast(0)) }
    val stretchAnim = remember { Animatable(0f) }

    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0 && selectedIndex != previousIndex) {
            val distance = abs(selectedIndex - previousIndex).coerceAtLeast(1)
            previousIndex = selectedIndex
            stretchAnim.snapTo((0.14f * distance).coerceAtMost(0.32f))
            stretchAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.65f,
                    stiffness = 460f
                )
            )
        } else if (selectedIndex >= 0) {
            previousIndex = selectedIndex
        }
    }

    val activeOption = options.getOrNull(selectedIndex)
    val thumbColor by animateColorAsState(
        targetValue = activeOption?.activeColor ?: defaultThumbColor,
        animationSpec = tween(220),
        label = "RubberThumbColor"
    )
    val thumbAlpha by animateFloatAsState(
        targetValue = if (hasSelection) 1f else 0f,
        animationSpec = tween(180),
        label = "RubberThumbAlpha"
    )

    val thumbRadius = (cornerRadius - inset).coerceAtLeast(4.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(cornerRadius))
            .background(trackColor)
            .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(cornerRadius))
            .padding(inset)
    ) {
        val slotWidth = maxWidth / options.size
        val targetOffset = slotWidth * selectedIndex.coerceAtLeast(0)
        val animatedOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = 0.74f,
                stiffness = 430f
            ),
            label = "RubberThumbOffset"
        )

        // Sliding rubber thumb with subtle elastic stretch while in transit
        if (thumbAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .offset(x = animatedOffset)
                    .width(slotWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        alpha = thumbAlpha
                        scaleX = 1f + stretchAnim.value
                        scaleY = 1f - (stretchAnim.value * 0.22f)
                    }
                    .clip(RoundedCornerShape(thumbRadius))
                    .background(thumbColor)
            )
        }

        // Foreground labels
        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { idx, opt ->
                val isSelected = idx == selectedIndex
                val labelColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        opt.activeContentColor ?: defaultActiveTextColor
                    } else {
                        idleTextColor
                    },
                    animationSpec = tween(180),
                    label = "RubberLabelColor_$idx"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(thumbRadius))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (opt.id != selectedId) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(opt.id)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = opt.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = labelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * React Bits Micro 2.0 — Voice Pill Waveform:
 * Live animated equalizer bars with organic attack/release motion for voice recording
 * and voice note playback bubbles.
 */
@Composable
fun VoicePillWaveform(
    isActive: Boolean,
    barColor: Color,
    inactiveBarColor: Color = barColor.copy(alpha = 0.32f),
    modifier: Modifier = Modifier,
    barCount: Int = 18,
    progress: Float? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VoicePillWave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "VoicePillPhase"
    )

    // Deterministic editorial silhouette envelope when idle
    val baseProfile = remember(barCount) {
        List(barCount) { i ->
            val norm = i.toFloat() / (barCount - 1).coerceAtLeast(1)
            val arch = sin(norm * PI).toFloat()
            val harmonic = 0.28f + 0.52f * arch + 0.18f * abs(sin(i * 1.37f))
            harmonic.coerceIn(0.18f, 0.96f)
        }
    }

    Canvas(modifier = modifier) {
        val count = barCount.coerceAtLeast(4)
        val gap = 3.dp.toPx()
        val totalGap = gap * (count - 1)
        val barWidth = ((size.width - totalGap) / count).coerceAtLeast(2.dp.toPx())
        val corner = barWidth / 2f

        for (i in 0 until count) {
            val heightFraction = if (isActive) {
                val wave1 = sin(phase + i * 0.62f)
                val wave2 = sin(phase * 1.7f - i * 0.41f)
                val env = 0.5f + 0.5f * ((wave1 * 0.65f + wave2 * 0.35f))
                (0.16f + env * 0.82f * baseProfile[i]).coerceIn(0.14f, 1f)
            } else {
                (baseProfile[i] * 0.55f).coerceIn(0.16f, 0.65f)
            }

            val barH = size.height * heightFraction
            val left = i * (barWidth + gap)
            val top = (size.height - barH) / 2f

            val filled = if (progress != null) {
                (i.toFloat() / count.toFloat()) <= progress
            } else {
                isActive
            }

            drawRoundRect(
                color = if (filled) barColor else inactiveBarColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(corner, corner)
            )
        }
    }
}

/**
 * React Bits Micro 2.0 — Pulse Heart:
 * Spring-physics heart button with concentric expanding halo burst & haptic feedback.
 */
@Composable
fun PulseHeartButton(
    liked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    activeColor: Color = Color(0xFFDC2626),
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: Dp = 20.dp
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val ringProgress = remember { Animatable(0f) }

    fun triggerPulse() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch {
            scale.snapTo(0.82f)
            scale.animateTo(
                targetValue = 1.26f,
                animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        scope.launch {
            ringProgress.snapTo(0f)
            ringProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
        }
        onToggle()
    }

    val tint by animateColorAsState(
        targetValue = if (liked) activeColor else inactiveColor,
        animationSpec = tween(180),
        label = "PulseHeartColor"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = ::triggerPulse
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier.size(iconSize + 12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (ringProgress.value in 0.01f..0.99f) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val p = ringProgress.value
                    drawCircle(
                        color = activeColor.copy(alpha = (1f - p) * 0.45f),
                        radius = (size.minDimension / 2f) * (0.45f + p * 0.65f),
                        style = Stroke(width = (2.dp.toPx() * (1f - p)).coerceAtLeast(0.5f))
                    )
                }
            }
            Icon(
                imageVector = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (liked) "Quitar me gusta" else "Me gusta",
                tint = tint,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
            )
        }
        if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = tint
            )
        }
    }
}

/**
 * React Bits Micro 2.0 — Spring Check:
 * Tactile circular checkbox with calibrated spring pop & haptic confirmation.
 */
@Composable
fun SpringCheckToggle(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    checkedColor: Color = MaterialTheme.colorScheme.primary,
    checkmarkColor: Color = MaterialTheme.colorScheme.onPrimary,
    size: Dp = 24.dp
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }

    val fillColor by animateColorAsState(
        targetValue = if (checked) checkedColor else Color.Transparent,
        animationSpec = tween(180),
        label = "SpringCheckFill"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) checkedColor else MaterialTheme.colorScheme.outline,
        animationSpec = tween(180),
        label = "SpringCheckBorder"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.58f,
            stiffness = 520f
        ),
        label = "SpringCheckIconScale"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(fillColor)
            .border(1.5.dp, borderColor, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch {
                    scale.snapTo(0.84f)
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.55f,
                            stiffness = 500f
                        )
                    )
                }
                onToggle()
            },
        contentAlignment = Alignment.Center
    ) {
        if (checkScale > 0.05f) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = if (checked) "Completado" else "Pendiente",
                tint = checkmarkColor,
                modifier = Modifier
                    .size(size * 0.62f)
                    .graphicsLayer {
                        scaleX = checkScale
                        scaleY = checkScale
                        alpha = checkScale.coerceIn(0f, 1f)
                    }
            )
        }
    }
}

/**
 * React Bits Micro 2.0 — Hold Button:
 * Press-and-hold confirmation button whose liquid fill with a scrolling wave meniscus
 * sweeps left-to-right while held, squashes gently (0.97x), snaps back fast (200ms) on
 * early release, and triggers haptic + callback on full completion.
 */
@Composable
fun HoldToConfirmButton(
    label: String,
    doneLabel: String,
    onHoldComplete: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    holdTimeMs: Int = 1400,
    releaseTimeMs: Int = 220,
    fillColor: Color = Color(0xFFB91C1C),
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    textColor: Color = Color(0xFFB91C1C),
    fillTextColor: Color = Color.White,
    cornerRadius: Dp = 12.dp,
    height: Dp = 46.dp
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val fillProgress = remember { Animatable(0f) }
    var isHolding by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }

    val pressScale by animateFloatAsState(
        targetValue = if (isHolding) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 500f),
        label = "HoldPressScale"
    )

    val infiniteWave = rememberInfiniteTransition(label = "HoldWave")
    val wavePhase by infiniteWave.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "HoldWavePhase"
    )

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, fillColor.copy(alpha = 0.42f), shape)
            .pointerInput(isDone, holdTimeMs) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (isDone) return@awaitEachGesture
                    isHolding = true
                    val fillJob = scope.launch {
                        val remaining = ((1f - fillProgress.value) * holdTimeMs).roundToInt().coerceAtLeast(100)
                        fillProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = remaining, easing = LinearEasing)
                        )
                        if (fillProgress.value >= 0.995f && !isDone) {
                            isDone = true
                            isHolding = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onHoldComplete()
                        }
                    }
                    waitForUpOrCancellation()
                    isHolding = false
                    if (!isDone) {
                        fillJob.cancel()
                        scope.launch {
                            fillProgress.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = releaseTimeMs, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Liquid fill canvas with subtle vertical meniscus wave on the leading edge
        Canvas(modifier = Modifier.fillMaxSize()) {
            val p = fillProgress.value
            if (p > 0.002f) {
                val edgeX = size.width * p
                val waveAmp = if (p < 0.99f) 4.dp.toPx() else 0f
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(edgeX, 0f)
                    val segments = 14
                    for (i in 1..segments) {
                        val y = size.height * (i.toFloat() / segments)
                        val dx = sin(wavePhase + (i.toFloat() / segments) * PI * 2.0).toFloat() * waveAmp
                        lineTo((edgeX + dx).coerceIn(0f, size.width), y)
                    }
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path = path, color = fillColor)
            }
        }

        val currentTextColor = if (fillProgress.value > 0.48f || isDone) fillTextColor else textColor

        AnimatedContent(
            targetState = isDone,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "HoldDoneLabel"
        ) { completed ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp)
            ) {
                if (!completed && icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = currentTextColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = if (completed) doneLabel else label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = currentTextColor
                )
            }
        }
    }
}

/**
 * React Bits Micro 2.0 — Thought Line:
 * Serene AI reasoning header with a breathing sparkle glyph (1.6s period),
 * sweeping ink shimmer across the label (1.8s), and a live elapsed clock.
 */
@Composable
fun ThoughtLineIndicator(
    label: String = "Pensando…",
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.secondary,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showTimer: Boolean = true
) {
    var elapsedTenths by remember(label) { mutableIntStateOf(0) }
    LaunchedEffect(label) {
        elapsedTenths = 0
        while (true) {
            delay(100L)
            elapsedTenths++
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ThoughtLine")
    val breathAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ThoughtBreath"
    )
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ThoughtShimmer"
    )

    val shimmerBrush = remember(shimmerPhase, textColor, accentColor) {
        val c = shimmerPhase
        val half = 0.25f
        Brush.linearGradient(
            colorStops = arrayOf(
                (c - half).coerceIn(0f, 1f) to textColor.copy(alpha = 0.72f),
                c.coerceIn(0f, 1f) to accentColor,
                (c + half).coerceIn(0f, 1f) to textColor.copy(alpha = 0.72f)
            ),
            start = Offset(0f, 0f),
            end = Offset(380f, 0f)
        )
    }

    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = accentColor.copy(alpha = breathAlpha),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                brush = shimmerBrush,
                fontWeight = FontWeight.Medium
            )
        )
        if (showTimer) {
            val secText = "${elapsedTenths / 10}.${elapsedTenths % 10}s"
            Text(
                text = secText,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                color = textColor.copy(alpha = 0.6f)
            )
        }
    }
}
