package app.marlboroadvance.mpvex.ui.player.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOn
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ShuffleOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.PlayerButton
import app.marlboroadvance.mpvex.ui.player.Panels
import app.marlboroadvance.mpvex.ui.player.PlayerActivity
import app.marlboroadvance.mpvex.ui.player.PlayerViewModel
import app.marlboroadvance.mpvex.ui.player.Sheets
import app.marlboroadvance.mpvex.ui.player.VideoAspect
import app.marlboroadvance.mpvex.ui.player.controls.components.ControlsButton
import app.marlboroadvance.mpvex.ui.player.controls.components.CurrentChapter
import app.marlboroadvance.mpvex.ui.theme.controlColor
import app.marlboroadvance.mpvex.ui.theme.spacing
import dev.vivvvek.seeker.Segment

@Composable
fun RenderPlayerButton(
  button: PlayerButton,
  chapters: List<Segment>,
  currentChapter: Int?,
  isPortrait: Boolean,
  isSpeedNonOne: Boolean,
  currentZoom: Float,
  aspect: VideoAspect,
  mediaTitle: String?,
  hideBackground: Boolean,
  decoder: app.marlboroadvance.mpvex.ui.player.Decoder,
  playbackSpeed: Float,
  onBackPress: () -> Unit,
  onOpenSheet: (Sheets) -> Unit,
  onOpenPanel: (Panels) -> Unit,
  viewModel: PlayerViewModel,
  activity: PlayerActivity,
  buttonSize: Dp = 40.dp,
  isMoreSheet: Boolean = false,
) {
  val clickEvent = LocalPlayerButtonsClickEvent.current
  when (button) {
    PlayerButton.BACK_ARROW -> {
      ControlsButton(
        icon = Icons.AutoMirrored.Default.ArrowBack,
        onClick = onBackPress,
        color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(buttonSize),
      )
    }

    PlayerButton.VIDEO_TITLE -> {
      val playlistModeEnabled = viewModel.hasPlaylistSupport()

      val titleInteractionSource = remember { MutableInteractionSource() }

      Surface(
        shape = CircleShape,
        color =
          if (hideBackground) {
            Color.Transparent
          } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(
              alpha = 0.55f,
            )
          },
        contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border =
          if (hideBackground) {
            null
          } else {
            BorderStroke(
              1.dp,
              MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
          },
        modifier =
          Modifier
            .height(buttonSize)
            .clip(CircleShape)
            .clickable(
              interactionSource = titleInteractionSource,
              indication = ripple(
                bounded = true,
              ),
              enabled = playlistModeEnabled,
              onClick = {
                clickEvent()
                onOpenSheet(Sheets.Playlist)
              },
            ),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier
              .padding(
                horizontal = MaterialTheme.spacing.extraSmall,
                vertical = MaterialTheme.spacing.small,
              ),
        ) {
          Text(
            mediaTitle ?: "",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f, fill = false),
          )
          viewModel.getPlaylistInfo()?.let { playlistInfo ->
            Text(
              " • $playlistInfo",
              maxLines = 1,
              overflow = TextOverflow.Visible,
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
    }

    PlayerButton.BOOKMARKS_CHAPTERS -> {
      if (chapters.isNotEmpty()) {
        if (isMoreSheet) {
          val chapter = chapters.getOrNull(currentChapter ?: 0)
          Surface(
            shape = CircleShape,
            color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { onOpenSheet(Sheets.Chapters) }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)
            ) {
              Icon(
                imageVector = Icons.Default.Bookmarks,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = chapter?.name ?: "Chapters",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp)
              )
            }
          }
        } else {
          ControlsButton(
            Icons.Default.Bookmarks,
            onClick = { onOpenSheet(Sheets.Chapters) },
            color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(buttonSize),
          )
        }
      }
    }

    PlayerButton.PLAYBACK_SPEED -> {
      val cycleSpeed = {
        val newSpeed = if (playbackSpeed >= 2f) 0.25f else playbackSpeed + 0.25f
        `is`.xyz.mpv.MPVLib.setPropertyFloat("speed", newSpeed)
      }

      if (isSpeedNonOne || isMoreSheet) {
        @OptIn(ExperimentalFoundationApi::class)
        Surface(
          shape = CircleShape,
          color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
          contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
          tonalElevation = 0.dp,
          shadowElevation = 0.dp,
          border = if (hideBackground) null else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
          ),
          modifier = Modifier
            .height(buttonSize)
            .clip(CircleShape)
            .combinedClickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = ripple(bounded = true),
              onClick = {
                clickEvent()
                cycleSpeed()
              },
              onLongClick = {
                clickEvent()
                onOpenSheet(Sheets.PlaybackSpeed)
              },
            ),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            modifier = Modifier.padding(
              horizontal = MaterialTheme.spacing.small,
              vertical = MaterialTheme.spacing.small,
            ),
          ) {
            Icon(
              imageVector = Icons.Default.Speed,
              contentDescription = "Playback Speed",
              tint = if (isSpeedNonOne) MaterialTheme.colorScheme.primary else (if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface),
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = String.format("%.2fx", playbackSpeed),
              maxLines = 1,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      } else {
        ControlsButton(
          icon = Icons.Default.Speed,
          onClick = { cycleSpeed() },
          onLongClick = { onOpenSheet(Sheets.PlaybackSpeed) },
          color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(buttonSize),
        )
      }
    }

    PlayerButton.DECODER -> {
      Surface(
        shape = CircleShape,
        color =
          if (hideBackground) {
            Color.Transparent
          } else {
            MaterialTheme.colorScheme.surfaceContainer.copy(
              alpha = 0.55f,
            )
          },
        contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border =
          if (hideBackground) {
            null
          } else {
            BorderStroke(
              1.dp,
              MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
          },
        modifier = Modifier
          .height(buttonSize)
          .clip(CircleShape)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = true),
            onClick = {
              clickEvent()
              onOpenSheet(Sheets.Decoders)
            },
          ),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier =
            Modifier
              .padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.small,
              ),
        ) {
          if (isMoreSheet) {
            Icon(
              imageVector = Icons.Outlined.Memory,
              contentDescription = null,
              modifier = Modifier.size(20.dp).padding(end = 6.dp)
            )
          }
          Text(
            text = decoder.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }

    PlayerButton.SCREEN_ROTATION -> {
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { viewModel.cycleScreenRotations() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)
            ) {
              Icon(
                imageVector = Icons.Default.ScreenRotation,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = "Rotation",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon = Icons.Default.ScreenRotation,
            onClick = viewModel::cycleScreenRotations,
            color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.FRAME_NAVIGATION -> {
      val isExpanded by viewModel.isFrameNavigationExpanded.collectAsState()
      val isSnapshotLoading by viewModel.isSnapshotLoading.collectAsState()
      val context = LocalContext.current

      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { onOpenSheet(Sheets.FrameNavigation) }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)
            ) {
              Icon(
                imageVector = Icons.Default.Camera,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = "Frame Nav",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
              (fadeIn(animationSpec = tween(200)) + expandHorizontally(animationSpec = tween(250)))
                .togetherWith(fadeOut(animationSpec = tween(200)) + shrinkHorizontally(animationSpec = tween(250)))
                .using(SizeTransform(clip = false))
            },
            label = "FrameNavExpandCollapse",
          ) { expanded ->
            if (expanded) {
              Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.height(buttonSize),
              ) {
                Row(
                  horizontalArrangement = Arrangement.spacedBy(2.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                  // Previous frame button
                  Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier
                      .size(buttonSize - 4.dp)
                      .clip(CircleShape)
                      .clickable(onClick = {
                        viewModel.frameStepBackward()
                        viewModel.resetFrameNavigationTimer()
                      }),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Previous Frame",
                        tint = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                      )
                    }
                  }

                  // Camera / Loading button
                  if (isSnapshotLoading) {
                    Surface(
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                      modifier = Modifier.size(buttonSize - 4.dp),
                    ) {
                      Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(
                          modifier = Modifier.size(16.dp),
                          strokeWidth = 2.dp,
                          color = if (hideBackground) controlColor else MaterialTheme.colorScheme.primary,
                        )
                      }
                    }
                  } else {
                    @OptIn(ExperimentalFoundationApi::class)
                    Surface(
                      shape = CircleShape,
                      color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                      modifier = Modifier
                        .size(buttonSize - 4.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                          onClick = {
                            viewModel.takeSnapshot(context)
                            viewModel.resetFrameNavigationTimer()
                          },
                          onLongClick = { onOpenSheet(Sheets.FrameNavigation) },
                        ),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          imageVector = Icons.Default.CameraAlt,
                          contentDescription = "Take Screenshot",
                          tint = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
                          modifier = Modifier.size(20.dp),
                        )
                      }
                    }
                  }

                  // Next frame button
                  Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier
                      .size(buttonSize - 4.dp)
                      .clip(CircleShape)
                      .clickable(onClick = {
                        viewModel.frameStepForward()
                        viewModel.resetFrameNavigationTimer()
                      }),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Next Frame",
                        tint = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                      )
                    }
                  }
                }
              }
            } else {
              // Collapsed: Show camera icon button
              ControlsButton(
                icon = Icons.Default.Camera,
                onClick = viewModel::toggleFrameNavigationExpanded,
                onLongClick = { onOpenSheet(Sheets.FrameNavigation) },
                color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(buttonSize),
              )
            }
          }
      }
    }

    PlayerButton.VIDEO_ZOOM -> {
      if (kotlin.math.abs(currentZoom) >= 0.005f || isMoreSheet) {
        @OptIn(ExperimentalFoundationApi::class)
        Surface(
          shape = CircleShape,
          color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
          contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
          tonalElevation = 0.dp,
          shadowElevation = 0.dp,
          border = if (hideBackground) null else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
          ),
          modifier = Modifier
            .height(buttonSize)
            .clip(CircleShape)
            .combinedClickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = ripple(bounded = true),
              onClick = {
                clickEvent()
                onOpenSheet(Sheets.VideoZoom)
              },
              onLongClick = {
                clickEvent()
                viewModel.resetVideoZoom()
              },
            ),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            modifier = Modifier.padding(
              horizontal = MaterialTheme.spacing.small,
              vertical = MaterialTheme.spacing.small,
            ),
          ) {
            Icon(
              imageVector = Icons.Default.ZoomIn,
              contentDescription = "Video Zoom",
              tint = if (kotlin.math.abs(currentZoom) >= 0.005f) MaterialTheme.colorScheme.primary else (if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface),
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = String.format("%.0f%%", currentZoom * 100),
              maxLines = 1,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      } else {
        ControlsButton(
          Icons.Default.ZoomIn,
          onClick = {
            clickEvent()
            onOpenSheet(Sheets.VideoZoom)
          },
          onLongClick = { viewModel.resetVideoZoom() },
          color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.size(buttonSize),
        )
      }
    }

    PlayerButton.PICTURE_IN_PICTURE -> {
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { activity.enterPipModeHidingOverlay() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)
            ) {
              Icon(
                imageVector = Icons.Default.PictureInPictureAlt,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = "PiP",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            Icons.Default.PictureInPictureAlt,
            onClick = { activity.enterPipModeHidingOverlay() },
            color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.ASPECT_RATIO -> {
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable {
                  when (aspect) {
                    VideoAspect.Fit -> viewModel.changeVideoAspect(VideoAspect.Stretch)
                    VideoAspect.Stretch -> viewModel.changeVideoAspect(VideoAspect.Crop)
                    VideoAspect.Crop -> viewModel.changeVideoAspect(VideoAspect.Fit)
                  }
              }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)
            ) {
              Icon(
                imageVector = when (aspect) {
                    VideoAspect.Fit -> Icons.Default.AspectRatio
                    VideoAspect.Stretch -> Icons.Default.ZoomOutMap
                    VideoAspect.Crop -> Icons.Default.FitScreen
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = aspect.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon =
              when (aspect) {
                VideoAspect.Fit -> Icons.Default.AspectRatio
                VideoAspect.Stretch -> Icons.Default.ZoomOutMap
                VideoAspect.Crop -> Icons.Default.FitScreen
              },
            onClick = {
              when (aspect) {
                VideoAspect.Fit -> viewModel.changeVideoAspect(VideoAspect.Stretch)
                VideoAspect.Stretch -> viewModel.changeVideoAspect(VideoAspect.Crop)
                VideoAspect.Crop -> viewModel.changeVideoAspect(VideoAspect.Fit)
              }
            },
            onLongClick = { onOpenSheet(Sheets.AspectRatios) },
            color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.LOCK_CONTROLS -> {
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { viewModel.lockControls() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)
            ) {
              Icon(
                imageVector = Icons.Default.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = "Lock",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            Icons.Default.LockOpen,
            onClick = viewModel::lockControls,
            color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.AUDIO_TRACK -> {
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { onOpenSheet(Sheets.AudioTracks) }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)
            ) {
              Icon(
                imageVector = Icons.Default.Audiotrack,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = "Audio",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            Icons.Default.Audiotrack,
            onClick = { onOpenSheet(Sheets.AudioTracks) },
            onLongClick = { onOpenPanel(Panels.AudioDelay) },
            color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.SUBTITLES -> {
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { onOpenSheet(Sheets.SubtitleTracks) }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)
            ) {
              Icon(
                imageVector = Icons.Default.Subtitles,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = "Subtitles",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            Icons.Default.Subtitles,
            onClick = { onOpenSheet(Sheets.SubtitleTracks) },
            onLongClick = { onOpenPanel(Panels.SubtitleDelay) },
            color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.MORE_OPTIONS -> {
      if (isMoreSheet) {
          // Hide more options inside more options sheet
      } else {
          ControlsButton(
            Icons.Default.MoreVert,
            onClick = { onOpenSheet(Sheets.More) },
            onLongClick = { onOpenPanel(Panels.VideoFilters) },
            color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.CURRENT_CHAPTER -> {
      if (isPortrait || isMoreSheet) {
      } else {
        AnimatedVisibility(
          chapters.getOrNull(currentChapter ?: 0) != null,
          enter = fadeIn(),
          exit = fadeOut(),
        ) {
          chapters.getOrNull(currentChapter ?: 0)?.let { chapter ->
            CurrentChapter(
              chapter = chapter,
              onClick = { onOpenSheet(Sheets.Chapters) },
            )
          }
        }
      }
    }

    PlayerButton.REPEAT_MODE -> {
      val repeatMode by viewModel.repeatMode.collectAsState()
      val icon = when (repeatMode) {
        app.marlboroadvance.mpvex.ui.player.RepeatMode.OFF -> Icons.Default.Repeat
        app.marlboroadvance.mpvex.ui.player.RepeatMode.ONE -> Icons.Default.RepeatOne
        app.marlboroadvance.mpvex.ui.player.RepeatMode.ALL -> Icons.Default.RepeatOn
      }
      
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            contentColor = if (repeatMode != app.marlboroadvance.mpvex.ui.player.RepeatMode.OFF) MaterialTheme.colorScheme.primary else (if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface),
            border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { viewModel.cycleRepeatMode() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)
            ) {
              Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = when (repeatMode) {
                    app.marlboroadvance.mpvex.ui.player.RepeatMode.OFF -> "Repeat Off"
                    app.marlboroadvance.mpvex.ui.player.RepeatMode.ONE -> "Repeat One"
                    app.marlboroadvance.mpvex.ui.player.RepeatMode.ALL -> "Repeat All"
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon = icon,
            onClick = viewModel::cycleRepeatMode,
            color = if (hideBackground) {
              when (repeatMode) {
                app.marlboroadvance.mpvex.ui.player.RepeatMode.OFF -> controlColor
                else -> MaterialTheme.colorScheme.primary
              }
            } else {
              when (repeatMode) {
                app.marlboroadvance.mpvex.ui.player.RepeatMode.OFF -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.primary
              }
            },
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.CUSTOM_SKIP -> {
      val playerPreferences = org.koin.compose.koinInject<app.marlboroadvance.mpvex.preferences.PlayerPreferences>()
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { viewModel.seekBy(playerPreferences.customSkipDuration.get()) }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)
            ) {
              Icon(
                imageVector = Icons.Default.FastForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = "Skip ${playerPreferences.customSkipDuration.get()}s",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon = Icons.Default.FastForward,
            onClick = { viewModel.seekBy(playerPreferences.customSkipDuration.get()) },
            color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.SHUFFLE -> {
      // Only show shuffle button if there's a playlist (more than one video)
      if (viewModel.hasPlaylistSupport()) {
        val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
        
        if (isMoreSheet) {
            Surface(
              shape = CircleShape,
              color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
              contentColor = if (shuffleEnabled) MaterialTheme.colorScheme.primary else (if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface),
              border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
              modifier = Modifier
                .height(buttonSize)
                .clip(CircleShape)
                .clickable { viewModel.toggleShuffle() }
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)
              ) {
                Icon(
                  imageVector = if (shuffleEnabled) Icons.Default.ShuffleOn else Icons.Default.Shuffle,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp)
                )
                Text(
                  text = if (shuffleEnabled) "Shuffle On" else "Shuffle Off",
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                )
              }
            }
        } else {
            ControlsButton(
              icon = if (shuffleEnabled) Icons.Default.ShuffleOn else Icons.Default.Shuffle,
              onClick = viewModel::toggleShuffle,
              color = if (hideBackground) {
                if (shuffleEnabled) MaterialTheme.colorScheme.primary else controlColor
              } else {
                if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
              },
              modifier = Modifier.size(buttonSize),
            )
        }
      }
    }

    PlayerButton.MIRROR -> {
      val isMirrored by viewModel.isMirrored.collectAsState()
      
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            contentColor = if (isMirrored) MaterialTheme.colorScheme.primary else (if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface),
            border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { viewModel.toggleMirroring() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)
            ) {
              Icon(
                imageVector = Icons.Default.Flip,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = if (isMirrored) "Mirrored" else "Mirror",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon = Icons.Default.Flip,
            onClick = viewModel::toggleMirroring,
            color = if (hideBackground) {
              if (isMirrored) MaterialTheme.colorScheme.primary else controlColor
            } else {
              if (isMirrored) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.VERTICAL_FLIP -> {
      val isVerticalFlipped by viewModel.isVerticalFlipped.collectAsState()
      val vFlipColor = if (hideBackground) {
        if (isVerticalFlipped) MaterialTheme.colorScheme.primary else controlColor
      } else {
        if (isVerticalFlipped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
      }
      
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            contentColor = vFlipColor,
            border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { viewModel.toggleVerticalFlip() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)
            ) {
              Icon(
                imageVector = Icons.Default.Flip,
                contentDescription = null,
                modifier = Modifier.size(20.dp).rotate(90f)
              )
              Text(
                text = if (isVerticalFlipped) "Flipped" else "Flip Vert",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          Surface(
            shape = CircleShape,
            color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            contentColor = vFlipColor,
            border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
              .size(buttonSize)
              .clip(CircleShape)
              .clickable(onClick = viewModel::toggleVerticalFlip),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.Flip,
                contentDescription = "Vertical Flip",
                tint = vFlipColor,
                modifier = Modifier
                  .padding(MaterialTheme.spacing.small)
                  .size(20.dp)
                  .rotate(90f),
              )
            }
          }
      }
    }

    PlayerButton.AB_LOOP -> {
      val isExpanded by viewModel.isABLoopExpanded.collectAsState()
      val loopA by viewModel.abLoopA.collectAsState()
      val loopB by viewModel.abLoopB.collectAsState()

      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            contentColor = if (loopA != null || loopB != null) MaterialTheme.colorScheme.primary else (if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface),
            border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { viewModel.toggleABLoopExpanded() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)
            ) {
              Text(
                text = "AB",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
              )
              Text(
                text = "Loop",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
              (fadeIn(animationSpec = tween(200)) + expandHorizontally(animationSpec = tween(250)))
                .togetherWith(fadeOut(animationSpec = tween(200)) + shrinkHorizontally(animationSpec = tween(250)))
                .using(SizeTransform(clip = false))
            },
            label = "ABLoopExpandCollapse",
          ) { expanded ->
            if (expanded) {
              Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.height(buttonSize),
              ) {
                Row(
                  horizontalArrangement = Arrangement.spacedBy(2.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                  // Point A Button - always transparent background
                  Surface(
                    shape = CircleShape,
                    color = if (loopA != null) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                    modifier = Modifier
                      .height(buttonSize - 4.dp)
                      .widthIn(min = buttonSize - 4.dp)
                      .clip(CircleShape)
                      .clickable(onClick = { viewModel.setLoopA() }),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(
                        text = if (loopA != null) viewModel.formatTimestamp(loopA!!) else "A",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (loopA != null) {
                          MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                          if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = if (loopA != null) 8.dp else 0.dp),
                      )
                    }
                  }

                  // Clear/Close Button - always has background
                  Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                      .size(buttonSize - 4.dp)
                      .clip(CircleShape)
                      .clickable(onClick = {
                        viewModel.clearABLoop()
                        viewModel.toggleABLoopExpanded()
                      }),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear Loop",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp),
                      )
                    }
                  }

                  // Point B Button - always transparent background
                  Surface(
                    shape = CircleShape,
                    color = if (loopB != null) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                    modifier = Modifier
                      .height(buttonSize - 4.dp)
                      .widthIn(min = buttonSize - 4.dp)
                      .clip(CircleShape)
                      .clickable(onClick = { viewModel.setLoopB() }),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Text(
                        text = if (loopB != null) viewModel.formatTimestamp(loopB!!) else "B",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (loopB != null) {
                          MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                          if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = if (loopB != null) 8.dp else 0.dp),
                      )
                    }
                  }
                }
              }
            } else {
              // Collapsed: Show "AB" text button
              Surface(
                shape = CircleShape,
                color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
                border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                  .size(buttonSize)
                  .clip(CircleShape)
                  .clickable(onClick = viewModel::toggleABLoopExpanded),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = "AB",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = if (loopA != null && loopB != null) {
                      MaterialTheme.colorScheme.primary
                    } else {
                      if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface
                    },
                  )
                }
              }
            }
          }
      }
    }

    PlayerButton.BACKGROUND_PLAYBACK -> {
      if (isMoreSheet) {
          Surface(
            shape = CircleShape,
            color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            contentColor = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier
              .height(buttonSize)
              .clip(CircleShape)
              .clickable { activity.triggerBackgroundPlayback() }
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)
            ) {
              Icon(
                imageVector = Icons.Default.Headset,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = "Background",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
              )
            }
          }
      } else {
          ControlsButton(
            icon = Icons.Default.Headset,
            onClick = { activity.triggerBackgroundPlayback() },
            color = if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(buttonSize),
          )
      }
    }

    PlayerButton.AMBIENT_MODE -> {
        val isAmbientEnabled by viewModel.isAmbientEnabled.collectAsState()
        
        if (isMoreSheet) {
            Surface(
              shape = CircleShape,
              color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
              contentColor = if (isAmbientEnabled) MaterialTheme.colorScheme.primary else (if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface),
              border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
              modifier = Modifier
                .height(buttonSize)
                .clip(CircleShape)
                .clickable { viewModel.toggleAmbientMode() }
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small)
              ) {
                Icon(
                  imageVector = if (isAmbientEnabled) Icons.Filled.BlurOn else Icons.Outlined.BlurOn,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp)
                )
                Text(
                  text = "Ambient",
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                )
              }
            }
        } else {
            @OptIn(ExperimentalFoundationApi::class)
            Surface(
              shape = CircleShape,
              color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
              contentColor = if (isAmbientEnabled) {
                   MaterialTheme.colorScheme.primary
                } else {
                   if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface
                },
              border = if (hideBackground) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
              modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = ripple(bounded = true),
                  onClick = { 
                    clickEvent()
                    viewModel.toggleAmbientMode() 
                  }
                ),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = if (isAmbientEnabled) Icons.Filled.BlurOn else Icons.Outlined.BlurOn,
                  contentDescription = "Ambience Mode",
                  tint = if (isAmbientEnabled) MaterialTheme.colorScheme.primary else (if (hideBackground) controlColor else MaterialTheme.colorScheme.onSurface),
                  modifier = Modifier.size(24.dp)
                )
              }
            }
        }
    }

    PlayerButton.NONE -> { /* Do nothing */
    }
  }
}
