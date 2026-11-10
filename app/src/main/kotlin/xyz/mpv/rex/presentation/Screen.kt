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
  /** Opacity only. Nothing moves, so nothing can stretch or feel like a slide. */
  Fade,

  /** The pre-container-transform horizontal push: incoming from the trailing edge. */
  Slide,

  /** Container transform from the tapped card's bounds, shared X when there is no source. */
  ContainerTransform,
}

interface Screen : NavKey {
  /**
   * Motion used when navigating to and from this screen. The app fades by default; screens
   * that want movement override this (preferences keep the original horizontal push).
   */
  val motion: MotionStyle
    get() = MotionStyle.Fade

  /**
   * Id of the card this screen expands out of, if any (see
   * `docs/ui-design/ANIMATION_STAGES.md`). Consumed by `NavDisplay` as the entry's
   * `contentKey` so the container transform can find the source bounds. `null` means the
   * screen is entered with the shared scale + fade transition instead.
   */
  val transformSourceId: String?
    get() = null

  @Composable
  fun Content()
}
