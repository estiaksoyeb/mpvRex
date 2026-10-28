package xyz.mpv.rex.ui.player.controls

import kotlin.math.hypot

/** Which third of the player a tap landed in, after [TapConfig.reverse] is applied. */
internal enum class TapRegion { Left, Center, Right }

/**
 * Receives the decisions made by [TapChromeEngine]. The engine never talks to mpv or to Compose
 * directly, which keeps every tap transition testable on a plain JVM.
 */
internal interface TapChromeListener {
  fun onShowChrome()

  fun onHideChrome()

  fun onAction(region: TapRegion, isSingleTap: Boolean)
}

/** Player state that is read fresh on every DOWN. The host fills this from the ViewModel. */
internal data class TapEnv(
  val chromeShown: Boolean,
  val locked: Boolean,
  val panelOrSheetOpen: Boolean,
)

internal data class TapConfig(
  val width: Float,
  val height: Float,
  val seekAreaPercent: Float = 30f,
  val reverse: Boolean = false,
  val instantCenter: Boolean = false,
  val instantSides: Boolean = false,
  val seekLeft: Boolean = true,
  val seekRight: Boolean = true,
  val doubleTapMs: Long = 300,
  val chainMs: Long = 650,
  val slop: Float = 100f,
)

/**
 * Decides what a tap does to the player chrome and which action it runs.
 *
 * Rules:
 *  - A single tap toggles the chrome on DOWN, so it feels instant.
 *  - A double tap ends with the chrome exactly as it was before the first tap.
 *  - With an instant single-tap action enabled, a tap outside the top/bottom quarter runs the action
 *    and never touches the chrome.
 *
 * Time is passed in explicitly, so a test drives the clock with its own arguments.
 */
internal class TapChromeEngine(
  private val listener: TapChromeListener,
  private val config: () -> TapConfig,
) {
  private enum class Kind { First, SecondOfDouble, Continuation, Swallowed, Locked }

  // Sequence state, survives between gestures.
  private var count = 0
  private var lastTime = 0L
  private var lastUp = 0L
  private var lastX = 0f
  private var lastY = 0f
  private var lastRegion: TapRegion? = null
  private var chainSeeking = false
  private var shownBefore = false
  private var committed = false

  // Latched at DOWN, consumed at UP.
  private var kind = Kind.First
  private var region = TapRegion.Center
  private var downTime = 0L
  private var downX = 0f
  private var downY = 0f
  private var spent = false
  private var live = false

  /** Test hook: must be exactly one per gesture sequence. */
  internal var snapshotWrites = 0
    private set

  /** Expires the double-tap window. Safe to call at any time; replaces the old cleanup effect. */
  fun tick(now: Long) {
    if (count == 1 && now - lastUp >= config().doubleTapMs) {
      count = 0
      lastRegion = null
      committed = false
    }
  }

  fun down(time: Long, x: Float, y: Float, env: TapEnv) {
    val c = config()
    tick(time)
    region = regionOf(x, c)
    downTime = time
    downX = x
    downY = y
    live = true
    val zone = c.height * 0.25f
    val inZone = y < zone || y > c.height - zone
    spent = hasInstant(region, c) && !inZone
    val near = lastRegion == region && hypot(x - lastX, y - lastY) < c.slop
    kind = when {
      env.locked -> Kind.Locked
      count == 1 && near && time - lastTime < c.doubleTapMs -> Kind.SecondOfDouble
      count >= 2 && chainSeeking && near && time - lastTime < c.chainMs -> Kind.Continuation
      count >= 2 && time - lastTime < c.doubleTapMs -> Kind.Swallowed
      else -> Kind.First
    }
    when (kind) {
      Kind.SecondOfDouble -> restore()
      Kind.First -> {
        committed = false
        chainSeeking = false
        if (!spent && !env.panelOrSheetOpen) {
          shownBefore = env.chromeShown
          snapshotWrites++
          committed = true
          if (env.chromeShown) listener.onHideChrome() else listener.onShowChrome()
        }
      }
      else -> Unit
    }
  }

  fun up(time: Long) {
    if (!live) return
    live = false
    val c = config()
    when (kind) {
      Kind.Locked -> if (spent) listener.onShowChrome()
      Kind.Swallowed -> Unit
      Kind.SecondOfDouble, Kind.Continuation -> {
        if (kind == Kind.SecondOfDouble) {
          count = 2
          chainSeeking = isSeek(region, c)
        } else {
          count++
        }
        lastTime = downTime
        lastX = downX
        lastY = downY
        listener.onAction(region, isSingleTap = false)
      }
      Kind.First -> {
        count = 1
        lastTime = downTime
        lastUp = time
        lastX = downX
        lastY = downY
        lastRegion = region
        if (spent) listener.onAction(region, isSingleTap = true)
      }
    }
  }

  /** A drag started or a long press fired on the current gesture. */
  fun cancel() {
    if (!live) return
    if (kind == Kind.First) restore()
    live = false
  }

  private fun restore() {
    if (!committed) return
    if (shownBefore) listener.onShowChrome() else listener.onHideChrome()
    committed = false
  }

  private fun regionOf(x: Float, c: TapConfig): TapRegion {
    val f = c.seekAreaPercent / 100f
    val raw = when {
      x > c.width * (1 - f) -> TapRegion.Right
      x < c.width * f -> TapRegion.Left
      else -> TapRegion.Center
    }
    return if (!c.reverse) {
      raw
    } else {
      when (raw) {
        TapRegion.Left -> TapRegion.Right
        TapRegion.Right -> TapRegion.Left
        else -> raw
      }
    }
  }

  private fun hasInstant(r: TapRegion, c: TapConfig) =
    if (r == TapRegion.Center) c.instantCenter else c.instantSides

  private fun isSeek(r: TapRegion, c: TapConfig) =
    when (r) {
      TapRegion.Left -> c.seekLeft
      TapRegion.Right -> c.seekRight
      else -> false
    }
}
