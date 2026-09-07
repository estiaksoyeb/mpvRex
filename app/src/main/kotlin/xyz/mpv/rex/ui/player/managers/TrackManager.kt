package xyz.mpv.rex.ui.player.managers

import android.net.Uri
import android.util.Log
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mpv.rex.preferences.SubtitlesPreferences
import xyz.mpv.rex.ui.player.TrackNode
import xyz.mpv.rex.ui.player.TrackSelector

/**
 * Manages audio and subtitle tracks, track selection, external audio tracks,
 * and duration synchronization for external audio.
 */
class TrackManager(
  private val subtitlesPreferences: SubtitlesPreferences,
  private val playbackManager: PlaybackManager,
  private val scope: CoroutineScope,
  private val onShowToast: (String) -> Unit,
  private val resolveUri: (Uri) -> String?,
  private val onPreciseDurationChanged: ((Float) -> Unit)? = null,
) {
  companion object {
    private const val TAG = "TrackManager"
  }

  private val _externalAudioTracks = mutableListOf<String>()
  val externalAudioTracks: List<String>
    get() = synchronized(_externalAudioTracks) { _externalAudioTracks.toList() }

  private val _primaryVideoDuration = MutableStateFlow<Double?>(null)
  val primaryVideoDuration: StateFlow<Double?> = _primaryVideoDuration.asStateFlow()

  val isExternalAudioActive: Boolean
    get() = synchronized(_externalAudioTracks) { _externalAudioTracks.isNotEmpty() } && (_primaryVideoDuration.value ?: 0.0) > 0.0

  fun addAudio(uri: Uri, select: Boolean = true, silent: Boolean = false) {
    scope.launch(Dispatchers.IO) {
      runCatching {
        // Save primary video duration before adding external audio if not saved yet
        if (_primaryVideoDuration.value == null || (_primaryVideoDuration.value ?: 0.0) <= 0.0) {
          val currentDur = MPVLib.getPropertyDouble("duration")
          if (currentDur != null && currentDur > 0) {
            _primaryVideoDuration.value = currentDur
          }
        }

        val path =
          resolveUri(uri)
            ?: return@launch withContext(Dispatchers.Main) {
              if (!silent) onShowToast("Failed to load audio file: Invalid URI")
            }

        synchronized(_externalAudioTracks) {
          val uriStr = uri.toString()
          if (!_externalAudioTracks.contains(uriStr)) {
            _externalAudioTracks.add(uriStr)
          }
        }

        val flag = if (select) "select" else "cached"
        MPVLib.command("audio-add", path, flag)

        _primaryVideoDuration.value?.let { primDur ->
          if (primDur > 0) {
            onPreciseDurationChanged?.invoke(primDur.toFloat())
          }
        }

        if (!silent) {
          withContext(Dispatchers.Main) {
            onShowToast("Audio track added")
          }
        }
      }.onFailure { e ->
        if (!silent) {
          withContext(Dispatchers.Main) {
            onShowToast("Failed to load audio: ${e.message}")
          }
        }
        Log.e(TAG, "Error adding audio", e)
      }
    }
  }

  fun resetExternalAudioTracks() {
    synchronized(_externalAudioTracks) {
      _externalAudioTracks.clear()
    }
    _primaryVideoDuration.value = null
  }

  fun setPrimaryVideoDuration(duration: Double?) {
    _primaryVideoDuration.value = duration
  }

  fun toggleSubtitle(id: Int, tracks: List<TrackNode>) {
    val primarySid = MPVLib.getPropertyInt("sid") ?: 0
    val secondarySid = MPVLib.getPropertyInt("secondary-sid") ?: 0

    when {
      id == primarySid -> {
        // Unselecting primary subtitle
        if (secondarySid > 0) {
          // If there's a secondary subtitle, promote it to primary
          val secondaryToPromote = secondarySid
          MPVLib.setPropertyString("secondary-sid", "no")
          MPVLib.setPropertyInt("sid", secondaryToPromote)
          val overrideAssSubs = subtitlesPreferences.overrideAssSubs.get()
          MPVLib.setPropertyString("sub-ass-override", if (overrideAssSubs) "force" else "scale")
          MPVLib.setPropertyInt("sub-pos", subtitlesPreferences.subPos.get())
          MPVLib.setPropertyFloat("sub-scale", subtitlesPreferences.subScale.get())
          val track = tracks.firstOrNull { it.id == secondaryToPromote }
          TrackSelector.rememberSubtitleTrack(track?.title, track?.lang, isOff = false)
        } else {
          // No secondary, just turn off primary
          MPVLib.setPropertyString("sid", "no")
          TrackSelector.rememberSubtitleTrack(null, null, isOff = true)
        }
      }
      id == secondarySid -> MPVLib.setPropertyString("secondary-sid", "no")
      primarySid <= 0 -> {
        MPVLib.setPropertyInt("sid", id)
        val track = tracks.firstOrNull { it.id == id }
        TrackSelector.rememberSubtitleTrack(track?.title, track?.lang, isOff = false)
      }
      secondarySid <= 0 -> {
        MPVLib.setPropertyString("secondary-sub-ass-override", "force")
        MPVLib.setPropertyInt("secondary-sub-pos", subtitlesPreferences.secondarySubPos.get())
        MPVLib.setPropertyFloat("secondary-sub-scale", subtitlesPreferences.secondarySubScale.get())
        MPVLib.setPropertyInt("secondary-sid", id)
      }
      else -> {
        MPVLib.setPropertyInt("sid", id)
        val track = tracks.firstOrNull { it.id == id }
        TrackSelector.rememberSubtitleTrack(track?.title, track?.lang, isOff = false)
      }
    }
  }

  fun isSubtitleSelected(id: Int): Boolean {
    val primarySid = MPVLib.getPropertyInt("sid") ?: 0
    val secondarySid = MPVLib.getPropertyInt("secondary-sid") ?: 0
    return (id == primarySid && primarySid > 0) || (id == secondarySid && secondarySid > 0)
  }

  fun selectAudioTrack(id: Int, title: String?, lang: String?) {
    val currentAid = MPVLib.getPropertyInt("aid") ?: 0
    if (currentAid == id) {
      MPVLib.setPropertyString("aid", "no")
      TrackSelector.rememberAudioTrack(null, null)
    } else {
      MPVLib.setPropertyInt("aid", id)
      TrackSelector.rememberAudioTrack(title, lang)
      playbackManager.resyncAudioOnTrackChange(scope)
    }
  }
}
