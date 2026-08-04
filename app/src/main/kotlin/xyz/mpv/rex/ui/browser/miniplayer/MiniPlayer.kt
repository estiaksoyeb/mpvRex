package xyz.mpv.rex.ui.browser.miniplayer

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import xyz.mpv.rex.ui.player.MediaPlaybackService
import xyz.mpv.rex.ui.player.RepeatMode
import xyz.mpv.rex.utils.media.MediaFormatter
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
  stateManager: MiniPlayerStateManager,
  modifier: Modifier = Modifier,
) {
  val state by stateManager.state.collectAsState()
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val offsetX = remember { Animatable(0f) }
  val expansionFraction = remember { Animatable(0f) }
  var contentWidth by remember { mutableFloatStateOf(1f) }

  val fraction = expansionFraction.value.coerceIn(0f, 1f)

  // Sync state expansion state if triggered externally
  LaunchedEffect(state.isExpanded) {
    val target = if (state.isExpanded) 1f else 0f
    if (expansionFraction.value != target) {
      expansionFraction.animateTo(target, spring())
    }
  }

  // Intercept back button when expanded to collapse sheet
  BackHandler(enabled = fraction > 0.5f) {
    coroutineScope.launch {
      expansionFraction.animateTo(0f, spring())
      stateManager.setExpanded(false)
    }
  }

  AnimatedVisibility(
    visible = state.isPlaybackActive,
    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
    exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }) + fadeOut(),
    modifier = modifier,
  ) {
    val animatedHeight = androidx.compose.ui.unit.lerp(83.dp, 420.dp, fraction)

    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .height(animatedHeight)
        .padding(horizontal = 8.dp)
        .clip(RoundedCornerShape(
          topStart = 20.dp,
          topEnd = 20.dp,
          bottomStart = androidx.compose.ui.unit.lerp(16.dp, 20.dp, fraction),
          bottomEnd = androidx.compose.ui.unit.lerp(16.dp, 20.dp, fraction)
        )),
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      tonalElevation = 8.dp,
      shadowElevation = 8.dp,
    ) {
      Box(modifier = Modifier.fillMaxSize()) {

        val compactAlpha = (1f - fraction * 3f).coerceIn(0f, 1f)

        // ──────────────────────────────────────────────────────────────
        // Drag Pill Bar — collapsed state (fades out as panel expands)
        // ──────────────────────────────────────────────────────────────
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .align(Alignment.TopCenter)
            .graphicsLayer { alpha = compactAlpha }  // visible when collapsed, fades as expanded header appears
            .pointerInput(Unit) {
              detectVerticalDragGestures(
                onDragEnd = {
                  coroutineScope.launch {
                    if (expansionFraction.value > 0.35f) {
                      expansionFraction.animateTo(1f, spring())
                      stateManager.setExpanded(true)
                    } else {
                      expansionFraction.animateTo(0f, spring())
                      stateManager.setExpanded(false)
                    }
                  }
                },
                onVerticalDrag = { change, dragAmount ->
                  change.consume()
                  coroutineScope.launch {
                    val delta = -dragAmount / 350f
                    val newFraction = (expansionFraction.value + delta).coerceIn(0f, 1f)
                    expansionFraction.snapTo(newFraction)
                  }
                }
              )
            },
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier = Modifier
              .width(36.dp)
              .height(4.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
          )
        }

        // ──────────────────────────────────────────────────────────────
        // Drag Pill Bar — expanded state (fades in as panel expands)
        // ──────────────────────────────────────────────────────────────
        val expandedPillAlpha = ((fraction - 0.25f) * 2.5f).coerceIn(0f, 1f)
        if (expandedPillAlpha > 0f) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(20.dp)
              .align(Alignment.TopCenter)
              .zIndex(1f)
              .graphicsLayer { alpha = expandedPillAlpha }
              .pointerInput(Unit) {
                detectVerticalDragGestures(
                  onDragEnd = {
                    coroutineScope.launch {
                      if (expansionFraction.value > 0.35f) {
                        expansionFraction.animateTo(1f, spring())
                        stateManager.setExpanded(true)
                      } else {
                        expansionFraction.animateTo(0f, spring())
                        stateManager.setExpanded(false)
                      }
                    }
                  },
                  onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    coroutineScope.launch {
                      val delta = -dragAmount / 350f
                      val newFraction = (expansionFraction.value + delta).coerceIn(0f, 1f)
                      expansionFraction.snapTo(newFraction)
                    }
                  }
                )
              },
            contentAlignment = Alignment.Center,
          ) {
            Box(
              modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
            )
          }
        }

        // ──────────────────────────────────────────────────────────────
        // Expanded Header (Collapse Button, NOW PLAYING, Close)
        // ──────────────────────────────────────────────────────────────
        val headerAlpha = ((fraction - 0.25f) * 2.5f).coerceIn(0f, 1f)
        if (headerAlpha > 0f) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 16.dp, start = 12.dp, end = 12.dp)
              .graphicsLayer { alpha = headerAlpha }
              .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            IconButton(
              onClick = {
                coroutineScope.launch {
                  expansionFraction.animateTo(0f, spring())
                  stateManager.setExpanded(false)
                }
              },
            ) {
              Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Collapse Mini Player",
                tint = MaterialTheme.colorScheme.onSurface,
              )
            }

            Text(
              text = "NOW PLAYING",
              style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold,
              ),
              color = MaterialTheme.colorScheme.primary,
            )

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

        // ──────────────────────────────────────────────────────────────
        // Collapsed content: swipe left/right to skip, click to open player
        // Progress bar at bottom with 4dp margin; no top/bottom padding on content
        // Layout: 47dp content + weight(~14dp) + 4dp margin + 3dp progress = 68dp
        // ──────────────────────────────────────────────────────────────
        if (compactAlpha > 0f) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 18.dp)  // clears the 18dp pill zone; no overlap = no gesture conflict
              .graphicsLayer { alpha = compactAlpha },
          ) {
            // Sliding content row (Thumbnail + Title/Artist + play/close buttons)
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(47.dp)
                .clipToBounds()
                .onSizeChanged { contentWidth = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(state.hasNext, state.hasPrevious) {
                  detectHorizontalDragGestures(
                    onDragEnd = {
                      coroutineScope.launch {
                        val threshold = contentWidth * 0.25f
                        val currentOffset = offsetX.value

                        if (currentOffset < -threshold && state.hasNext) {
                          // Complete slide left → next track
                          offsetX.animateTo(-contentWidth, tween(180))
                          stateManager.playNext()
                          offsetX.snapTo(0f)
                        } else if (currentOffset > threshold && state.hasPrevious) {
                          // Complete slide right → previous track
                          offsetX.animateTo(contentWidth, tween(180))
                          stateManager.playPrevious()
                          offsetX.snapTo(0f)
                        } else {
                          // Snap back if threshold not reached
                          offsetX.animateTo(0f, spring())
                        }
                      }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                      change.consume()
                      coroutineScope.launch {
                        val proposed = offsetX.value + dragAmount
                        val clamped = when {
                          proposed < 0 -> if (state.hasNext) proposed else 0f
                          proposed > 0 -> if (state.hasPrevious) proposed else 0f
                          else -> proposed
                        }
                        offsetX.snapTo(clamped)
                      }
                    }
                  )
                }
                .clickable { stateManager.openPlayer(context) },
            ) {
              val currentDx = offsetX.value.roundToInt()

              // Next track preview (slides in from right when swiping left)
              if (offsetX.value < 0 && state.hasNext) {
                val nextDx = (contentWidth + offsetX.value).roundToInt()
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .offset { IntOffset(nextDx, 0) }
                    .padding(start = 18.dp, end = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Box(
                    modifier = Modifier
                      .size(44.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                  ) {
                    val nextThumb = state.nextThumbnail
                    if (nextThumb != null && !nextThumb.isRecycled) {
                      Image(
                        bitmap = nextThumb.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                      )
                    } else {
                      Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                      )
                    }
                  }
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = state.nextTitle?.ifBlank { "Next Track" } ?: "Next Track",
                      style = MaterialTheme.typography.titleMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                      text = "Swipe to play next",
                      style = MaterialTheme.typography.bodySmall,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                }
              }

              // Previous track preview (slides in from left when swiping right)
              if (offsetX.value > 0 && state.hasPrevious) {
                val prevDx = (-contentWidth + offsetX.value).roundToInt()
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .offset { IntOffset(prevDx, 0) }
                    .padding(start = 18.dp, end = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Box(
                    modifier = Modifier
                      .size(44.dp)
                      .clip(RoundedCornerShape(8.dp))
                      .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                  ) {
                    val prevThumb = state.prevThumbnail
                    if (prevThumb != null && !prevThumb.isRecycled) {
                      Image(
                        bitmap = prevThumb.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                      )
                    } else {
                      Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                      )
                    }
                  }
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = state.prevTitle?.ifBlank { "Previous Track" } ?: "Previous Track",
                      style = MaterialTheme.typography.titleMedium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                      text = "Swipe to play previous",
                      style = MaterialTheme.typography.bodySmall,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      color = MaterialTheme.colorScheme.primary,
                    )
                  }
                }
              }

              // Current track item content
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .fillMaxHeight()
                  .offset { IntOffset(currentDx, 0) }
                  .padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                // Thumbnail or placeholder icon
                Box(
                  modifier = Modifier
                    .size(44.dp)
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
                      modifier = Modifier.fillMaxSize(),
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
                Column(modifier = Modifier.weight(1f)) {
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
                IconButton(onClick = { stateManager.togglePlayPause() }) {
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
            } // end sliding Box

            // Progress Bar — 7dp top, 8dp bottom, 18dp left/right (18+47+(7+3+8)=83dp total)
            val progress = if (state.durationMs > 0) {
              (state.currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            LinearProgressIndicator(
              progress = { progress },
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp, bottom = 9.dp, start = 18.dp, end = 18.dp)
                .height(4.dp),
              color = MaterialTheme.colorScheme.primary,
              trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
          } // end Column (compact layout)
        }

        // ──────────────────────────────────────────────────────────────
        // Expanded mode: Artwork Container (morphs from collapsed)
        // ──────────────────────────────────────────────────────────────
        val artworkSize = androidx.compose.ui.unit.lerp(44.dp, 140.dp, fraction)
        val artworkCorner = androidx.compose.ui.unit.lerp(8.dp, 16.dp, fraction)
        val artworkTopPadding = androidx.compose.ui.unit.lerp(22.dp, 64.dp, fraction)
        val artworkStartPadding = androidx.compose.ui.unit.lerp(12.dp, 0.dp, fraction)

        if (fraction > 0f) {
          Box(
            modifier = Modifier
              .padding(
                top = artworkTopPadding,
                start = if (fraction < 0.3f) artworkStartPadding else 0.dp
              )
              .then(
                if (fraction >= 0.3f) Modifier.align(Alignment.TopCenter)
                else Modifier.align(Alignment.TopStart)
              )
              .size(artworkSize)
              .shadow(
                elevation = androidx.compose.ui.unit.lerp(0.dp, 8.dp, fraction),
                shape = RoundedCornerShape(artworkCorner)
              )
              .clip(RoundedCornerShape(artworkCorner))
              .background(MaterialTheme.colorScheme.primaryContainer)
              .clickable {
                if (fraction > 0.5f) {
                  stateManager.openPlayer(context)
                }
              },
            contentAlignment = Alignment.Center,
          ) {
            val thumbnail = state.thumbnail
            if (thumbnail != null && !thumbnail.isRecycled) {
              Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
              )
            } else {
              Icon(
                imageVector = Icons.Filled.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(androidx.compose.ui.unit.lerp(24.dp, 52.dp, fraction)),
              )
            }
          }
        }

        // Title & Subtitle Info (expanded view)
        val textTopPadding = androidx.compose.ui.unit.lerp(24.dp, 220.dp, fraction)
        val textStartPadding = androidx.compose.ui.unit.lerp(68.dp, 16.dp, fraction)
        val textEndPadding = androidx.compose.ui.unit.lerp(96.dp, 16.dp, fraction)

        if (fraction > 0f) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(
                top = textTopPadding,
                start = textStartPadding,
                end = textEndPadding
              ),
            horizontalAlignment = if (fraction > 0.4f) Alignment.CenterHorizontally else Alignment.Start,
          ) {
            Text(
              text = state.title.ifBlank { "Playing Media" },
              style = if (fraction > 0.4f) {
                MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
              } else {
                MaterialTheme.typography.titleMedium
              },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              color = MaterialTheme.colorScheme.onSurface,
              textAlign = if (fraction > 0.4f) TextAlign.Center else TextAlign.Start,
              modifier = Modifier.fillMaxWidth(),
            )

            val timeText = if (state.durationMs > 0) {
              "${MediaFormatter.formatDuration(state.currentPositionMs)} / ${MediaFormatter.formatDuration(state.durationMs)}"
            } else {
              state.artist.ifBlank { "Background Playback" }
            }

            Text(
              text = if (fraction > 0.4f) state.artist.ifBlank { "mpvRex Media Player" } else timeText,
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = if (fraction > 0.4f) TextAlign.Center else TextAlign.Start,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }

        // ──────────────────────────────────────────────────────────────
        // Expanded Seekbar + Duration Labels
        // ──────────────────────────────────────────────────────────────
        val seekbarAlpha = ((fraction - 0.4f) * 2.5f).coerceIn(0f, 1f)
        if (seekbarAlpha > 0f) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 275.dp, start = 16.dp, end = 16.dp)
              .graphicsLayer {
                alpha = seekbarAlpha
                translationY = (1f - seekbarAlpha) * 30f
              },
          ) {
            var sliderValue by remember(state.currentPositionMs) {
              mutableFloatStateOf(state.currentPositionMs.toFloat())
            }
            var isDraggingSlider by remember { mutableStateOf(false) }

            val maxDuration = state.durationMs.coerceAtLeast(1L).toFloat()
            val currentPosFloat = if (isDraggingSlider) sliderValue else state.currentPositionMs.toFloat().coerceIn(0f, maxDuration)

            Slider(
              value = currentPosFloat,
              onValueChange = {
                isDraggingSlider = true
                sliderValue = it
              },
              onValueChangeFinished = {
                isDraggingSlider = false
                stateManager.seekTo(sliderValue.toLong())
              },
              valueRange = 0f..maxDuration,
              modifier = Modifier.fillMaxWidth(),
              colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
              ),
            )

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = MediaFormatter.formatDuration(if (isDraggingSlider) sliderValue.toLong() else state.currentPositionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = MediaFormatter.formatDuration(state.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        // ──────────────────────────────────────────────────────────────
        // Expanded Control Row (Shuffle | Prev | Play/Pause | Next | Repeat)
        // ──────────────────────────────────────────────────────────────
        val expandedControlsAlpha = ((fraction - 0.5f) * 2f).coerceIn(0f, 1f)
        if (expandedControlsAlpha > 0f) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 345.dp, start = 16.dp, end = 16.dp)
              .graphicsLayer {
                alpha = expandedControlsAlpha
                translationY = (1f - expandedControlsAlpha) * 40f
              },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            val isShuffle = state.shuffleEnabled
            IconButton(onClick = { stateManager.toggleShuffle() }) {
              Icon(
                imageVector = if (isShuffle) Icons.Filled.ShuffleOn else Icons.Filled.Shuffle,
                contentDescription = "Toggle Shuffle",
                tint = if (isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
              )
            }

            IconButton(onClick = { stateManager.playPrevious() }) {
              Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Previous Track",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp),
              )
            }

            Surface(
              onClick = { stateManager.togglePlayPause() },
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primary,
              shadowElevation = 4.dp,
              modifier = Modifier.size(58.dp),
            ) {
              Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Icon(
                  imageVector = if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                  contentDescription = if (state.isPaused) "Play" else "Pause",
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(34.dp),
                )
              }
            }

            IconButton(onClick = { stateManager.playNext() }) {
              Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Next Track",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(32.dp),
              )
            }

            val repeatIcon = when (state.repeatMode) {
              RepeatMode.OFF -> Icons.Filled.Repeat
              RepeatMode.ONE -> Icons.Filled.RepeatOne
              RepeatMode.ALL -> Icons.Filled.RepeatOn
            }
            val isRepeatActive = state.repeatMode != RepeatMode.OFF

            IconButton(onClick = { stateManager.cycleRepeatMode() }) {
              Icon(
                imageVector = repeatIcon,
                contentDescription = "Cycle Repeat Mode",
                tint = if (isRepeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
              )
            }
          }
        }
      }
    }
  }
}
