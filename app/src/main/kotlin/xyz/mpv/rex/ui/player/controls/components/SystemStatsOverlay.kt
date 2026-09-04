package xyz.mpv.rex.ui.player.controls.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.R
import xyz.mpv.rex.utils.system.CustomStatsSnapshot
import xyz.mpv.rex.utils.system.SystemStatsOps

@Composable
fun SystemStatsOverlay(
  visible: Boolean,
  modifier: Modifier = Modifier,
) {
  if (!visible) return

  val context = LocalContext.current.applicationContext

  val statsFlow = remember(context) {
    SystemStatsOps.getCustomStatsFlow(
      context = context,
    )
  }

  val statsState = statsFlow.collectAsState(
    initial = remember {
      CustomStatsSnapshot(
        fileName = "--",
        renderContext = "--",
        video = "--",
        audio = "--",
        batteryPercentText = "--%",
        batteryRateText = "Unknown",
        batteryWattsText = "-- W",
        batteryTempText = "--°C",
        hdrActive = "--",
        decoderEfficiencyText = "Unknown",
        thermalStateText = "Normal",
        peakTempText = "--°C",
        tempRiseText = "+0.0°C",
      )
    }
  )
  val stats = statsState.value

  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(),
    exit = fadeOut(),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 520.dp)
        .alpha(0.88f),
      verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
      val baseStyle = MaterialTheme.typography.bodySmall.copy(
        color = Color.White,
        fontSize = 8.sp,
        lineHeight = 10.sp,
        shadow = Shadow(
          color = Color.Black,
          offset = androidx.compose.ui.geometry.Offset(1.2f, 1.2f),
          blurRadius = 3f,
        ),
      )
      val headerStyle = baseStyle.copy(
        fontWeight = FontWeight.Bold,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        fontSize = 8.5.sp,
      )
      val labelStyle = baseStyle.copy(fontWeight = FontWeight.Bold)
      val valueStyle = baseStyle

      OutlinedText(stringResource(R.string.diagnostics_playback_decoder_header), style = headerStyle)
      OutlinedLabeled("File", stats.fileName, labelStyle, valueStyle)
      OutlinedLabeled(
        "Decoder & VO",
        "${stats.renderContext} | ${stats.video} | Eff: ${stats.decoderEfficiencyText}",
        labelStyle,
        valueStyle,
      )
      OutlinedLabeled("Audio", "${stats.audio} | HDR: ${stats.hdrActive}", labelStyle, valueStyle)

      Spacer(modifier = Modifier.height(2.dp))
      OutlinedText(stringResource(R.string.diagnostics_power_thermals_header), style = headerStyle)
      OutlinedLabeled(
        "Battery",
        "${stats.batteryPercentText} | ${stats.batteryWattsText} | Rate: ${stats.batteryRateText}",
        labelStyle,
        valueStyle,
      )
      OutlinedLabeled(
        "Temp",
        "${stats.batteryTempText} (Peak: ${stats.peakTempText} | Rise: ${stats.tempRiseText})",
        labelStyle,
        valueStyle,
      )
      OutlinedLabeled("Thermal", stats.thermalStateText, labelStyle, valueStyle)
    }
  }
}

@Composable
fun OutlinedText(
  text: String,
  style: androidx.compose.ui.text.TextStyle,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier) {
    Text(
      text = text,
      style = style.copy(
        color = Color.Black,
        shadow = null,
        drawStyle = Stroke(
          width = with(LocalDensity.current) { 1.2.dp.toPx() },
          join = StrokeJoin.Round,
        ),
      ),
    )
    Text(
      text = text,
      style = style,
    )
  }
}

@Composable
fun OutlinedLabeled(
  label: String,
  value: String,
  labelStyle: androidx.compose.ui.text.TextStyle,
  valueStyle: androidx.compose.ui.text.TextStyle,
  modifier: Modifier = Modifier,
) {
  val annotated = buildAnnotatedString {
    withStyle(SpanStyle(fontWeight = labelStyle.fontWeight)) {
      append("$label: ")
    }
    withStyle(SpanStyle(fontWeight = valueStyle.fontWeight)) {
      append(value)
    }
  }
  Box(modifier = modifier) {
    Text(
      text = annotated,
      style = labelStyle.copy(
        color = Color.Black,
        shadow = null,
        drawStyle = Stroke(
          width = with(LocalDensity.current) { 1.2.dp.toPx() },
          join = StrokeJoin.Round,
        ),
      ),
    )
    Text(
      text = annotated,
      style = labelStyle,
    )
  }
}
