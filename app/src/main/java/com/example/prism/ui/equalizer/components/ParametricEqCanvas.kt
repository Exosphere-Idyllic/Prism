package com.example.prism.ui.equalizer.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prism.domain.model.equalizer.EqBand
import com.example.prism.player.effects.BiquadCoefficients
import com.example.prism.ui.theme.AppAccent
import com.example.prism.ui.theme.AppAccentSoft
import com.example.prism.ui.theme.AppSurface
import com.example.prism.ui.theme.AppSurface2
import com.example.prism.ui.theme.AppTextPrimary
import com.example.prism.ui.theme.AppTextSecondary
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private const val MIN_FREQ = 20f
private const val MAX_FREQ = 20_000f
private const val MIN_DB = -15f
private const val MAX_DB = 15f
private const val SAMPLE_RATE = 44_100

/**
 * Converts frequency in Hz to normalized X (0f..1f) along logarithmic scale.
 */
fun freqToNormalizedX(freqHz: Float): Float {
    val logMin = log10(MIN_FREQ)
    val logMax = log10(MAX_FREQ)
    val clamped = freqHz.coerceIn(MIN_FREQ, MAX_FREQ)
    return ((log10(clamped) - logMin) / (logMax - logMin)).coerceIn(0f, 1f)
}

/**
 * Converts normalized X (0f..1f) to frequency in Hz along logarithmic scale.
 */
fun normalizedXToFreq(normX: Float): Float {
    val logMin = log10(MIN_FREQ)
    val logMax = log10(MAX_FREQ)
    val clamped = normX.coerceIn(0f, 1f)
    val logVal = logMin + clamped * (logMax - logMin)
    return 10.0.pow(logVal.toDouble()).toFloat().coerceIn(MIN_FREQ, MAX_FREQ)
}

/**
 * Converts dB gain to normalized Y (0f..1f) where 0f is top (+15 dB) and 1f is bottom (-15 dB).
 */
