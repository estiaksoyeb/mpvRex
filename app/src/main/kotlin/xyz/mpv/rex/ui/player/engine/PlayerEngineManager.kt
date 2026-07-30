package xyz.mpv.rex.ui.player.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.SurfaceHolder
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.mpv.rex.preferences.PlayerPreferences
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

import xyz.mpv.rex.ui.player.MPVLifecycleLock
import kotlinx.coroutines.Job
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager

const val ACTION_REATTACH_SESSION = "xyz.mpv.rex.action.REATTACH_SESSION"
const val ACTION_PLAY_NEW_FILE = "xyz.mpv.rex.action.PLAY_NEW_FILE"

/**
 * State of the SSOT MPV Player Engine.
 */
enum class EngineState {
    IDLE,
    FOREGROUND_PLAYING,
    BACKGROUND_PLAYING,
    CONFIG_CHANGING,
    TEARDOWN
}

/**
 * Single Source of Truth (SSOT) Player Engine Manager.
 *
 * Centralizes native MPVLib C++ handle lifecycle management and surface attachment/detachment.
 * Provides ANR-safe surface lock acquisition with generation versioning and StateFlow status streams.
 */
class PlayerEngineManager(
    private val context: Context,
    private val playerPreferences: PlayerPreferences,
) {
    companion object {
        private const val TAG = "PlayerEngineManager"
        private const val LOCK_TIMEOUT_MS = 500L
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var isNoisyReceiverRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.d(TAG, "Audio becoming noisy - pausing MPV playback")
                withEngineLock {
                    MPVLib.setPropertyBoolean("pause", true)
                }
            }
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost - pausing playback")
                withEngineLock {
                    MPVLib.setPropertyBoolean("pause", true)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus transient loss ducking - lowering volume")
                withEngineLock {
                    MPVLib.command("multiply", "volume", "0.5")
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                withEngineLock {
                    MPVLib.command("multiply", "volume", "2.0")
                }
            }
        }
    }

    fun registerSystemAudioListeners() {
        if (!isNoisyReceiverRegistered) {
            runCatching {
                val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                context.registerReceiver(noisyReceiver, filter)
                isNoisyReceiverRegistered = true
                Log.d(TAG, "Audio becoming noisy receiver registered")
            }
        }
    }

    fun unregisterSystemAudioListeners() {
        if (isNoisyReceiverRegistered) {
            runCatching {
                context.unregisterReceiver(noisyReceiver)
                isNoisyReceiverRegistered = false
                Log.d(TAG, "Audio becoming noisy receiver unregistered")
            }
        }
    }

    /**
     * Handles incoming intent contracts.
     */
    fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            ACTION_REATTACH_SESSION -> {
                Log.d(TAG, "Handling ACTION_REATTACH_SESSION")
                if (_engineState.value == EngineState.BACKGROUND_PLAYING || _engineState.value == EngineState.IDLE) {
                    _engineState.value = EngineState.FOREGROUND_PLAYING
                }
            }
            ACTION_PLAY_NEW_FILE, Intent.ACTION_VIEW -> {
                Log.d(TAG, "Handling ACTION_PLAY_NEW_FILE / ACTION_VIEW")
                intent.data?.let { uri ->
                    _currentMediaUri.value = uri
                }
                _engineState.value = EngineState.FOREGROUND_PLAYING
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val nativeEngineLock = ReentrantLock()

    @Volatile private var currentSurfaceGeneration = 0L
    @Volatile private var pendingDetachGeneration: Long? = null

    @Volatile private var isEngineInitialized = false
    @Volatile private var isEngineDestroyed = false

    // Reactive StateFlows
    private val _engineState = MutableStateFlow(EngineState.IDLE)
    val engineState: StateFlow<EngineState> = _engineState.asStateFlow()

    private val _currentMediaUri = MutableStateFlow<Uri?>(null)
    val currentMediaUri: StateFlow<Uri?> = _currentMediaUri.asStateFlow()

    private val _playlistState = MutableStateFlow<List<String>>(emptyList())
    val playlistState: StateFlow<List<String>> = _playlistState.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    @Volatile private var destroyJob: Job? = null

    /**
     * Awaits any active native engine teardown synchronously (safe for main thread during activity setup).
     */
    fun awaitTeardownSync() {
        if (!MPVLifecycleLock.isTearingDown.value && destroyJob?.isActive != true) return
        Log.d(TAG, "Awaiting in-flight MPV engine teardown completion...")
        runCatching {
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                destroyJob?.join()
                MPVLifecycleLock.awaitTeardown()
            }
        }
        Log.d(TAG, "MPV engine teardown wait finished")
    }

    /**
     * Non-blockingly suspends until any active native engine teardown completes.
     */
    suspend fun awaitTeardown() {
        if (!MPVLifecycleLock.isTearingDown.value && destroyJob?.isActive != true) return
        Log.d(TAG, "Awaiting in-flight MPV engine teardown completion (suspend)...")
        destroyJob?.join()
        MPVLifecycleLock.awaitTeardown()
        Log.d(TAG, "MPV engine teardown wait finished")
    }

    /**
     * Executes a native command guarded by [nativeEngineLock] with timeout to prevent main thread ANRs.
     */
    fun <T> withEngineLock(timeoutMs: Long = LOCK_TIMEOUT_MS, block: () -> T): T? {
        val acquired = runCatching {
            nativeEngineLock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)
        }.getOrDefault(false)
        if (!acquired) {
            Log.e(TAG, "withEngineLock timed out waiting for nativeEngineLock (${timeoutMs}ms)")
            return null
        }
        try {
            if (isEngineDestroyed) return null
            val result = block()
            val pendingGen = pendingDetachGeneration
            if (pendingGen != null && pendingGen == currentSurfaceGeneration && !isEngineDestroyed) {
                pendingDetachGeneration = null
                Log.i(TAG, "Deferred surface detachment executed for generation $pendingGen")
                MPVLib.setPropertyString("vo", "null")
                MPVLib.detachSurface()
            }
            return result
        } finally {
            nativeEngineLock.unlock()
        }
    }

    /**
     * Initializes the MPV library if not already initialized, waiting for any teardown first.
     */
    fun initializeEngineIfNeeded(timeoutMs: Long = LOCK_TIMEOUT_MS): Boolean {
        awaitTeardownSync()
        val acquired = runCatching {
            nativeEngineLock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)
        }.getOrDefault(false)
        if (!acquired) {
            Log.e(TAG, "initializeEngineIfNeeded timed out waiting for nativeEngineLock (${timeoutMs}ms)")
            return false
        }
        try {
            if (isEngineInitialized && !isEngineDestroyed) return true
            Log.d(TAG, "Initializing MPV engine")
            isEngineDestroyed = false
            isEngineInitialized = true
            registerSystemAudioListeners()
            _engineState.value = EngineState.IDLE
            return true
        } finally {
            nativeEngineLock.unlock()
        }
    }

    /**
     * Attaches a SurfaceHolder surface safely with generation versioning and timeout lock.
     */
    fun attachSurface(holder: SurfaceHolder, timeoutMs: Long = LOCK_TIMEOUT_MS): Boolean {
        awaitTeardownSync()
        val acquired = runCatching {
            nativeEngineLock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)
        }.getOrDefault(false)
        if (!acquired) {
            Log.e(TAG, "attachSurface timed out waiting for nativeEngineLock (${timeoutMs}ms)")
            return false
        }
        try {
            currentSurfaceGeneration++
            pendingDetachGeneration = null // Clear stale pending detachment
            Log.d(TAG, "Attaching surface for generation $currentSurfaceGeneration")
            if (!isEngineDestroyed) {
                MPVLib.attachSurface(holder.surface)
                MPVLib.setPropertyString("vo", "gpu")
                if (_engineState.value == EngineState.IDLE || _engineState.value == EngineState.CONFIG_CHANGING) {
                    _engineState.value = EngineState.FOREGROUND_PLAYING
                }
            }
            return true
        } finally {
            nativeEngineLock.unlock()
        }
    }

    /**
     * Synchronously attempts to detach surface with 500ms timeout to avoid main thread ANRs.
     */
    fun detachSurfaceSyncSafe(): Boolean {
        val genAtDetachStart = currentSurfaceGeneration
        val acquired = nativeEngineLock.tryLock(LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!acquired) {
            Log.e(TAG, "CRITICAL: nativeEngineLock tryLock(${LOCK_TIMEOUT_MS}ms) timed out! Pending detach for gen $genAtDetachStart")
            pendingDetachGeneration = genAtDetachStart
            return false
        }
        try {
            if (isEngineDestroyed) return false
            Log.d(TAG, "Detaching surface synchronously for generation $genAtDetachStart")
            MPVLib.setPropertyString("vo", "null")
            MPVLib.detachSurface()
            return true
        } finally {
            nativeEngineLock.unlock()
        }
    }

    /**
     * Update current state of engine.
     */
    fun updateState(newState: EngineState) {
        _engineState.value = newState
    }

    /**
     * Updates current media URI.
     */
    fun setCurrentMediaUri(uri: Uri?) {
        _currentMediaUri.value = uri
    }

    /**
     * Updates current playlist state.
     */
    fun setPlaylist(items: List<String>) {
        _playlistState.value = items
    }

    /**
     * Updates current playback position in milliseconds.
     */
    fun updatePosition(posMs: Long) {
        _positionMs.value = posMs
    }

    /**
     * Updates total duration in milliseconds.
     */
    fun updateDuration(durMs: Long) {
        _durationMs.value = durMs
    }

    /**
     * Asynchronously tears down the native engine off the main thread.
     */
    fun destroyEngineAsync(reason: String = "user_action", onComplete: (() -> Unit)? = null): Job {
        MPVLifecycleLock.onTeardownStart()
        val job = scope.launch(Dispatchers.IO) {
            try {
                destroyEngineSyncInternal(reason)
            } finally {
                onComplete?.invoke()
            }
        }
        destroyJob = job
        return job
    }

    /**
     * Synchronously destroys native MPV engine resources (safe for IO dispatcher or onTaskRemoved).
     */
    fun destroyEngineSyncInternal(reason: String = "user_action") {
        MPVLifecycleLock.onTeardownStart()
        try {
            nativeEngineLock.withLock {
                if (isEngineDestroyed) return
                Log.i(TAG, "Destroying MPV Engine synchronously. Reason: $reason")
                _engineState.value = EngineState.TEARDOWN
                unregisterSystemAudioListeners()
                runCatching {
                    MPVLib.setPropertyString("vo", "null")
                    MPVLib.detachSurface()
                    MPVLib.destroy()
                }.onFailure { e ->
                    Log.e(TAG, "Error during MPVLib native destroy", e)
                }
                isEngineInitialized = false
                isEngineDestroyed = true
                _currentMediaUri.value = null
                _positionMs.value = 0L
                _durationMs.value = 0L
                _engineState.value = EngineState.IDLE
            }
        } finally {
            MPVLifecycleLock.onTeardownComplete()
        }
    }
}
