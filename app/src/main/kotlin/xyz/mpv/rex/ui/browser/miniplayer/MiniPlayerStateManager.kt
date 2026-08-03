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
)

/**
 * Central state manager for the Mini Player component.
 * Coordinates real-time state between MediaPlaybackService, PlayerActivity, and MainScreen.
 */
class MiniPlayerStateManager {
  private val _state = MutableStateFlow(MiniPlayerState())
  val state: StateFlow<MiniPlayerState> = _state.asStateFlow()

  fun updateState(
    isPlaybackActive: Boolean = _state.value.isPlaybackActive,
    title: String = _state.value.title,
    artist: String = _state.value.artist,
    currentPositionMs: Long = _state.value.currentPositionMs,
    durationMs: Long = _state.value.durationMs,
    isPaused: Boolean = _state.value.isPaused,
    thumbnail: Bitmap? = _state.value.thumbnail,
    videoPath: String? = _state.value.videoPath,
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

  @Volatile
  var savedPlayerIntent: Intent? = null

  fun clearState() {
    savedPlayerIntent = null
    _state.value = MiniPlayerState()
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
