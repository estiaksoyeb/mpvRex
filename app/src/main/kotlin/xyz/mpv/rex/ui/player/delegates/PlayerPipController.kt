package xyz.mpv.rex.ui.player.delegates

import android.content.res.Configuration
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import xyz.mpv.rex.ui.player.MPVPipHelper
import xyz.mpv.rex.ui.player.PlayerActivity
import xyz.mpv.rex.ui.player.PlayerActivity.Companion.TAG

/**
 * Controller responsible for Picture-in-Picture (PiP) lifecycle, aspect ratio updates,
 * controls alpha management, and system UI coordination during PiP entry/exit.
 */
class PlayerPipController(
  private val activity: PlayerActivity,
) {
  val pipHelper: MPVPipHelper by lazy {
    MPVPipHelper(activity = activity, mpvView = activity.player)
  }

  var wasInPipMode: Boolean = false

  /**
   * Configures window for Picture-in-Picture mode.
   * Shows system UI and navigation bars.
   */
  fun enterPipUIMode() {
    activity.systemUiController.enterPipUIMode()
  }

  /**
   * Restores window configuration when exiting Picture-in-Picture mode.
   * Hides system UI for immersive playback.
   */
  fun exitPipUIMode() {
    activity.systemUiController.exitPipUIMode()
  }

  /**
   * Enters Picture-in-Picture mode.
   */
  fun enterPipMode() {
    pipHelper.enterPipMode()
  }

  /**
   * Updates Picture-in-Picture parameters (e.g., aspect ratio, actions).
   */
  fun updatePictureInPictureParams() {
    pipHelper.updatePictureInPictureParams()
  }

  /**
   * Cleans up PiP receiver and state on stop.
   */
  fun onStop() {
    pipHelper.onStop()
  }

  /**
   * Enters Picture-in-Picture mode and hides all overlay controls.
   */
  fun enterPipModeHidingOverlay() {
    runCatching {
      enterPipUIMode()
    }.onFailure { e ->
      Log.e(TAG, "Error entering PiP mode with hidden overlay", e)
    }

    activity.binding.controls.alpha = 0f
    activity.miniPlayerStateManager.clearState()

    pipHelper.enterPipMode()
  }

  /**
   * Handles configuration and UI changes when entering or exiting Picture-in-Picture mode.
   */
  @RequiresApi(Build.VERSION_CODES.P)
  fun onPictureInPictureModeChanged(
    isInPictureInPictureMode: Boolean,
    newConfig: Configuration,
  ) {
    wasInPipMode = isInPictureInPictureMode
    pipHelper.onPictureInPictureModeChanged(isInPictureInPictureMode)

    activity.binding.controls.alpha = if (isInPictureInPictureMode) 0f else 1f

    runCatching {
      if (isInPictureInPictureMode) {
        activity.miniPlayerStateManager.clearState()
        enterPipUIMode()
      } else {
        exitPipUIMode()
      }
    }.onFailure { e ->
      Log.e(TAG, "Error handling PiP mode change", e)
    }
  }
}
