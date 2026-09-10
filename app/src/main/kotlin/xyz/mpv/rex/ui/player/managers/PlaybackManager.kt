package xyz.mpv.rex.ui.player.managers

import xyz.mpv.rex.preferences.PlayerPreferences
import `is`.xyz.mpv.MPVLib
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mpv.rex.ui.player.VideoAspect

/**
 * Manages playback operations like seeking and speed control.
 */
class PlaybackManager(
    private val playerPreferences: PlayerPreferences
) {
    companion object {
        private const val TAG = "PlaybackManager"
        private const val SEEK_COALESCE_MS = 150L
        private const val SEEK_AUDIO_RESTORE_DELAY_MS = 60L
        private const val SEEK_AUDIO_FALLBACK_RESTORE_MS = 1500L
    }

    private var seekJob: Job? = null
    private var resyncJob: Job? = null
    @Volatile private var lastSeekAt = 0L

    private var seekAudioGuardPreviousMute: Boolean? = null
    private var seekAudioGuardToken = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Briefly mutes audio during seeking transitions to protect Android AudioTrack
     * from buffer underruns, stale frames, or audio track failure.
     */
    private fun beginSeekAudioGuard() {
        val isPaused = MPVLib.getPropertyBoolean("pause") ?: false
        if (isPaused) return

        if (seekAudioGuardPreviousMute == null) {
            val wasMuted = MPVLib.getPropertyBoolean("mute") ?: false
            seekAudioGuardPreviousMute = wasMuted
            if (!wasMuted) {
                runCatching { MPVLib.setPropertyBoolean("mute", true) }
            }
        }
        seekAudioGuardToken++
        scheduleSeekAudioGuardRestore(SEEK_AUDIO_FALLBACK_RESTORE_MS)
    }

    fun onPlaybackRestart() {
        if (seekAudioGuardPreviousMute != null) {
            scheduleSeekAudioGuardRestore(SEEK_AUDIO_RESTORE_DELAY_MS)
        }
    }

    private fun scheduleSeekAudioGuardRestore(delayMs: Long) {
        if (seekAudioGuardPreviousMute == null) return
        val token = seekAudioGuardToken
        mainHandler.postDelayed({
            if (token != seekAudioGuardToken) return@postDelayed
            val previousMute = seekAudioGuardPreviousMute
            seekAudioGuardPreviousMute = null
            seekAudioGuardToken++
            if (previousMute != null) {
                runCatching { MPVLib.setPropertyBoolean("mute", previousMute) }
            }
        }, delayMs)
    }

    /**
     * Performs an absolute seek to the specified position.
     * Clamps the position between 0 and duration, and optionally within AB loop.
     * Handles streams with undetermined duration gracefully and cancels prior in-flight seeks.
     */
    fun seekTo(scope: CoroutineScope, position: Int, abLoopA: Double?, abLoopB: Double?) {
        seekJob?.cancel()
        seekJob = scope.launch(Dispatchers.IO) {
            val isRemote = MPVLib.getPropertyString("path")?.startsWith("http", ignoreCase = true) == true
            if (isRemote && SystemClock.elapsedRealtime() - lastSeekAt < SEEK_COALESCE_MS) {
                delay(SEEK_COALESCE_MS)
            }
            lastSeekAt = SystemClock.elapsedRealtime()
            val maxDuration = MPVLib.getPropertyInt("duration") ?: 0

            var clampedPosition = position
            if (abLoopA != null && abLoopB != null) {
                val min = minOf(abLoopA.toInt(), abLoopB.toInt())
                val max = maxOf(abLoopA.toInt(), abLoopB.toInt())
                clampedPosition = clampedPosition.coerceIn(min, max)
            }

            if (maxDuration > 0) {
                if (clampedPosition !in 0..maxDuration) return@launch
            } else {
                if (clampedPosition < 0) return@launch
            }

            // Use precise seeking only if preference is explicitly enabled or for short finite videos (1..119s)
            val shouldUsePreciseSeeking = playerPreferences.usePreciseSeeking.get() || (maxDuration in 1..119)
            val seekMode = if (shouldUsePreciseSeeking) "absolute+exact" else "absolute+keyframes"
            beginSeekAudioGuard()
            MPVLib.command("seek", clampedPosition.toString(), seekMode)
        }
    }

    /**
     * Performs a relative seek immediately with concurrency protection and stream-safe seek modes.
     */
    fun seekBy(scope: CoroutineScope, offset: Int) {
        if (offset == 0) return
        
        seekJob?.cancel()
        seekJob = scope.launch(Dispatchers.IO) {
            val duration = MPVLib.getPropertyInt("duration") ?: 0
            val currentPos = MPVLib.getPropertyInt("time-pos") ?: 0

            beginSeekAudioGuard()
            if (duration > 0 && currentPos + offset >= duration) {
                // Force seek to 100% to ensure EOF is triggered
                MPVLib.command("seek", "100", "absolute-percent+exact")
            } else {
                val shouldUsePreciseSeeking = playerPreferences.usePreciseSeeking.get() || (duration in 1..119)
                val seekMode = if (shouldUsePreciseSeeking) "relative+exact" else "relative+keyframes"
                MPVLib.command("seek", offset.toString(), seekMode)
            }
        }
    }

    /**
     * Resynchronizes audio and video demuxer streams after an audio track change.
     * Prevents audio muting and buffer starvation on network streams by flushing the demuxer queues
     * and aligning the audio presentation timestamp (PTS) with the master clock.
     */
    fun resyncAudioOnTrackChange(scope: CoroutineScope) {
        resyncJob?.cancel()
        resyncJob = scope.launch(Dispatchers.IO) {
            delay(50)
            val timePos = MPVLib.getPropertyDouble("time-pos")
            if (timePos != null && timePos > 0.0) {
                MPVLib.command("seek", timePos.toString(), "absolute+keyframes")
            } else {
                MPVLib.command("seek", "0", "relative+keyframes")
            }
        }
    }

    fun setSpeed(speed: Float) {
        MPVLib.setPropertyFloat("speed", speed)
    }

    fun resetSpeed() {
        setSpeed(1.0f)
    }

    fun setSubSpeed(speed: Double) {
        MPVLib.setPropertyDouble("sub-speed", speed)
        MPVLib.setPropertyDouble("secondary-sub-speed", speed)
    }

    fun pauseUnpause(
        scope: CoroutineScope,
        onRequestAudioFocus: () -> Unit,
        onAbandonAudioFocus: () -> Unit,
    ) {
        scope.launch(Dispatchers.IO) {
            val isPaused = MPVLib.getPropertyBoolean("pause") ?: false
            if (isPaused) {
                withContext(Dispatchers.Main) { onRequestAudioFocus() }
                MPVLib.setPropertyBoolean("pause", false)
            } else {
                MPVLib.setPropertyBoolean("pause", true)
                withContext(Dispatchers.Main) { onAbandonAudioFocus() }
            }
        }
    }

    fun pause(scope: CoroutineScope, onAbandonAudioFocus: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            MPVLib.setPropertyBoolean("pause", true)
            withContext(Dispatchers.Main) { onAbandonAudioFocus() }
        }
    }

    fun unpause(scope: CoroutineScope, onRequestAudioFocus: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { onRequestAudioFocus() }
            MPVLib.setPropertyBoolean("pause", false)
        }
    }

    fun frameStepForward(
        scope: CoroutineScope,
        paused: Boolean?,
        onPauseUnpause: () -> Unit,
        onFrameStepped: () -> Unit,
    ) {
        scope.launch(Dispatchers.IO) {
            if (paused != true) {
                onPauseUnpause()
                delay(50)
            }
            MPVLib.command("no-osd", "frame-step")
            delay(100)
            onFrameStepped()
        }
    }

    fun frameStepBackward(
        scope: CoroutineScope,
        paused: Boolean?,
        onPauseUnpause: () -> Unit,
        onFrameStepped: () -> Unit,
    ) {
        scope.launch(Dispatchers.IO) {
            if (paused != true) {
                onPauseUnpause()
                delay(50)
            }
            MPVLib.command("no-osd", "frame-back-step")
            delay(100)
            onFrameStepped()
        }
    }

    fun subSeek(
        scope: CoroutineScope,
        forward: Boolean,
        onDiffCalculated: (diff: Double) -> Unit,
        onFallback: () -> Unit,
    ) {
        val sid = MPVLib.getPropertyInt("sid") ?: 0
        if (sid != 0) {
            val pos1 = MPVLib.getPropertyDouble("time-pos") ?: 0.0
            MPVLib.command("sub-seek", if (forward) "1" else "-1")

            scope.launch(Dispatchers.IO) {
                delay(50)
                val pos2 = MPVLib.getPropertyDouble("time-pos") ?: pos1
                val diff = pos2 - pos1
                onDiffCalculated(diff)
            }
        } else {
            onFallback()
        }
    }

    fun applyVideoAspect(
        aspect: VideoAspect,
        screenWidth: Int,
        screenHeight: Int,
        videoRotation: Int,
    ) {
        when (aspect) {
            VideoAspect.Fit -> {
                MPVLib.setPropertyDouble("panscan", 0.0)
                MPVLib.setPropertyDouble("video-aspect-override", -1.0)
            }
            VideoAspect.Crop -> {
                MPVLib.setPropertyDouble("video-aspect-override", -1.0)
                MPVLib.setPropertyDouble("panscan", 1.0)
            }
            VideoAspect.Stretch -> {
                val isVideoRotated = (videoRotation % 180 == 90)
                val screenRatio = if (isVideoRotated) {
                    screenHeight.toDouble() / screenWidth.toDouble()
                } else {
                    screenWidth.toDouble() / screenHeight.toDouble()
                }
                MPVLib.setPropertyDouble("video-aspect-override", screenRatio)
                MPVLib.setPropertyDouble("panscan", 0.0)
            }
        }
    }

    fun applyCustomAspectRatio(ratio: Double) {
        MPVLib.setPropertyDouble("panscan", 0.0)
        MPVLib.setPropertyDouble("video-aspect-override", ratio)
    }
}
