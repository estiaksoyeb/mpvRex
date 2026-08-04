package xyz.mpv.rex.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Xml
import android.view.ViewGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.mpv.rex.R
import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerStateManager
import xyz.mpv.rex.utils.media.MediaThumbnailUtils
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.xmlpull.v1.XmlPullParser
import java.io.File

/**
 * Headless MPV playback controller for direct mini player mode.
 *
 * Creates an off-window [MPVView] and plays audio-first with `vo=null`, so tapping a
 * media item starts playback in the bottom mini player bar WITHOUT ever launching
 * [PlayerActivity] — eliminating the window-transition flicker.
 *
 * When the user expands the mini player, [PlayerActivity] launches with
 * `attach_existing_session=true` and takes over the (still-running) global MPV instance.
 * At that point this controller relinquishes ownership via [detachForHandoff] WITHOUT
 * destroying MPV.
 *
 * ## Ownership Model
 * Exactly ONE of {HeadlessPlaybackController, PlayerActivity} owns the global MPV instance
 * and is responsible for [MPVLib.destroy]. `MPVLib` is a process-global native singleton, so
 * only one `MPVLib.create()` may be live at a time.
 */
class HeadlessPlaybackController(private val appContext: Context) : KoinComponent {
  private val miniPlayerStateManager: MiniPlayerStateManager by inject()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  private var mpvView: MPVView? = null

  @Volatile
  var isSessionActive: Boolean = false
    private set

  // Metadata for handoff to PlayerActivity.
  @Volatile
  var activeUris: List<Uri> = emptyList()
    private set

  @Volatile
  var activeIndex: Int = 0
    private set

  @Volatile
  var activeTitle: String = ""
    private set

  private var resumeObserver: MPVLib.EventObserver? = null

  /**
   * Starts headless playback of [uris] beginning at [startIndex].
   *
   * @param resumePositionSec position (seconds) to resume the first file at, or 0 to start fresh.
   */
  fun startHeadless(
    uris: List<Uri>,
    startIndex: Int,
    title: String,
    artist: String = "",
    resumePositionSec: Int = 0,
  ) {
    if (uris.isEmpty() || startIndex < 0 || startIndex >= uris.size) {
      Log.w(TAG, "startHeadless: invalid uris/startIndex")
      return
    }

    PlayerActivity.finishBackgroundInstance()

    activeUris = uris
    activeIndex = startIndex
    activeTitle = title

    // Set handlers for swipe/button next/previous in MiniPlayer
    miniPlayerStateManager.onNextHandler = { playNext() }
    miniPlayerStateManager.onPreviousHandler = { playPrevious() }

    val isMpvNativeInitialized = MPVLifecycleLock.isNativeInitialized

    // A session is already running under this controller or native MPV is alive: reuse live MPV.
    if ((isSessionActive && mpvView != null) || isMpvNativeInitialized) {
      Log.d(TAG, "startHeadless: reusing live MPV instance")
      if (mpvView == null) {
        val view = createOffWindowView()
        mpvView = view
        view.attachToExistingSession()
        MPVLib.setPropertyString("vo", "null")
      }
      isSessionActive = true
      playItem(startIndex, resumePositionSec)
      startService(activeTitle, artist)
      return
    }

    scope.launch {
      // Prepare config/scripts off the main thread BEFORE MPV init (mpv loads scripts on init).
      withContext(Dispatchers.IO) {
        runCatching { MpvConfigSync.prepare(appContext) }
          .onFailure { e -> Log.e(TAG, "Config prepare failed", e) }
      }

      val view = createOffWindowView()
      mpvView = view

      runCatching {
        view.initialize(appContext.filesDir.path, appContext.cacheDir.path)
        // Audio-first: no surface exists off-window, so disable video output.
        MPVLib.setPropertyString("vo", "null")
      }.onFailure { e ->
        Log.e(TAG, "MPV initialize failed", e)
        runCatching { view.destroy() }
        mpvView = null
        return@launch
      }

      isSessionActive = true
      playItem(startIndex, resumePositionSec)
      startService(activeTitle, artist)
      Log.d(TAG, "Headless session started: $title")
    }
  }

  fun playNext() {
    if (activeUris.isEmpty()) return
    val nextIndex = activeIndex + 1
    if (nextIndex in activeUris.indices) {
      playItem(nextIndex)
    }
  }

  fun playPrevious() {
    if (activeUris.isEmpty()) return
    val prevIndex = activeIndex - 1
    if (prevIndex in activeUris.indices) {
      playItem(prevIndex)
    }
  }

  fun playItem(index: Int, resumePositionSec: Int = 0) {
    if (index !in activeUris.indices) return
    activeIndex = index
    val uri = activeUris[index]
    val title = deriveTitle(uri)
    activeTitle = title

    val playable = uri.resolveUri(appContext) ?: uri.toString()
    runCatching { MPVLib.command("loadfile", playable) }
    runCatching { MPVLib.setPropertyBoolean("pause", false) }

    scheduleResume(resumePositionSec)
    updateStateAndMetadata(index)
  }

