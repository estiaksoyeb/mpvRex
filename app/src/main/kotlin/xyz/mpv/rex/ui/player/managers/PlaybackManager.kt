package xyz.mpv.rex.ui.player.managers

import xyz.mpv.rex.preferences.PlayerPreferences
import `is`.xyz.mpv.MPVLib
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mpv.rex.ui.player.MPVLifecycleLock
import xyz.mpv.rex.ui.player.VideoAspect

/**
 * Manages playback operations like seeking and speed control.
 */
class PlaybackManager(
    private val playerPreferences: PlayerPreferences
) {
    companion object {
        private const val TAG = "PlaybackManager"
        private const val SEEK_COALESCE_REMOTE_MS = 150L
        private const val SEEK_COALESCE_LOCAL_MS = 40L
    }

    private var seekJob: Job? = null
    private var resyncJob: Job? = null
    @Volatile private var lastSeekAt = 0L

    private fun canAccessMpv(): Boolean =
        MPVLifecycleLock.isNativeInitialized && !MPVLifecycleLock.isTearingDown.value

    fun cancelPendingJobs() {
        seekJob?.cancel()
        seekJob = null
        resyncJob?.cancel()
        resyncJob = null
    }

    fun onPlaybackRestart() {
        ensureUnmuted()
    }

    /**
     * Ensures MPV mute property is not stuck at true from prior seek guard or race condition.
     */
    fun ensureUnmuted() {
        if (!canAccessMpv()) return
        runCatching {
            if (MPVLib.getPropertyBoolean("mute") == true) {
                MPVLib.setPropertyBoolean("mute", false)
            }
        }
    }

    /**
     * Performs an absolute seek to the specified position.
     * Clamps the position between 0 and duration, and optionally within AB loop.
     * Handles streams with undetermined duration gracefully and cancels prior in-flight seeks.
     */
    fun seekTo(scope: CoroutineScope, position: Int, abLoopA: Double?, abLoopB: Double?) {
        seekJob?.cancel()
        if (!canAccessMpv()) return
        seekJob = scope.launch(Dispatchers.IO) {
            if (!isActive || !canAccessMpv()) return@launch
            val isRemote = runCatching { MPVLib.getPropertyString("path") }.getOrNull()?.startsWith("http", ignoreCase = true) == true
            val coalesceMs = if (isRemote) SEEK_COALESCE_REMOTE_MS else SEEK_COALESCE_LOCAL_MS
            val timeSinceLastSeek = SystemClock.elapsedRealtime() - lastSeekAt
            if (timeSinceLastSeek < coalesceMs) {
                delay(coalesceMs - timeSinceLastSeek)
            }
            if (!isActive || !canAccessMpv()) return@launch
            lastSeekAt = SystemClock.elapsedRealtime()
            val maxDuration = runCatching { MPVLib.getPropertyInt("duration") }.getOrNull() ?: 0

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

            if (!isActive || !canAccessMpv()) return@launch

            // Use precise seeking only if preference is explicitly enabled or for short finite videos (1..119s)
            val shouldUsePreciseSeeking = playerPreferences.usePreciseSeeking.get() || (maxDuration in 1..119)
            val seekMode = if (shouldUsePreciseSeeking) "absolute+exact" else "absolute+keyframes"
            ensureUnmuted()
            if (!canAccessMpv()) return@launch
            runCatching { MPVLib.command("seek", clampedPosition.toString(), seekMode) }
        }
    }

    /**
     * Performs a relative seek immediately with concurrency protection and stream-safe seek modes.
     */
    fun seekBy(scope: CoroutineScope, offset: Int) {
        if (offset == 0 || !canAccessMpv()) return
        
        seekJob?.cancel()
        seekJob = scope.launch(Dispatchers.IO) {
            if (!isActive || !canAccessMpv()) return@launch
            val duration = runCatching { MPVLib.getPropertyInt("duration") }.getOrNull() ?: 0
            val currentPos = runCatching { MPVLib.getPropertyInt("time-pos") }.getOrNull() ?: 0

            if (!isActive || !canAccessMpv()) return@launch
            ensureUnmuted()
            if (!canAccessMpv()) return@launch
            if (duration > 0 && currentPos + offset >= duration) {
                // Force seek to 100% to ensure EOF is triggered
                runCatching { MPVLib.command("seek", "100", "absolute-percent+exact") }
            } else {
                val shouldUsePreciseSeeking = playerPreferences.usePreciseSeeking.get() || (duration in 1..119)
                val seekMode = if (shouldUsePreciseSeeking) "relative+exact" else "relative+keyframes"
                runCatching { MPVLib.command("seek", offset.toString(), seekMode) }
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
        if (!canAccessMpv()) return
        resyncJob = scope.launch(Dispatchers.IO) {
            delay(50)
            if (!isActive || !canAccessMpv()) return@launch
            val timePos = runCatching { MPVLib.getPropertyDouble("time-pos") }.getOrNull()
            if (timePos != null && timePos > 0.0) {
                runCatching { MPVLib.command("seek", timePos.toString(), "absolute+keyframes") }
            } else {
                runCatching { MPVLib.command("seek", "0", "relative+keyframes") }
            }
        }
    }

    fun setSpeed(speed: Float) {
        if (!canAccessMpv()) return
        runCatching { MPVLib.setPropertyFloat("speed", speed) }
    }

    fun resetSpeed() {
        setSpeed(1.0f)
    }

    fun setSubSpeed(speed: Double) {
        if (!canAccessMpv()) return
        runCatching {
            MPVLib.setPropertyDouble("sub-speed", speed)
            MPVLib.setPropertyDouble("secondary-sub-speed", speed)
        }
    }

    fun pauseUnpause(
        scope: CoroutineScope,
        onRequestAudioFocus: () -> Unit,
        onAbandonAudioFocus: () -> Unit,
    ) {
        if (!canAccessMpv()) return
        scope.launch(Dispatchers.IO) {
            if (!isActive || !canAccessMpv()) return@launch
            val isPaused = runCatching { MPVLib.getPropertyBoolean("pause") }.getOrNull() ?: false
            if (!canAccessMpv()) return@launch
            if (isPaused) {
                withContext(Dispatchers.Main) { onRequestAudioFocus() }
                if (!canAccessMpv()) return@launch
                runCatching { MPVLib.setPropertyBoolean("pause", false) }
            } else {
                runCatching { MPVLib.setPropertyBoolean("pause", true) }
                withContext(Dispatchers.Main) { onAbandonAudioFocus() }
            }
        }
    }

    fun pause(scope: CoroutineScope, onAbandonAudioFocus: () -> Unit) {
        if (!canAccessMpv()) return
        scope.launch(Dispatchers.IO) {
            if (!isActive || !canAccessMpv()) return@launch
            runCatching { MPVLib.setPropertyBoolean("pause", true) }
            withContext(Dispatchers.Main) { onAbandonAudioFocus() }
        }
    }

    fun unpause(scope: CoroutineScope, onRequestAudioFocus: () -> Unit) {
        if (!canAccessMpv()) return
        scope.launch(Dispatchers.IO) {
            if (!isActive || !canAccessMpv()) return@launch
            withContext(Dispatchers.Main) { onRequestAudioFocus() }
            if (!canAccessMpv()) return@launch
            runCatching { MPVLib.setPropertyBoolean("pause", false) }
        }
    }

    fun frameStepForward(
        scope: CoroutineScope,
        paused: Boolean?,
        onPauseUnpause: () -> Unit,
        onFrameStepped: () -> Unit,
    ) {
        if (!canAccessMpv()) return
        scope.launch(Dispatchers.IO) {
            if (paused != true) {
                onPauseUnpause()
                delay(50)
            }
            if (!isActive || !canAccessMpv()) return@launch
            runCatching { MPVLib.command("no-osd", "frame-step") }
            delay(100)
            if (!isActive || !canAccessMpv()) return@launch
            onFrameStepped()
        }
    }

    fun frameStepBackward(
        scope: CoroutineScope,
        paused: Boolean?,
        onPauseUnpause: () -> Unit,
        onFrameStepped: () -> Unit,
    ) {
        if (!canAccessMpv()) return
        scope.launch(Dispatchers.IO) {
            if (paused != true) {
                onPauseUnpause()
                delay(50)
            }
            if (!isActive || !canAccessMpv()) return@launch
            runCatching { MPVLib.command("no-osd", "frame-back-step") }
            delay(100)
            if (!isActive || !canAccessMpv()) return@launch
            onFrameStepped()
        }
    }

    fun subSeek(
        scope: CoroutineScope,
        forward: Boolean,
        onDiffCalculated: (diff: Double) -> Unit,
        onFallback: () -> Unit,
    ) {
        if (!canAccessMpv()) return
        val sid = runCatching { MPVLib.getPropertyInt("sid") }.getOrNull() ?: 0
        if (sid != 0) {
            val pos1 = runCatching { MPVLib.getPropertyDouble("time-pos") }.getOrNull() ?: 0.0
            runCatching { MPVLib.command("sub-seek", if (forward) "1" else "-1") }

            scope.launch(Dispatchers.IO) {
                delay(50)
                if (!isActive || !canAccessMpv()) return@launch
                val pos2 = runCatching { MPVLib.getPropertyDouble("time-pos") }.getOrNull() ?: pos1
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
        if (!canAccessMpv()) return
        runCatching {
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
    }

    fun applyCustomAspectRatio(ratio: Double) {
        if (!canAccessMpv()) return
        runCatching {
            MPVLib.setPropertyDouble("panscan", 0.0)
            MPVLib.setPropertyDouble("video-aspect-override", ratio)
        }
    }
}
