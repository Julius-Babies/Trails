package es.jvbabi.trails.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val ColorFull     = Color(0xFF34C759)  // green  – 60-100 %
private val ColorMedium   = Color(0xFFFFCC00)  // yellow – 20-59 %
private val ColorLow      = Color(0xFFFF3B30)  // red    – 0-19 %
private val ColorShell    = Color(0x44000000)  // semi-transparent body background

/**
 * A borderless, vertical battery icon drawn with Canvas.
 *
 * @param modifier    Controls size. Recommended aspect ratio ≈ 1 : 2 (width : height).
 * @param percentage  Charge level in [0, 100]. Fill rises from the bottom.
 * @param isCharging  Shows a lightning bolt and switches fill to cyan with a pulse.
 * @param emptyColor  Tint of the unfilled body background.
 */
@Composable
fun BatteryIcon(
    modifier: Modifier = Modifier,
    percentage: Int = 80,
    isCharging: Boolean = false,
    emptyColor: Color = ColorShell,
) {
    val clampedPct = percentage.coerceIn(0, 100)

    // Pulse alpha only while charging
    val chargingAlpha by if (isCharging) {
        rememberInfiniteTransition(label = "charging_pulse").animateFloat(
            initialValue = 0.70f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(850, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "alpha_pulse",
        )
    } else {
        rememberInfiniteTransition(label = "idle").animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Restart,
            ),
            label = "idle_alpha",
        )
    }

    val fillColor = when {
        clampedPct >= 60 -> ColorFull
        clampedPct >= 20 -> ColorMedium
        else             -> ColorLow
    }

    androidx.compose.foundation.Canvas(
        // graphicsLayer is required for BlendMode ops to composite correctly
        modifier = modifier.graphicsLayer { alpha = 0.99f },
    ) {
        drawBattery(
            pct        = clampedPct / 100f,
            fillColor  = fillColor.copy(alpha = chargingAlpha),
            emptyColor = emptyColor,
            isCharging = isCharging,
        )
    }
}

private fun DrawScope.drawBattery(
    pct: Float,
    fillColor: Color,
    emptyColor: Color,
    isCharging: Boolean,
) {
    val w = size.width
    val h = size.height

    //
    //  ┌──────┐   ← cap (rounded pill, separated by a small gap)
    //  │ gap  │
    //  ╔══════╗   ─ bodyTop
    //  ║      ║
    //  ║ fill ║   ← fill rises from the bottom, clipped to body shape
    //  ║      ║
    //  ╚══════╝   ─ bodyBottom = h
    //
    val capHeightFraction = 0.07f   // cap is 7 % of total height
    val gapFraction       = 0.025f  // gap between cap and body

    val capH   = h * capHeightFraction
    val gap    = h * gapFraction
    val capW   = w * 0.42f                    // cap narrower than body
    val capX   = (w - capW) / 2f
    val capY   = 0f
    val capRadius = capH / 2f                 // fully pill-shaped cap

    val bodyTop    = capY + capH + gap
    val bodyBottom = h
    val bodyHeight = bodyBottom - bodyTop
    val bodyRadius = w * 0.38f               // very round corners (pill-ish)

    // ── Combined mask path: cap + body as one shape ─────────────────────────
    // Both the cap and the body share the same clip so the fill can flow
    // through both seamlessly if percentage is high enough.
    val maskPath = Path().apply {
        // Cap
        addRoundRect(
            RoundRect(
                rect         = Rect(Offset(capX, capY), Size(capW, capH)),
                cornerRadius = CornerRadius(capRadius, capRadius),
            )
        )
        // Body
        addRoundRect(
            RoundRect(
                rect         = Rect(Offset(0f, bodyTop), Size(w, bodyHeight)),
                cornerRadius = CornerRadius(bodyRadius, bodyRadius),
            )
        )
    }

    // ── 1. Background (empty shell) ─────────────────────────────────────────
    clipPath(maskPath) {
        drawRect(emptyColor)
    }

    // ── 2. Fill – rises from the bottom, scaled across the full height ─────
    // The total fillable range is capY (0f) to bodyBottom (h), which includes
    // the cap and the gap. At pct == 1 the fill reaches the very top of the cap.
    if (pct > 0f) {
        val totalFillHeight = bodyBottom - capY   // h
        val fillTop         = bodyBottom - totalFillHeight * pct

        val fillRect = Path().apply {
            addRect(Rect(0f, fillTop, w, bodyBottom))
        }

        clipPath(maskPath) {
            val fillBrush = Brush.verticalGradient(
                colors = listOf(fillColor.copy(alpha = 0.80f), fillColor),
                startY = fillTop,
                endY   = bodyBottom,
            )
            drawPath(fillRect, fillBrush)
        }
    }

    if (isCharging) {
        drawLightningBolt(
            centerX    = w / 2f,
            centerY    = bodyTop + bodyHeight / 2f,
            boltHeight = bodyHeight * 0.4f,
        )
    }
}

private fun DrawScope.drawLightningBolt(
    centerX: Float,
    centerY: Float,
    boltHeight: Float,
) {
    val boltWidth = boltHeight * 1f

    // Simpler, tighter coordinates for a modern 6-point bolt
    val bolt = Path().apply {
        moveTo(centerX + boltWidth * 0.15f,  centerY - boltHeight * 0.45f) // Top tip
        lineTo(centerX - boltWidth * 0.30f,  centerY + boltHeight * 0.05f) // Outer left jog
        lineTo(centerX + boltWidth * 0.05f,  centerY + boltHeight * 0.05f) // Inner left corner
        lineTo(centerX - boltWidth * 0.15f,  centerY + boltHeight * 0.45f) // Bottom tip
        lineTo(centerX + boltWidth * 0.30f,  centerY - boltHeight * 0.05f) // Outer right jog
        lineTo(centerX - boltWidth * 0.05f,  centerY - boltHeight * 0.05f) // Inner right corner
        close()
    }

    // 1. Cut out the transparent halo.
    // Using StrokeJoin.Round ensures the cutout itself has soft corners.
    drawPath(
        path      = bolt,
        color     = Color.Black,
        style     = Stroke(
            width = boltHeight * 0.20f,
            join  = StrokeJoin.Round // KMP safe!
        ),
        blendMode = BlendMode.Clear,
    )

    // 2. Draw the solid white fill (which has sharp corners)
    drawPath(
        path  = bolt,
        color = Color.White
    )

    // 3. Draw a white stroke over the fill with StrokeJoin.Round.
    // This overlaps the sharp points of the fill and smoothly rounds the outside edges!
    drawPath(
        path  = bolt,
        color = Color.White,
        style = Stroke(
            width = boltHeight * 0.06f, // Amount of rounding
            join  = StrokeJoin.Round
        )
    )
}


@Preview
@Composable
private fun BatteryIconPreview() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(28.dp),
    ) {
        BatteryIcon(modifier = Modifier.size(36.dp, 72.dp), percentage = 100)
        BatteryIcon(modifier = Modifier.size(36.dp, 72.dp), percentage = 60)
        BatteryIcon(modifier = Modifier.size(36.dp, 72.dp), percentage = 35)
        BatteryIcon(modifier = Modifier.size(36.dp, 72.dp), percentage = 10)
        BatteryIcon(modifier = Modifier.size(36.dp, 72.dp), percentage = 55, isCharging = true)
        BatteryIcon(modifier = Modifier.size(36.dp, 72.dp), percentage = 0,  isCharging = true)
        // Larger size to verify scaling
        BatteryIcon(modifier = Modifier.size(56.dp, 112.dp), percentage = 75)
    }
}