fun dbToNormalizedY(gainDb: Float): Float {
    val clamped = gainDb.coerceIn(MIN_DB, MAX_DB)
    return (1f - (clamped - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0f, 1f)
}

/**
 * Converts normalized Y (0f..1f) to dB gain.
 */
fun normalizedYToDb(normY: Float): Float {
    val clamped = normY.coerceIn(0f, 1f)
    return (MAX_DB - clamped * (MAX_DB - MIN_DB)).coerceIn(MIN_DB, MAX_DB)
}

@Composable
fun ParametricEqCanvas(
    bands: List<EqBand>,
    preampDb: Float,
    selectedBandId: Int?,
    enabled: Boolean,
    onSelectBand: (Int) -> Unit,
    onBandNodeDrag: (bandId: Int, newFreqHz: Float, newGainDb: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Precalculate biquad coefficients for enabled bands
    val biquads = remember(bands) {
        bands.filter { it.enabled }.map { band ->
            BiquadCoefficients.calculate(
                type = band.type,
                frequencyHz = band.frequencyHz,
                sampleRate = SAMPLE_RATE,
                gainDb = band.gainDb,
                q = band.q,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppSurface),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .pointerInput(enabled, bands) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { tapOffset ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        if (width <= 0 || height <= 0) return@detectTapGestures

                        // Find closest band node within tap radius (48dp)
                        var closestBand: EqBand? = null
                        var minDistanceSq = Float.MAX_VALUE
                        val hitRadiusSq = (40.dp.toPx()) * (40.dp.toPx())

                        bands.forEach { band ->
                            val nodeX = freqToNormalizedX(band.frequencyHz) * width
                            val nodeY = dbToNormalizedY(band.gainDb) * height
                            val dx = tapOffset.x - nodeX
                            val dy = tapOffset.y - nodeY
                            val distSq = dx * dx + dy * dy
                            if (distSq < hitRadiusSq && distSq < minDistanceSq) {
                                minDistanceSq = distSq
                                closestBand = band
                            }
                        }

                        closestBand?.let { onSelectBand(it.id) }
                    }
                }
                .pointerInput(enabled, bands, selectedBandId) {
                    if (!enabled) return@pointerInput
                    var draggedBandId: Int? = null

                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            if (width <= 0 || height <= 0) return@detectDragGestures

                            // If already selected, check if touching it first
                            val currentSelected = bands.find { it.id == selectedBandId }
                            if (currentSelected != null) {
                                val nodeX = freqToNormalizedX(currentSelected.frequencyHz) * width
                                val nodeY = dbToNormalizedY(currentSelected.gainDb) * height
                                val dx = startOffset.x - nodeX
                                val dy = startOffset.y - nodeY
                                if (dx * dx + dy * dy < (40.dp.toPx()) * (40.dp.toPx())) {
                                    draggedBandId = currentSelected.id
                                    return@detectDragGestures
                                }
                            }

                            // Otherwise find closest node
                            var closestBand: EqBand? = null
                            var minDistanceSq = Float.MAX_VALUE
                            val hitRadiusSq = (44.dp.toPx()) * (44.dp.toPx())

                            bands.forEach { band ->
                                val nodeX = freqToNormalizedX(band.frequencyHz) * width
                                val nodeY = dbToNormalizedY(band.gainDb) * height
                                val dx = startOffset.x - nodeX
                                val dy = startOffset.y - nodeY
                                val distSq = dx * dx + dy * dy
                                if (distSq < hitRadiusSq && distSq < minDistanceSq) {
                                    minDistanceSq = distSq
                                    closestBand = band
                                }
                            }

                            draggedBandId = closestBand?.id
                            draggedBandId?.let { onSelectBand(it) }
                        },
                        onDrag = { change, _ ->
                            val id = draggedBandId ?: return@detectDragGestures
                            change.consume()
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            if (width <= 0 || height <= 0) return@detectDragGestures

                            val normX = (change.position.x / width).coerceIn(0f, 1f)
                            val normY = (change.position.y / height).coerceIn(0f, 1f)

                            val newFreq = normalizedXToFreq(normX)
                            val newGain = normalizedYToDb(normY)
                            onBandNodeDrag(id, newFreq, newGain)
                        },
                        onDragEnd = { draggedBandId = null },
                        onDragCancel = { draggedBandId = null },
                    )
                }
        ) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@Canvas

            // ── Grid Lines ──────────────────────────────────────────────────
            // 0 dB center reference line
            val zeroY = dbToNormalizedY(0f) * height
            drawLine(
                color = Color.White.copy(alpha = 0.15f),
                start = Offset(0f, zeroY),
                end = Offset(width, zeroY),
                strokeWidth = 1.5.dp.toPx(),
            )

            // +10 dB and -10 dB reference lines
            val plus10Y = dbToNormalizedY(10f) * height
            val minus10Y = dbToNormalizedY(-10f) * height
            drawLine(
                color = Color.White.copy(alpha = 0.05f),
                start = Offset(0f, plus10Y),
                end = Offset(width, plus10Y),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = Color.White.copy(alpha = 0.05f),
                start = Offset(0f, minus10Y),
                end = Offset(width, minus10Y),
                strokeWidth = 1.dp.toPx(),
            )

            // Frequency vertical guides: 100Hz, 1kHz, 10kHz
            val guideFreqs = floatArrayOf(100f, 1_000f, 10_000f)
            guideFreqs.forEach { freq ->
                val x = freqToNormalizedX(freq) * width
                drawLine(
                    color = Color.White.copy(alpha = 0.06f),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            // ── Frequency Response Curve ────────────────────────────────────
            val curveSteps = 120
            val curvePath = Path()
            val fillPath = Path()

            var isFirst = true
            for (i in 0..curveSteps) {
                val normX = i.toFloat() / curveSteps.toFloat()
                val freq = normalizedXToFreq(normX)

                // Sum magnitude responses in dB of all biquads + preamp
                var totalGainDb = if (enabled) preampDb else 0f
                if (enabled) {
                    for (biquad in biquads) {
                        totalGainDb += biquad.magnitudeDb(freq, SAMPLE_RATE)
                    }
                }

                val x = normX * width
                val y = dbToNormalizedY(totalGainDb) * height

                if (isFirst) {
                    curvePath.moveTo(x, y)
                    fillPath.moveTo(x, zeroY)
                    fillPath.lineTo(x, y)
                    isFirst = false
                } else {
                    curvePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(width, zeroY)
            fillPath.close()

            // Fill area under curve
            val fillBrush = Brush.verticalGradient(
                colors = listOf(
                    (if (enabled) AppAccent else AppTextSecondary).copy(alpha = 0.25f),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = height,
            )
            drawPath(path = fillPath, brush = fillBrush)

            // Draw response line
            val curveColor = if (enabled) AppAccentSoft else AppTextSecondary.copy(alpha = 0.5f)
            drawPath(
                path = curvePath,
                color = curveColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            // ── Interactive Band Nodes ──────────────────────────────────────
            bands.forEach { band ->
                val isSelected = band.id == selectedBandId
                val nodeX = freqToNormalizedX(band.frequencyHz) * width
                val nodeY = dbToNormalizedY(band.gainDb) * height

                val nodeCenter = Offset(nodeX, nodeY)
                val nodeRadius = if (isSelected) 10.dp.toPx() else 7.dp.toPx()

                // Stem down to 0 dB line for selected or active node
                if (isSelected) {
                    drawLine(
                        color = AppAccent.copy(alpha = 0.4f),
                        start = Offset(nodeX, zeroY),
                        end = nodeCenter,
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }

                // Node outer glow / halo if selected
                if (isSelected) {
                    drawCircle(
                        color = AppAccent.copy(alpha = 0.35f),
                        radius = nodeRadius + 5.dp.toPx(),
                        center = nodeCenter,
                    )
                }

                // Node circle
                drawCircle(
                    color = when {
                        !enabled -> AppSurface2
                        isSelected -> AppAccent
                        else -> Color.White
                    },
                    radius = nodeRadius,
                    center = nodeCenter,
                )

                // Inner dot
                drawCircle(
                    color = if (isSelected) Color.White else AppSurface,
                    radius = nodeRadius * 0.45f,
                    center = nodeCenter,
                )
            }
        }

        // dB labels on left edge
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = "+15",
                color = AppTextSecondary.copy(alpha = 0.5f),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Text(
                text = "0 dB",
                color = AppTextSecondary.copy(alpha = 0.5f),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Text(
                text = "-15",
                color = AppTextSecondary.copy(alpha = 0.5f),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        // Frequency guides labels on bottom edge
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = "100Hz",
                color = AppTextSecondary.copy(alpha = 0.4f),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 40.dp),
            )
            Text(
                text = "1kHz",
                color = AppTextSecondary.copy(alpha = 0.4f),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            Text(
                text = "10kHz",
                color = AppTextSecondary.copy(alpha = 0.4f),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 40.dp),
            )
        }
    }
}
