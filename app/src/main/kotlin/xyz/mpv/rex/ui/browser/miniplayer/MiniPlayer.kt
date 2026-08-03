package xyz.mpv.rex.ui.browser.miniplayer

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.ui.player.MediaPlaybackService
import xyz.mpv.rex.utils.media.MediaFormatter

@Composable
fun MiniPlayer(
  stateManager: MiniPlayerStateManager,
  modifier: Modifier = Modifier,
) {
  val state by stateManager.state.collectAsState()
  val context = LocalContext.current

  AnimatedVisibility(
    visible = state.isPlaybackActive,
    enter = slideInVertically(initialOffsetY = { it }),
    exit = slideOutVertically(targetOffsetY = { it }),
    modifier = modifier,
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp)
        .clip(RoundedCornerShape(16.dp)),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 8.dp,
      shadowElevation = 8.dp,
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        // Top slim progress indicator
        val progress = if (state.durationMs > 0) {
          (state.currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
          0f
        }

        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier
            .fillMaxWidth()
            .height(3.dp),
          color = MaterialTheme.colorScheme.primary,
          trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable {
              stateManager.openPlayer(context)
            }
            .padding(horizontal = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          // Thumbnail or placeholder icon
          Box(
            modifier = Modifier
              .size(48.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            val thumbnail = state.thumbnail
            if (thumbnail != null && !thumbnail.isRecycled) {
              Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp),
              )
            } else {
              Icon(
                imageVector = Icons.Filled.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
              )
            }
          }

          Spacer(modifier = Modifier.width(12.dp))

          // Media Title and Position / Duration
          Column(
            modifier = Modifier.weight(1f),
          ) {
            Text(
              text = state.title.ifBlank { "Playing Media" },
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              color = MaterialTheme.colorScheme.onSurface,
            )
            val timeText = if (state.durationMs > 0) {
              "${MediaFormatter.formatDuration(state.currentPositionMs)} / ${MediaFormatter.formatDuration(state.durationMs)}"
            } else {
              state.artist.ifBlank { "Background Playback" }
            }
            Text(
              text = timeText,
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          // Play / Pause Button
          IconButton(
            onClick = {
              stateManager.togglePlayPause()
            },
          ) {
            Icon(
              imageVector = if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
              contentDescription = if (state.isPaused) "Play" else "Pause",
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }

          // Close / Stop Button
          IconButton(
            onClick = {
              runCatching {
                context.stopService(Intent(context, MediaPlaybackService::class.java))
              }
              stateManager.clearState()
            },
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = "Close Mini Player",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}