  private fun updateStateAndMetadata(index: Int) {
    val currentUri = activeUris[index]
    val currentTitle = deriveTitle(currentUri)

    val hasNextItem = index < activeUris.size - 1
    val hasPrevItem = index > 0

    val nextUri = if (hasNextItem) activeUris[index + 1] else null
    val prevUri = if (hasPrevItem) activeUris[index - 1] else null

    val nextTitle = nextUri?.let { deriveTitle(it) }
    val prevTitle = prevUri?.let { deriveTitle(it) }

    miniPlayerStateManager.updateState(
      isPlaybackActive = true,
      title = currentTitle,
      videoPath = currentUri.toString(),
      hasNext = hasNextItem,
      hasPrevious = hasPrevItem,
      nextTitle = nextTitle,
      prevTitle = prevTitle,
      nextThumbnail = null,
      prevThumbnail = null,
    )

    scope.launch(Dispatchers.IO) {
      val mainThumb = MediaThumbnailUtils.extractThumbnailOrCoverArt(appContext, currentUri)
      val nextThumb = nextUri?.let { MediaThumbnailUtils.extractThumbnailOrCoverArt(appContext, it) }
      val prevThumb = prevUri?.let { MediaThumbnailUtils.extractThumbnailOrCoverArt(appContext, it) }

      MediaPlaybackService.thumbnail = mainThumb
      withContext(Dispatchers.Main) {
        miniPlayerStateManager.updateState(
          thumbnail = mainThumb,
          nextThumbnail = nextThumb,
          prevThumbnail = prevThumb,
        )
      }
    }
  }

  /** Seeks to [resumePositionSec] once the first file finishes loading, then self-detaches. */
  private fun scheduleResume(resumePositionSec: Int) {
    resumeObserver?.let { runCatching { MPVLib.removeObserver(it) } }
    resumeObserver = null
    if (resumePositionSec <= 3) return

    val observer = object : MPVLib.EventObserver {
      override fun event(eventId: Int, data: MPVNode) {
        if (eventId == MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED) {
          runCatching { MPVLib.setPropertyInt("time-pos", resumePositionSec) }
          val self = this
          scope.launch {
            runCatching { MPVLib.removeObserver(self) }
            if (resumeObserver === self) resumeObserver = null
          }
        }
      }
      override fun eventProperty(property: String) {}
      override fun eventProperty(property: String, value: Long) {}
      override fun eventProperty(property: String, value: Boolean) {}
      override fun eventProperty(property: String, value: String) {}
      override fun eventProperty(property: String, value: Double) {}
      override fun eventProperty(property: String, value: MPVNode) {}
    }
    resumeObserver = observer
    runCatching { MPVLib.addObserver(observer) }
  }

  private fun deriveTitle(uri: Uri): String {
    return if (uri.scheme == "file") {
      File(uri.path ?: "").name.ifBlank { uri.lastPathSegment ?: "Media" }
    } else {
      uri.lastPathSegment ?: "Media"
    }
  }

  private fun startService(title: String, artist: String) {
    MediaPlaybackService.createNotificationChannel(appContext)
    val intent = Intent(appContext, MediaPlaybackService::class.java).apply {
      putExtra("media_title", title)
      putExtra("media_artist", artist)
    }
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        appContext.startForegroundService(intent)
      } else {
        appContext.startService(intent)
      }
    }.onFailure { e -> Log.e(TAG, "Failed to start MediaPlaybackService", e) }
  }

  /**
   * Relinquishes ownership of the live MPV instance to [PlayerActivity] WITHOUT tearing it down.
   * Called just before launching the full-screen player so playback continues seamlessly.
   */
  fun detachForHandoff() {
    resumeObserver?.let { runCatching { MPVLib.removeObserver(it) } }
    resumeObserver = null
    miniPlayerStateManager.onNextHandler = null
    miniPlayerStateManager.onPreviousHandler = null
    // Drop our surface callback but keep the global MPV instance alive for PlayerActivity.
    mpvView?.let { runCatching { it.holder.removeCallback(it) } }
    mpvView = null
    isSessionActive = false
    Log.d(TAG, "Detached headless session for handoff")
  }

  /** Fully stops headless playback and destroys the MPV instance owned by this controller. */
  fun stop() {
    resumeObserver?.let { runCatching { MPVLib.removeObserver(it) } }
    resumeObserver = null
    miniPlayerStateManager.onNextHandler = null
    miniPlayerStateManager.onPreviousHandler = null
    mpvView?.let { runCatching { it.destroy() } }
    mpvView = null
    isSessionActive = false
    activeUris = emptyList()
    activeIndex = 0
    activeTitle = ""
    runCatching { appContext.stopService(Intent(appContext, MediaPlaybackService::class.java)) }
    Log.d(TAG, "Headless session stopped")
  }

  private fun createOffWindowView(): MPVView {
    val parser = appContext.resources.getLayout(R.layout.shorts_dummy_layout)
    var type: Int
    while (parser.next().also { type = it } != XmlPullParser.START_TAG &&
      type != XmlPullParser.END_DOCUMENT
    ) { /* advance to first tag */ }
    val attrs = Xml.asAttributeSet(parser)
    return MPVView(appContext, attrs).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
      )
    }
  }

  companion object {
    private const val TAG = "HeadlessPlayback"
  }
}
