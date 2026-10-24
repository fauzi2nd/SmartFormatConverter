package org.roruku.smartformatconverter.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.roruku.smartformatconverter.R

/**
 * Branded follow-through to the short, OS-controlled Android 12+ splash.
 * Android's native splash cannot display a full-screen poster or custom text;
 * this Compose layer supplies the Pixel Shift orbit, depth and tagline.
 */
@Composable
fun PixelShiftSplash() {
    val transition = rememberInfiniteTransition(label = "pixelShiftOrbit")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "orbitPhase"
    )
    Box(
        modifier = Modifier.fillMaxSize().background(PixelBackground),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.43f)
            val radius = size.width.coerceAtMost(size.height) * 0.38f
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(PixelBlue.copy(alpha = 0.21f), PixelViolet.copy(alpha = 0.09f), Color.Transparent),
                    center = center,
                    radius = radius * 1.8f
                )
            )
            drawArc(
                color = PixelCyan.copy(alpha = 0.48f),
                startAngle = phase * 360f - 65f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = PixelViolet.copy(alpha = 0.45f),
                startAngle = phase * 360f + 125f,
                sweepAngle = 72f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.85f, center.y - radius * 0.85f),
                size = Size(radius * 1.7f, radius * 1.7f),
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
            )
            repeat(32) { index ->
                val angle = index * 2.39996 + phase * PI * 2.0
                val distance = radius * (0.53 + (index % 7) / 13.0)
                val point = Offset(
                    center.x + (cos(angle) * distance).toFloat(),
                    center.y + (sin(angle) * distance).toFloat()
                )
                drawCircle(
                    color = if (index % 3 == 0) PixelViolet else PixelCyan,
                    radius = (if (index % 5 == 0) 2.2f else 1.2f).dp.toPx(),
                    center = point,
                    alpha = 0.22f + (index % 5) * 0.11f
                )
            }
            val horizon = size.height * 0.80f
            repeat(7) { index ->
                val y = horizon + (index * index) * 7.dp.toPx()
                if (y < size.height) {
                    drawLine(
                        color = PixelBlue.copy(alpha = 0.065f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(178.dp)
                    .graphicsLayer { translationY = (sin(phase * PI * 2.0) * 5.dp.toPx()).toFloat() }
                    .clip(RoundedCornerShape(44.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF112747), Color(0xFF182455), Color(0xFF231B4A))
                        )
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(PixelCyan.copy(alpha = 0.8f), PixelViolet.copy(alpha = 0.75f))),
                        RoundedCornerShape(44.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(38.dp))
            Text(
                text = "Smart Format",
                style = TextStyle(
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4f).sp,
                    color = PixelText
                ),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Converter",
                style = TextStyle(
                    brush = Brush.horizontalGradient(listOf(PixelCyan, PixelBlue, PixelViolet)),
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4f).sp
                ),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(13.dp))
            Text(
                text = "Transform Media the Smart Way.",
                style = TextStyle(fontSize = 12.sp, letterSpacing = 0.8.sp, color = PixelMuted),
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = "IMAGE  •  VIDEO  •  AUDIO",
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 38.dp),
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
                color = PixelMuted.copy(alpha = 0.8f)
            )
        )
    }
}
