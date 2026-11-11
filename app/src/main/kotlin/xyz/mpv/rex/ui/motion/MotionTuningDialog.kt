package xyz.mpv.rex.ui.motion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.AppearancePreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.presentation.MotionStyle

/**
 * Temporary live tuning lab for navigation motion parameters.
 * Allows tweaking numbers on the fly without recompiling.
 */
@Composable
fun MotionTuningDialog(
  preferences: AppearancePreferences,
  onDismiss: () -> Unit,
) {
  val currentGlobalPreset by preferences.navigationMotionStyle.collectAsState()
  var selectedTabStyle by remember { mutableStateOf(currentGlobalPreset) }

  // Parallax collected
  val pEnterDur by preferences.motionParallaxEnterDuration.collectAsState()
  val pExitDur by preferences.motionParallaxExitDuration.collectAsState()
  val pFadeDur by preferences.motionParallaxFadeDuration.collectAsState()
  val pFadeDelay by preferences.motionParallaxFadeDelay.collectAsState()
  val pInScale by preferences.motionParallaxInitialScale.collectAsState()
  val pOutScale by preferences.motionParallaxRecedingScale.collectAsState()
  val pDivisor by preferences.motionParallaxDivisor.collectAsState()

  // Shared Z collected
  val zEnterDur by preferences.motionSharedZEnterDuration.collectAsState()
  val zExitDur by preferences.motionSharedZExitDuration.collectAsState()
  val zInScale by preferences.motionSharedZInitialScale.collectAsState()
  val zOutScale by preferences.motionSharedZTargetScale.collectAsState()

  // Slide collected
  val sDuration by preferences.motionSlideDuration.collectAsState()
  val sDivisor by preferences.motionSlideDivisor.collectAsState()

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Outlined.Edit,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "Motion Lab (Toys)",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "Live tune parameters without rebuilding",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 440.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        // Preset selector chips
        Text(
          text = "Preset to tune:",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          MotionStyle.selectablePresets.forEach { preset ->
            FilterChip(
              selected = selectedTabStyle == preset,
              onClick = {
                selectedTabStyle = preset
                preferences.navigationMotionStyle.set(preset)
              },
              label = {
                Text(
                  text = when (preset) {
                    MotionStyle.ExpressiveParallax -> "Parallax"
                    MotionStyle.SharedZ -> "Shared-Z"
                    MotionStyle.Slide -> "Slide"
                    else -> preset.name
                  },
                  style = MaterialTheme.typography.labelSmall,
                )
              },
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTabStyle) {
          MotionStyle.ExpressiveParallax, MotionStyle.Default -> {
            TuningSlider(
              label = "Enter Duration",
              valueText = "$pEnterDur ms",
              value = pEnterDur.toFloat(),
              onValueChange = {
                val v = it.toInt()
                RexMotion.tuning.parallaxEnterDuration = v
                preferences.motionParallaxEnterDuration.set(v)
              },
              valueRange = 150f..700f,
            )

            TuningSlider(
              label = "Exit Duration",
              valueText = "$pExitDur ms",
              value = pExitDur.toFloat(),
              onValueChange = {
                val v = it.toInt()
                RexMotion.tuning.parallaxExitDuration = v
                preferences.motionParallaxExitDuration.set(v)
              },
              valueRange = 150f..700f,
            )

            TuningSlider(
              label = "Incoming Scale",
              valueText = String.format(Locale.US, "%.2f", pInScale),
              value = pInScale,
              onValueChange = {
                val v = Math.round(it * 100f) / 100f
                RexMotion.tuning.parallaxInitialScale = v
                preferences.motionParallaxInitialScale.set(v)
              },
              valueRange = 0.75f..1.00f,
            )

            TuningSlider(
              label = "Receding Scale",
              valueText = String.format(Locale.US, "%.2f", pOutScale),
              value = pOutScale,
              onValueChange = {
                val v = Math.round(it * 100f) / 100f
                RexMotion.tuning.parallaxRecedingScale = v
                preferences.motionParallaxRecedingScale.set(v)
              },
              valueRange = 0.80f..1.00f,
            )

            TuningSlider(
              label = "Background Parallax Travel",
              valueText = "Width / $pDivisor (${(100 / pDivisor)}%)",
              value = pDivisor.toFloat(),
              onValueChange = {
                val v = it.toInt().coerceIn(2, 8)
                RexMotion.tuning.parallaxDivisor = v
                preferences.motionParallaxDivisor.set(v)
              },
              valueRange = 2f..8f,
              steps = 5,
            )

            TuningSlider(
              label = "Fade Duration",
              valueText = "$pFadeDur ms",
              value = pFadeDur.toFloat(),
              onValueChange = {
                val v = it.toInt()
                RexMotion.tuning.parallaxFadeDuration = v
                preferences.motionParallaxFadeDuration.set(v)
              },
              valueRange = 100f..500f,
            )

            TuningSlider(
              label = "Fade Delay",
              valueText = "$pFadeDelay ms",
              value = pFadeDelay.toFloat(),
              onValueChange = {
                val v = it.toInt()
                RexMotion.tuning.parallaxFadeDelay = v
                preferences.motionParallaxFadeDelay.set(v)
              },
              valueRange = 0f..150f,
            )
          }

          MotionStyle.SharedZ -> {
            TuningSlider(
              label = "Enter Duration",
              valueText = "$zEnterDur ms",
              value = zEnterDur.toFloat(),
              onValueChange = {
                val v = it.toInt()
                RexMotion.tuning.sharedZEnterDuration = v
                preferences.motionSharedZEnterDuration.set(v)
              },
              valueRange = 150f..600f,
            )

            TuningSlider(
              label = "Exit Duration",
              valueText = "$zExitDur ms",
              value = zExitDur.toFloat(),
              onValueChange = {
                val v = it.toInt()
                RexMotion.tuning.sharedZExitDuration = v
                preferences.motionSharedZExitDuration.set(v)
              },
              valueRange = 150f..600f,
            )

            TuningSlider(
              label = "Incoming Zoom Start Scale",
              valueText = String.format(Locale.US, "%.2f", zInScale),
              value = zInScale,
              onValueChange = {
                val v = Math.round(it * 100f) / 100f
                RexMotion.tuning.sharedZInitialScale = v
                preferences.motionSharedZInitialScale.set(v)
              },
              valueRange = 0.70f..1.00f,
            )

            TuningSlider(
              label = "Exit Zoom Overshoot Scale",
              valueText = String.format(Locale.US, "%.2f", zOutScale),
              value = zOutScale,
              onValueChange = {
                val v = Math.round(it * 100f) / 100f
                RexMotion.tuning.sharedZTargetScale = v
                preferences.motionSharedZTargetScale.set(v)
              },
              valueRange = 1.00f..1.25f,
            )
          }

          MotionStyle.Slide -> {
            TuningSlider(
              label = "Slide Duration",
              valueText = "$sDuration ms",
              value = sDuration.toFloat(),
              onValueChange = {
                val v = it.toInt()
                RexMotion.tuning.slideDuration = v
                preferences.motionSlideDuration.set(v)
              },
              valueRange = 100f..500f,
            )

            TuningSlider(
              label = "Slide Distance",
              valueText = when (sDivisor) {
                1 -> "Full Width (100%)"
                2 -> "Half Width (50%)"
                3 -> "One Third (33%)"
                4 -> "Quarter (25%)"
                else -> "Width / $sDivisor"
              },
              value = sDivisor.toFloat(),
              onValueChange = {
                val v = it.toInt().coerceIn(1, 4)
                RexMotion.tuning.slideDivisor = v
                preferences.motionSlideDivisor.set(v)
              },
              valueRange = 1f..4f,
              steps = 2,
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
          onClick = { preferences.resetMotionTuning(selectedTabStyle) },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(
            imageVector = Icons.Outlined.Refresh,
            contentDescription = null,
            modifier = Modifier.height(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(text = "Reset This Preset to Defaults")
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(text = "Done")
      }
    },
    shape = RoundedCornerShape(24.dp),
  )
}

@Composable
private fun TuningSlider(
  label: String,
  valueText: String,
  value: Float,
  onValueChange: (Float) -> Unit,
  valueRange: ClosedFloatingPointRange<Float>,
  steps: Int = 0,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = valueText,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
      )
    }
    Slider(
      value = value.coerceIn(valueRange.start, valueRange.endInclusive),
      onValueChange = onValueChange,
      valueRange = valueRange,
      steps = steps,
    )
  }
}
