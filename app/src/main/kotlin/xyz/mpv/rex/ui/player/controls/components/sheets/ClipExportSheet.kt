package xyz.mpv.rex.ui.player.controls.components.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `is`.xyz.mpv.FastClipper.ClipMode
import xyz.mpv.rex.R
import xyz.mpv.rex.presentation.components.PlayerSheet
import xyz.mpv.rex.ui.theme.spacing
import java.util.Locale

@Composable
fun ClipExportSheet(
  startSec: Double,
  endSec: Double,
  onExport: (ClipMode) -> Unit,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val startFormatted = formatHighPrecisionTime(startSec)
  val endFormatted = formatHighPrecisionTime(endSec)
  val durationSec = String.format(Locale.US, "%.2fs", maxOf(0.0, endSec - startSec))

  PlayerSheet(onDismissRequest = onDismissRequest) {
    Column(
      modifier = modifier
        .verticalScroll(rememberScrollState())
        .padding(horizontal = MaterialTheme.spacing.medium, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = Icons.Default.ContentCut,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.clip_export_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      // Interval & Duration Summary Badge
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column {
            Text(
              text = "$startFormatted → $endFormatted",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface
            )
          }

          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Text(
              text = stringResource(R.string.clip_export_duration, durationSec),
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(2.dp))

      // Option 1: Fast (Lossless Copy)
      ExportOptionCard(
        icon = Icons.Default.Bolt,
        title = stringResource(R.string.clip_export_fast_title),
        tag = stringResource(R.string.clip_export_fast_tag),
        description = stringResource(R.string.clip_export_fast_desc),
        isPrimary = true,
        onClick = {
          onExport(ClipMode.FAST_COPY)
          onDismissRequest()
        }
      )

      // Option 2: Exact Frame (Hardware Transcode)
      ExportOptionCard(
        icon = Icons.Default.CenterFocusStrong,
        title = stringResource(R.string.clip_export_exact_title),
        tag = stringResource(R.string.clip_export_exact_tag),
        description = stringResource(R.string.clip_export_exact_desc),
        isPrimary = false,
        onClick = {
          onExport(ClipMode.FRAME_ACCURATE)
          onDismissRequest()
        }
      )

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
private fun ExportOptionCard(
  icon: ImageVector,
  title: String,
  tag: String,
  description: String,
  isPrimary: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isPrimary) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
      } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
      }
    ),
    border = BorderStroke(
      width = 1.dp,
      color = if (isPrimary) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
      } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
      }
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(42.dp)
          .background(
            color = if (isPrimary) {
              MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            } else {
              MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
            },
            shape = CircleShape
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
          modifier = Modifier.size(24.dp)
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isPrimary) {
              MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
              MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
            }
          ) {
            Text(
              text = tag,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Medium,
              color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

private fun formatHighPrecisionTime(seconds: Double): String {
  val totalSec = seconds.toInt()
  val h = totalSec / 3600
  val m = (totalSec % 3600) / 60
  val s = totalSec % 60
  val ms = ((seconds - totalSec) * 1000).toInt().coerceIn(0, 999)
  return if (h > 0) {
    String.format(Locale.US, "%d:%02d:%02d.%03d", h, m, s, ms)
  } else {
    String.format(Locale.US, "%02d:%02d.%03d", m, s, ms)
  }
}
