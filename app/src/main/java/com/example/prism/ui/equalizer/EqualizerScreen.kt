package com.example.prism.ui.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.prism.R
import com.example.prism.domain.model.equalizer.EqBand
import com.example.prism.domain.model.equalizer.EqualizerMode
import com.example.prism.ui.theme.AppAccent
import com.example.prism.ui.theme.AppAccentBg
import com.example.prism.ui.theme.AppAccentSoft
import com.example.prism.ui.theme.AppBgBottom
import com.example.prism.ui.theme.AppBgTop
import com.example.prism.ui.theme.AppSurface
import com.example.prism.ui.theme.AppSurface2
import com.example.prism.ui.theme.AppTextPrimary
import com.example.prism.ui.theme.AppTextSecondary
import com.example.prism.ui.theme.AppTrackBg
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EqualizerViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    EqualizerContent(
        uiState = uiState,
        onBack = onBack,
        onToggleEnabled = { viewModel.toggleEnabled() },
        onModeChange = { viewModel.setMode(it) },
        onPreampChange = { viewModel.setPreamp(it) },
        onBandGainChange = { id, gain -> viewModel.setBandGain(id, gain) },
        onSelectPreset = { viewModel.selectPreset(it) },
        onReset = { viewModel.reset() },
        modifier = modifier,
    )
}

@Composable
fun EqualizerContent(
    uiState: EqualizerUiState,
    onBack: () -> Unit,
    onToggleEnabled: () -> Unit,
    onModeChange: (EqualizerMode) -> Unit,
    onPreampChange: (Float) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onSelectPreset: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(AppBgTop, AppBgBottom)
                    )
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Top Bar ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AppSurface2),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = AppTextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.equalizer_title).uppercase(Locale.getDefault()),
                    color = AppTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                )

                IconButton(
                    onClick = onReset,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AppSurface2),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.eq_reset),
                        tint = AppTextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ── Master Switch Card ─────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppSurface)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.eq_enable),
                            color = AppTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (uiState.enabled) "Active (DSP engine running)" else "Disabled (Bypassed)",
                            color = if (uiState.enabled) AppAccentSoft else AppTextSecondary,
                            fontSize = 12.sp,
                        )
                    }

                    Switch(
                        checked = uiState.enabled,
                        onCheckedChange = { onToggleEnabled() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppAccent,
                            uncheckedThumbColor = AppTextSecondary,
                            uncheckedTrackColor = AppSurface2,
                            uncheckedBorderColor = Color.Transparent,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Presets Selector (Dropdown + Chips) ────────────────────────
                PresetSelectorRow(
                    selectedPreset = uiState.selectedPreset,
                    availablePresets = uiState.availablePresets,
                    enabled = uiState.enabled,
                    onSelectPreset = onSelectPreset,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ── Preamp Slider Card ─────────────────────────────────────────
                PreampCard(
                    preampDb = uiState.preampDb,
                    enabled = uiState.enabled,
                    onPreampChange = onPreampChange,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── 10 ISO Bands Sliders ───────────────────────────────────────
                Text(
                    text = "BANDS (dB)",
                    color = AppTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp),
                )

                IsoBandsRow(
                    bands = uiState.bands,
                    enabled = uiState.enabled,
                    onBandGainChange = onBandGainChange,
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun PresetSelectorRow(
    selectedPreset: String?,
    availablePresets: List<String>,
    enabled: Boolean,
    onSelectPreset: (String) -> Unit,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        availablePresets.forEach { preset ->
            val isSelected = preset == selectedPreset
            val bg = if (isSelected) AppAccent else AppSurface
            val textCol = if (isSelected) Color.White else AppTextSecondary

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bg)
                    .clickable(enabled = enabled) { onSelectPreset(preset) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = preset,
                    color = if (enabled) textCol else textCol.copy(alpha = 0.4f),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
fun PreampCard(
    preampDb: Float,
    enabled: Boolean,
    onPreampChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppSurface)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.eq_preamp),
                color = AppTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = String.format(Locale.getDefault(), "%+.1f dB", preampDb),
                color = if (enabled) AppAccentSoft else AppTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Slider(
            value = preampDb,
            onValueChange = onPreampChange,
            valueRange = -12f..12f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = AppAccent,
                activeTrackColor = AppAccent,
                inactiveTrackColor = AppTrackBg,
                disabledThumbColor = AppSurface2,
                disabledActiveTrackColor = AppSurface2,
                disabledInactiveTrackColor = AppTrackBg.copy(alpha = 0.5f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun IsoBandsRow(
    bands: List<EqBand>,
    enabled: Boolean,
    onBandGainChange: (Int, Float) -> Unit,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppSurface)
            .padding(vertical = 16.dp, horizontal = 12.dp)
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bands.forEach { band ->
            VerticalBandSlider(
                band = band,
                enabled = enabled,
                onGainChange = { onBandGainChange(band.id, it) },
            )
        }
    }
}

@Composable
fun VerticalBandSlider(
    band: EqBand,
    enabled: Boolean,
    onGainChange: (Float) -> Unit,
) {
    val freqLabel = formatFrequency(band.frequencyHz)
    val gainLabel = String.format(Locale.getDefault(), "%+.0f", band.gainDb)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(48.dp),
    ) {
        Text(
            text = "${gainLabel}dB",
            color = if (enabled) AppAccentSoft else AppTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Vertical Slider container
        Box(
            modifier = Modifier
                .height(180.dp)
                .width(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Horizontal reference line for 0 dB
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppTextSecondary.copy(alpha = 0.25f)),
            )

            // Rotated slider to make it vertical (-90 deg)
            Slider(
                value = band.gainDb,
                onValueChange = onGainChange,
                valueRange = -12f..12f,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = AppAccent,
                    activeTrackColor = AppAccent,
                    inactiveTrackColor = AppTrackBg,
                    disabledThumbColor = AppSurface2,
                    disabledActiveTrackColor = AppSurface2,
                    disabledInactiveTrackColor = AppTrackBg.copy(alpha = 0.5f),
                ),
                modifier = Modifier
                    .width(180.dp)
                    .rotate(-90f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = freqLabel,
            color = if (enabled) AppTextPrimary else AppTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatFrequency(freqHz: Float): String {
    return if (freqHz >= 1000f) {
        val kHz = freqHz / 1000f
        if (kHz == kHz.toInt().toFloat()) {
            "${kHz.toInt()}k"
        } else {
            String.format(Locale.getDefault(), "%.1fk", kHz)
        }
    } else {
        "${freqHz.toInt()}Hz"
    }
}
