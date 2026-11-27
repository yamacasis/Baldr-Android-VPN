package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ConnectionState
import com.example.ui.theme.ConnectedGreen
import com.example.ui.theme.ConnectedGreenBezel
import com.example.ui.theme.ConnectedGreenDarkRing
import com.example.ui.theme.ConnectedGreenDeep
import com.example.ui.theme.ConnectedGreenLight
import com.example.ui.theme.ConnectingOrange
import com.example.ui.theme.ConnectingOrangeDarkRing
import com.example.ui.theme.ConnectingOrangeDeep
import com.example.ui.theme.ConnectingOrangeLight
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralError
import com.example.ui.theme.CoralErrorDeep
import com.example.ui.theme.CoralErrorLight
import com.example.ui.theme.IdleBlueCore
import com.example.ui.theme.IdleBlueDeep
import com.example.ui.theme.IdleBlueLight
import com.example.ui.theme.IdleBlueRing
import com.example.ui.theme.LapisAzure
import com.example.ui.theme.NavyShadow
import com.example.ui.theme.NavyShadowSpot
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tactile 3D-layered VPN power button with navy-blue ambient shadow, sculpted outer bezel ring,
 * glassmorphic specular highlight, compact mode for connected side-by-side layout, and distinct state palettes:
 * - Connected: vibrant emerald green orb with dark forest-green outer ring & emerald ripples (no yellow).
 * - Connecting / Reconnecting / Disconnecting: rich tangerine orange orb with sweeping orbital arcs.
 * - Idle / Disconnected: vivid royal sapphire orb with navy bezel & crisp sky-blue outer dial.
 * - Firewall locked / Error: deep coral-crimson orb.
 */
