package xyz.mpv.rex.feature.webshare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.mpv.rex.domain.media.model.Video
import xyz.mpv.rex.utils.media.MediaFormatter
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebShareSheet(
  videos: List<Video> = emptyList(),
  files: List<File> = emptyList(),
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val shareState by WebShareManager.state.collectAsState()
  var copied by remember { mutableStateOf(false) }

  // 1. Prepare shareable files on initial composition
  LaunchedEffect(videos, files) {
    val shareables = mutableListOf<WebShareServer.ShareableFile>()

    for (video in videos) {
      val f = File(video.path)
      if (f.exists()) {
        shareables.add(
          WebShareServer.ShareableFile(
            id = video.id.toString(),
            file = f,
            displayName = video.displayName,
            durationFormatted = MediaFormatter.formatDuration(video.duration)
          )
        )
      }
    }

    for (file in files) {
      if (file.exists()) {
        shareables.add(
          WebShareServer.ShareableFile(
            id = file.name.hashCode().toString(),
            file = file,
            displayName = file.name
          )
        )
      }
    }

    if (shareables.isNotEmpty()) {
      WebShareManager.startSharing(context, shareables)
    }
  }

  // Periodic network refresh while sheet is open
  LaunchedEffect(Unit) {
    while (true) {
      kotlinx.coroutines.delay(2000L)
      WebShareManager.refreshNetworkState(context)
    }
  }

  val totalSize = remember(shareState.files) {
    shareState.files.sumOf { it.file.length() }
  }
  val totalSizeFormatted = remember(totalSize) {
    MediaFormatter.formatFileSize(totalSize)
  }

  val qrBitmap = remember(shareState.serverUrl) {
    shareState.serverUrl?.let { url ->
      try {
        QrCodeGenerator.generateQrBitmap(url, sizePx = 400).asImageBitmap()
      } catch (e: Exception) {
        null
      }
    }
  }

  ModalBottomSheet(
    onDismissRequest = {
      WebShareManager.stopSharing(context)
      onDismiss()
    },
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp)
        .padding(bottom = 32.dp)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "Web Share",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "${shareState.files.size} ${if (shareState.files.size == 1) "file" else "files"} ($totalSizeFormatted)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        IconButton(
          onClick = {
            WebShareManager.stopSharing(context)
            onDismiss()
          }
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Close",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Network Status Pill / Banner
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = when (shareState.networkType) {
          WebShareManager.NetworkType.HOTSPOT -> MaterialTheme.colorScheme.primaryContainer
          WebShareManager.NetworkType.WIFI -> MaterialTheme.colorScheme.secondaryContainer
          WebShareManager.NetworkType.NONE -> MaterialTheme.colorScheme.errorContainer
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            imageVector = when (shareState.networkType) {
              WebShareManager.NetworkType.HOTSPOT -> Icons.Default.Language
              WebShareManager.NetworkType.WIFI -> Icons.Default.Language
              WebShareManager.NetworkType.NONE -> Icons.Default.Warning
            },
            contentDescription = null,
            tint = when (shareState.networkType) {
              WebShareManager.NetworkType.HOTSPOT -> MaterialTheme.colorScheme.onPrimaryContainer
              WebShareManager.NetworkType.WIFI -> MaterialTheme.colorScheme.onSecondaryContainer
              WebShareManager.NetworkType.NONE -> MaterialTheme.colorScheme.onErrorContainer
            },
            modifier = Modifier.size(22.dp),
          )

          Column {
            Text(
              text = when (shareState.networkType) {
                WebShareManager.NetworkType.HOTSPOT -> "Sharing via Mobile Hotspot"
                WebShareManager.NetworkType.WIFI -> "Sharing via Local Wi-Fi"
                WebShareManager.NetworkType.NONE -> "No Wi-Fi or Hotspot active"
              },
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
              color = when (shareState.networkType) {
                WebShareManager.NetworkType.HOTSPOT -> MaterialTheme.colorScheme.onPrimaryContainer
                WebShareManager.NetworkType.WIFI -> MaterialTheme.colorScheme.onSecondaryContainer
                WebShareManager.NetworkType.NONE -> MaterialTheme.colorScheme.onErrorContainer
              },
            )
            Text(
              text = when (shareState.networkType) {
                WebShareManager.NetworkType.HOTSPOT -> "Receiver connects to your Hotspot"
                WebShareManager.NetworkType.WIFI -> "Receiver connects to the same Wi-Fi network"
                WebShareManager.NetworkType.NONE -> "Turn on Hotspot or connect to Wi-Fi to share"
              },
              style = MaterialTheme.typography.bodySmall,
              color = when (shareState.networkType) {
                WebShareManager.NetworkType.HOTSPOT -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                WebShareManager.NetworkType.WIFI -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                WebShareManager.NetworkType.NONE -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
              },
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // QR Code Card
      if (qrBitmap != null) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color.White,
          shadowElevation = 4.dp,
          modifier = Modifier.size(200.dp),
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(12.dp),
          ) {
            Image(
              bitmap = qrBitmap,
              contentDescription = "Scan QR Code",
              modifier = Modifier.size(176.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // URL Display & Copy Card
      shareState.serverUrl?.let { url ->
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Browser Address",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }

            IconButton(
              onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Web Share Link", url))
                copied = true
                Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
              }
            ) {
              Icon(
                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = "Copy Link",
                tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Step-by-step guidance
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        InstructionRow(step = "1", text = "Connect receiver device to this Wi-Fi or Hotspot")
        InstructionRow(step = "2", text = "Scan QR Code with Camera or open the link in any browser")
        InstructionRow(step = "3", text = "Tap Download or Play in browser (zero app required)")
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Stop Sharing Button
      Button(
        onClick = {
          WebShareManager.stopSharing(context)
          onDismiss()
        },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
      ) {
        Text(
          text = "Stop Sharing",
          fontWeight = FontWeight.SemiBold,
          fontSize = 15.sp,
        )
      }
    }
  }
}

@Composable
private fun InstructionRow(step: String, text: String) {
  Row(
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Surface(
      shape = RoundedCornerShape(8.dp),
      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
      modifier = Modifier.size(22.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(
          text = step,
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
      }
    }

    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.weight(1f),
    )
  }
}
