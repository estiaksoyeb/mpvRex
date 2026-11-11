package xyz.mpv.rex.presentation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey

/**
 * How a screen enters and leaves.
 *
 * Read by `NavDisplay`'s transition specs in `MainActivity`, so a screen chooses its own
 * motion by overriding [Screen.motion] instead of editing the navigation code.
 */
enum class MotionStyle {
  /**
   * Default: a short horizontal offset plus a fade-through. Directional, reversible, and it
   * matches the predictive-back edge swipe.
   */
  SharedX,

  /** The app's original horizontal push: incoming slides in from the trailing edge. */
  Slide,
}

interface Screen : NavKey {
  /**
   * Motion used when navigating to and from this screen. The app uses [MotionStyle.SharedX] by
   * default; screens that want to feel different override this (settings keep the full slide).
   */
  val motion: MotionStyle
    get() = MotionStyle.SharedX

  @Composable
  fun Content()
}
