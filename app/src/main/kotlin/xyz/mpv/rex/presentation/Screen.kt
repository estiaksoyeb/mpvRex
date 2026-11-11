package xyz.mpv.rex.presentation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import xyz.mpv.rex.R

/**
 * How a screen enters and leaves.
 *
 * Read by `NavDisplay`'s transition specs in `MainActivity`, so a screen chooses its own
 * motion by overriding [Screen.motion] or falls back to [MotionStyle.Default] to follow the
 * user's configured navigation motion preset.
 */
enum class MotionStyle(
  @StringRes val titleRes: Int,
  @StringRes val descriptionRes: Int,
) {
  /**
   * Expressive parallax: full-width slide with subtle 3D scale (0.92 -> 1.0) while
   * outgoing layer recedes with a 25% parallax shift and 0.96 scale.
   */
  ExpressiveParallax(
    titleRes = R.string.motion_preset_expressive_parallax,
    descriptionRes = R.string.motion_preset_expressive_parallax_desc,
  ),

  /**
   * Material Shared-Z: 3D spatial zoom (scale 0.88 -> 1.0) without horizontal sliding.
   */
  SharedZ(
    titleRes = R.string.motion_preset_shared_z,
    descriptionRes = R.string.motion_preset_shared_z_desc,
  ),

  /**
   * Traditional horizontal push: incoming slides in from trailing edge, outgoing pushes out.
   */
  Slide(
    titleRes = R.string.motion_preset_classic_slide,
    descriptionRes = R.string.motion_preset_classic_slide_desc,
  ),

  /**
   * Follow the user's configured navigation motion preset.
   */
  Default(
    titleRes = R.string.motion_preset_default,
    descriptionRes = R.string.motion_preset_default_desc,
  );

  companion object {
    /** Presets available in Appearance Settings and runtime selector. */
    val selectablePresets: List<MotionStyle> = listOf(
      ExpressiveParallax,
      SharedZ,
      Slide,
    )
  }
}

interface Screen : NavKey {
  /**
   * Motion used when navigating to and from this screen. Defaults to [MotionStyle.Default]
   * to follow the user's selected motion preset.
   */
  val motion: MotionStyle
    get() = MotionStyle.Default

  @Composable
  fun Content()
}