@Composable
fun ConnectPowerButton(
    connectionState: ConnectionState,
    isLowBatteryActive: Boolean,
    contentDescription: String,
    onToggleConnection: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val isConnected = connectionState == ConnectionState.CONNECTED
    val isConnecting = connectionState == ConnectionState.CONNECTING ||
        connectionState == ConnectionState.RECONNECTING ||
        connectionState == ConnectionState.DISCONNECTING
    val isErrorOrLocked = connectionState == ConnectionState.FIREWALL_LOCKED ||
        connectionState == ConnectionState.ERROR
    val isActiveState = isConnected || isConnecting

    val colorSpec = spring<Color>(dampingRatio = 0.82f, stiffness = 280f)
    val sizeSpec = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.80f, stiffness = 320f)

    val canvasSize by animateDpAsState(
        targetValue = if (compact) 114.dp else 196.dp,
        animationSpec = sizeSpec,
        label = "orb_canvas_size"
    )
    val orbSize by animateDpAsState(
        targetValue = if (compact) 74.dp else 120.dp,
        animationSpec = sizeSpec,
        label = "orb_button_size"
    )
    val iconSize by animateDpAsState(
        targetValue = if (compact) 32.dp else 50.dp,
        animationSpec = sizeSpec,
        label = "orb_icon_size"
    )
    val scaleFactor = if (compact) 0.62f else 1f

    // Orb 3D gradient stops
    val orbTopColor by animateColorAsState(
        targetValue = when {
            isConnected -> ConnectedGreenLight
            isConnecting -> ConnectingOrangeLight
            isErrorOrLocked -> CoralErrorLight
            else -> IdleBlueLight
        },
        animationSpec = colorSpec,
        label = "orb_top_color"
    )
    val orbMidColor by animateColorAsState(
        targetValue = when {
            isConnected -> ConnectedGreen
            isConnecting -> ConnectingOrange
            isErrorOrLocked -> CoralError
            else -> IdleBlueCore
        },
        animationSpec = colorSpec,
        label = "orb_mid_color"
    )
    val orbBottomColor by animateColorAsState(
        targetValue = when {
            isConnected -> ConnectedGreenDeep
            isConnecting -> ConnectingOrangeDeep
            isErrorOrLocked -> CoralErrorDeep
            else -> IdleBlueDeep
        },
        animationSpec = colorSpec,
        label = "orb_bottom_color"
    )

    // Outer bezel / halo ring color
    val outerRingColor by animateColorAsState(
        targetValue = when {
            isConnected -> ConnectedGreenDarkRing
            isConnecting -> ConnectingOrangeDarkRing
            isErrorOrLocked -> CoralContainer
            else -> IdleBlueRing
        },
        animationSpec = colorSpec,
        label = "outer_ring_color"
    )
    val outerRingAccent by animateColorAsState(
        targetValue = when {
            isConnected -> ConnectedGreenBezel
            isConnecting -> ConnectingOrangeDeep
            isErrorOrLocked -> CoralErrorDeep
            else -> NavyShadowSpot
        },
        animationSpec = colorSpec,
        label = "outer_ring_accent"
    )
    val auraColor by animateColorAsState(
        targetValue = when {
            isConnected -> ConnectedGreen
            isConnecting -> ConnectingOrange
            isErrorOrLocked -> CoralError
            else -> LapisAzure
        },
        animationSpec = colorSpec,
        label = "aura_color"
    )

    val connectedStrength by animateFloatAsState(
        targetValue = if (isConnected) 1f else 0f,
        animationSpec = tween(420),
        label = "connected_strength"
    )
    val connectingStrength by animateFloatAsState(
        targetValue = if (isConnecting) 1f else 0f,
        animationSpec = tween(320),
        label = "connecting_strength"
    )

    val sweepAngle: Float
    val wave1: Float
    val wave2: Float
    val pulseScale: Float

    if (isActiveState && !isLowBatteryActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "connect_orb_fx")
        val pScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isConnecting) 1.06f else 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (isConnecting) 950 else 1700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orb_pulse_scale"
        )
        val sweep by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (isConnecting) 1500 else 3600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orb_ring_sweep"
        )
        val w1 by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orb_wave1"
        )
        pulseScale = pScale
        sweepAngle = sweep
        wave1 = w1
        wave2 = (w1 + 0.5f) % 1f
    } else {
        pulseScale = 1f
        sweepAngle = 0f
        wave1 = 0f
        wave2 = 0f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(vertical = if (compact) 0.dp else 4.dp)
    ) {
        Canvas(
            modifier = Modifier
                .size(canvasSize)
                .testTag("connected_visual_effect")
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val orbRadius = (orbSize / 2f).toPx()
            val ringStroke = (if (compact) 6.dp else 9.dp).toPx()
            val bezelRadius = orbRadius + ringStroke + (if (compact) 1.dp else 1.5.dp).toPx()
            val maxRadius = size.minDimension / 2f

            // 1. Soft, deep navy-blue ambient drop shadow beneath the orb
            val shadowCenter = Offset(center.x, center.y + (11.dp.toPx() * scaleFactor))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NavyShadow.copy(alpha = 0.68f),
                        NavyShadowSpot.copy(alpha = 0.36f),
                        Color.Transparent
                    ),
                    center = shadowCenter,
                    radius = (bezelRadius * 1.22f).coerceAtMost(maxRadius)
                ),
                radius = (bezelRadius * 1.22f).coerceAtMost(maxRadius),
                center = shadowCenter
            )

            // 2. State-specific atmospheric radial glow
            val glowAlpha = when {
                isConnected -> 0.28f
                isConnecting -> 0.30f
                isErrorOrLocked -> 0.22f
                else -> 0.16f
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        auraColor.copy(alpha = glowAlpha),
                        outerRingColor.copy(alpha = glowAlpha * 0.65f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = center
            )

            // 3. Idle precision compass ring ticks around the navy bezel
            val idleAlpha = (1f - connectedStrength - connectingStrength).coerceIn(0f, 1f)
            if (idleAlpha > 0.05f && !compact) {
                val tickInner = bezelRadius + 4.dp.toPx()
                val tickOuter = bezelRadius + 8.dp.toPx()
                for (i in 0 until 24) {
                    val angleRad = (i * 15.0) * PI / 180.0
                    val isCardinal = i % 6 == 0
                    val lenBoost = if (isCardinal) 3.dp.toPx() else 0f
                    val cosA = cos(angleRad).toFloat()
                    val sinA = sin(angleRad).toFloat()
                    drawLine(
                        color = LapisAzure.copy(alpha = (if (isCardinal) 0.55f else 0.24f) * idleAlpha),
                        start = Offset(center.x + tickInner * cosA, center.y + tickInner * sinA),
                        end = Offset(
                            center.x + (tickOuter + lenBoost) * cosA,
                            center.y + (tickOuter + lenBoost) * sinA
                        ),
                        strokeWidth = if (isCardinal) 2.2.dp.toPx() else 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 4. Connected expanding ripples + dark-green & emerald orbital arcs (zero yellow!)
            if (connectedStrength > 0.01f) {
                val r1 = bezelRadius + (maxRadius - bezelRadius) * wave1
                val a1 = (1f - wave1) * 0.46f * connectedStrength
                drawCircle(
                    color = ConnectedGreen.copy(alpha = a1),
                    radius = r1,
                    center = center,
                    style = Stroke(width = (if (compact) 1.6.dp else 2.2.dp).toPx())
                )

                val r2 = bezelRadius + (maxRadius - bezelRadius) * wave2
                val a2 = (1f - wave2) * 0.42f * connectedStrength
                drawCircle(
                    color = ConnectedGreenDarkRing.copy(alpha = a2),
                    radius = r2,
                    center = center,
                    style = Stroke(width = (if (compact) 1.8.dp else 2.5.dp).toPx())
                )

                val arcDiameter = (bezelRadius + (if (compact) 4.dp else 7.dp).toPx()) * 2f
                val arcTopLeft = Offset(
                    center.x - arcDiameter / 2f,
                    center.y - arcDiameter / 2f
                )
                drawArc(
                    color = ConnectedGreenDarkRing.copy(alpha = 0.92f * connectedStrength),
                    startAngle = sweepAngle,
                    sweepAngle = 82f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = Size(arcDiameter, arcDiameter),
                    style = Stroke(width = (if (compact) 2.4.dp else 3.2.dp).toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = ConnectedGreenLight.copy(alpha = 0.72f * connectedStrength),
                    startAngle = sweepAngle + 180f,
                    sweepAngle = 56f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = Size(arcDiameter, arcDiameter),
                    style = Stroke(width = (if (compact) 1.8.dp else 2.4.dp).toPx(), cap = StrokeCap.Round)
                )
            }

            // 5. Connecting rich orange pulsing halo & dual rotating arcs
            if (connectingStrength > 0.01f) {
                val rOrange = bezelRadius + (maxRadius - bezelRadius) * wave1
                val aOrange = (1f - wave1) * 0.44f * connectingStrength
                drawCircle(
                    color = ConnectingOrange.copy(alpha = aOrange),
                    radius = rOrange,
                    center = center,
                    style = Stroke(width = (if (compact) 1.8.dp else 2.4.dp).toPx())
                )

                val arcDiameter = (bezelRadius + (if (compact) 4.dp else 7.dp).toPx()) * 2f
                val arcTopLeft = Offset(
                    center.x - arcDiameter / 2f,
                    center.y - arcDiameter / 2f
                )
                drawArc(
                    color = ConnectingOrange.copy(alpha = 0.95f * connectingStrength),
                    startAngle = sweepAngle,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = Size(arcDiameter, arcDiameter),
                    style = Stroke(width = (if (compact) 2.5.dp else 3.4.dp).toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = ConnectingOrangeLight.copy(alpha = 0.78f * connectingStrength),
                    startAngle = sweepAngle + 195f,
                    sweepAngle = 64f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = Size(arcDiameter, arcDiameter),
                    style = Stroke(width = (if (compact) 1.8.dp else 2.4.dp).toPx(), cap = StrokeCap.Round)
                )
            }

            // 6. Sculpted Outer Bezel Housing Ring around the button
            val ringMidRadius = orbRadius + ringStroke / 2f + (if (compact) 1.dp else 1.5.dp).toPx()
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        outerRingColor,
                        outerRingAccent
                    ),
                    start = Offset(center.x - ringMidRadius, center.y - ringMidRadius),
                    end = Offset(center.x + ringMidRadius, center.y + ringMidRadius)
                ),
                radius = ringMidRadius,
                center = center,
                style = Stroke(width = ringStroke)
            )
            drawCircle(
                color = when {
                    isConnected -> ConnectedGreen.copy(alpha = 0.55f)
                    isConnecting -> ConnectingOrangeLight.copy(alpha = 0.62f)
                    isErrorOrLocked -> CoralErrorLight.copy(alpha = 0.45f)
                    else -> LapisAzure.copy(alpha = 0.42f)
                },
                radius = ringMidRadius + ringStroke / 2f,
                center = center,
                style = Stroke(width = (if (compact) 1.1.dp else 1.4.dp).toPx())
            )
        }

        // Main Tactile 3D Power Orb with deep navy drop shadow & glossy depth
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(orbSize)
                .scale(pulseScale)
                .shadow(
                    elevation = if (isConnected) 14.dp else 12.dp,
                    shape = CircleShape,
                    ambientColor = NavyShadow,
                    spotColor = NavyShadow
                )
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            orbTopColor,
                            orbMidColor,
                            orbBottomColor
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(260f, 320f)
                    )
                )
                .minimumInteractiveComponentSize()
                .clickable(onClick = onToggleConnection)
                .testTag("vpn_connect_button")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f
                val c = Offset(r, r)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.34f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(r * 0.68f, r * 0.52f),
                        radius = r * 0.85f
                    ),
                    radius = r,
                    center = c
                )

                drawCircle(
                    color = if (isConnected) {
                        ConnectedGreenDarkRing.copy(alpha = 0.72f)
                    } else {
                        Color.White.copy(alpha = 0.28f)
                    },
                    radius = r - 1.5.dp.toPx(),
                    center = c,
                    style = Stroke(width = (if (compact) 1.8.dp else 2.2.dp).toPx())
                )

                val inset = (if (compact) 5.dp else 7.dp).toPx()
                drawArc(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.45f),
                            Color.Transparent
                        )
                    ),
                    startAngle = 212f,
                    sweepAngle = 116f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2f, size.height - inset * 2f),
                    style = Stroke(width = (if (compact) 1.5.dp else 2.dp).toPx(), cap = StrokeCap.Round)
                )
            }

            if (isConnecting) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.22f),
                        strokeWidth = if (compact) 2.5.dp else 3.5.dp,
                        modifier = Modifier.size(if (compact) 38.dp else 58.dp)
                    )
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = contentDescription,
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(if (compact) 18.dp else 28.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = contentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
