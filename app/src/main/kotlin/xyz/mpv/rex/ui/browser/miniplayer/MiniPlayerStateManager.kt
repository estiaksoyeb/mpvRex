package xyz.mpv.rex.ui.browser.miniplayer

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import xyz.mpv.rex.ui.player.PlayerActivity
import xyz.mpv.rex.ui.player.MediaPlaybackService
import `is`.xyz.mpv.MPVLib

data class MiniPlayerState(
  val isPlaybackActive: Boolean = false,
  val title: String = "",
  val artist: String = "",
  val currentPositionMs: Long = 0L,
  val durationMs: Long = 0L,
  val isPaused: Boolean = false,
  val thumbnail: Bitmap? = null,
  val videoPath: String? = null,
  val hasNext: Boolean = false,
  val hasPrevious: Boolean = false,
  val nextTitle: String? = null,
  val prevTitle: String? = null,
  val nextThumbnail: Bitmap? = null,
  val prevThumbnail: Bitmap? = null,
)

/**
 * Central state manager for the Mini Player component.
 * Coordinates real-time state between MediaPlaybackService, PlayerActivity, and MainScreen.
 */
class MiniPlayerStateManager {
  private val _state = MutableStateFlow(MiniPlayerState())
  val state: StateFlow<MiniPlayerState> = _state.asStateFlow()

  @Volatile
  var onNextHandler: (() -> Unit)? = null

  @Volatile
  var onPreviousHandler: (() -> Unit)? = null

  fun updateState(
    isPlaybackActive: Boolean = _state.value.isPlaybackActive,
    title: String = _state.value.title,
    artist: String = _state.value.artist,
    currentPositionMs: Long = _state.value.currentPositionMs,
    durationMs: Long = _state.value.durationMs,
    isPaused: Boolean = _state.value.isPaused,
    thumbnail: Bitmap? = _state.value.thumbnail,
    videoPath: String? = _state.value.videoPath,
    hasNext: Boolean = _state.value.hasNext,
    hasPrevious: Boolean = _state.value.hasPrevious,
    nextTitle: String? = _state.value.nextTitle,
    prevTitle: String? = _state.value.prevTitle,
    nextThumbnail: Bitmap? = _state.value.nextThumbnail,
    prevThumbnail: Bitmap? = _state.value.prevThumbnail,
  ) {
    _state.update {
      it.copy(
        isPlaybackActive = isPlaybackActive,
        title = title,
        artist = artist,
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        isPaused = isPaused,
        thumbnail = thumbnail,
        videoPath = videoPath,
        hasNext = hasNext,
        hasPrevious = hasPrevious,
        nextTitle = nextTitle,
        prevTitle = prevTitle,
        nextThumbnail = nextThumbnail,
        prevThumbnail = prevThumbnail,
      )
    }
  }

  fun togglePlayPause() {
    val newPaused = !_state.value.isPaused
    runCatching {
      MPVLib.setPropertyBoolean("pause", newPaused)
    }
    _state.update { it.copy(isPaused = newPaused) }
  }

  fun playNext() {
    val currentNextThumb = _state.value.nextThumbnail
    val currentNextTitle = _state.value.nextTitle
    if (!currentNextTitle.isNullOrBlank()) {
      MediaPlaybackService.thumbnail = currentNextThumb
      _state.update {
        it.copy(
          title = currentNextTitle,
          thumbnail = currentNextThumb,
          nextThumbnail = null,
          prevThumbnail = null,
        )
      }
    }
    val handler = onNextHandler
    if (handler != null) {
      handler.invoke()
    } else {
      runCatching {
        MPVLib.command("playlist-next")
      }
    }
  }

  fun playPrevious() {
    val currentPrevThumb = _state.value.prevThumbnail
    val currentPrevTitle = _state.value.prevTitle
    if (!currentPrevTitle.isNullOrBlank()) {
      MediaPlaybackService.thumbnail = currentPrevThumb
      _state.update {
        it.copy(
          title = currentPrevTitle,
          thumbnail = currentPrevThumb,
          nextThumbnail = null,
          prevThumbnail = null,
        )
      }
    }
    val handler = onPreviousHandler
    if (handler != null) {
      handler.invoke()
    } else {
      runCatching {
        MPVLib.command("playlist-prev")
      }
    }
  }

  @Volatile
  var savedPlayerIntent: Intent? = null

  fun clearState() {
    savedPlayerIntent = null
    _state.update { it.copy(isPlaybackActive = false) }
  }

  fun openPlayer(context: Context) {
    val intent = Intent(context, PlayerActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
    }
    context.startActivity(
      intent,
      ActivityOptions.makeCustomAnimation(context, android.R.anim.fade_in, 0).toBundle()
    )
  }
}
