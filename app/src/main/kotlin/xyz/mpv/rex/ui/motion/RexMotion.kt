package xyz.mpv.rex.ui.motion

import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import xyz.mpv.rex.presentation.MotionStyle

/**
 * One source of truth for app-wide motion.
 *
 * Navigation style is chosen per screen ([MotionStyle]); this object supplies the primitives:
 *  - shared X: the default push/pop (small horizontal offset + fade-through)
 *  - slide: the app's original horizontal push, used by settings
 *  - dock expand: bottom bars / sheets grow from their docked edge, instead of sliding in
 *
 * Specs come from [MotionScheme.expressive] - the same scheme the theme installs - read
 * directly rather than through `MaterialTheme.motionScheme`, because `NavDisplay` transition
 * specs are not composable.
 */
object RexMotion {
  private val scheme: MotionScheme = MotionScheme.expressive()

  /** True when the user has not turned system animations off. */
  fun animationsEnabled(context: android.content.Context): Boolean {
    val scale = try {
      Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
      )
    } catch (_: Exception) {
      1f
    }
    return scale > 0f
  }

  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  fun spatialSpec(context: android.content.Context): FiniteAnimationSpec<Float> =
    if (animationsEnabled(context)) scheme.defaultSpatialSpec() else snap()

  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  fun effectsSpec(context: android.content.Context): FiniteAnimationSpec<Float> =
    if (animationsEnabled(context)) scheme.defaultEffectsSpec() else tween(durationMillis = 0)

  /**
   * Spatial spec for size-based transitions (`expandVertically`, `shrinkHorizontally`, ...),
   * which animate `IntSize` rather than `Float`. Snaps when system animations are disabled.
   */
  @OptIn(ExperimentalMaterial3ExpressiveApi::class)
  fun sizeSpec(context: android.content.Context): FiniteAnimationSpec<IntSize> =
    if (animationsEnabled(context)) scheme.defaultSpatialSpec() else snap()

  /**
   * Pick the transition for a screen according to the style it asked for.
   *
   * [forward] describes the edge: true when the screen is arriving, false when leaving. Which
   * screen supplies the style is decided by the caller, not here. See
   * [xyz.mpv.rex.presentation.Screen.motion].
   */
  fun screenTransition(
    context: android.content.Context,
    forward: Boolean,
    style: MotionStyle,
  ): Pair<EnterTransition, ExitTransition> = when (style) {
    MotionStyle.SharedX -> sharedXTransition(context, forward = forward)
    MotionStyle.Slide -> slideTransition(context, forward = forward)
  }

  /**
   * Shared axis X: the default navigation motion.
   *
   * The incoming screen travels in from the trailing edge by [SHARED_X_OFFSET_DP] while fading
   * in; the outgoing travels the same distance towards the leading edge while fading out, and is
   * gone by [SHARED_X_OUT_MILLIS] so the content change is actually visible. A pure cross-fade
   * between two same-looking lists reads as no animation at all, which is what this replaces.
   *
   * Why X and not Y or Z: a horizontal move matches the predictive-back edge swipe and the
   * file-manager convention of horizontal depth, whereas a vertical move implies paging and
   * fights the scrolling list underneath. The offset is deliberately small - this plays on every
   * tap, so a full-width slide would be tiresome.
   *
   * The offset uses a non-bouncy spring. The expressive spatial spring overshoots, and an
   * overshoot on a full-screen translation reads as the screen wobbling.
   */
  fun sharedXTransition(
    context: android.content.Context,
    forward: Boolean,
  ): Pair<EnterTransition, ExitTransition> {
    if (!animationsEnabled(context)) return fadeTransition(context)
    // Specs are not composable, so density comes from the context rather than LocalDensity.
    val offsetPx = (SHARED_X_OFFSET_DP * context.resources.displayMetrics.density).toInt()
    val direction = if (forward) 1 else -1
    val enter = fadeIn(tween(durationMillis = SHARED_X_IN_MILLIS)) +
      slideIn(sharedXSpec) { IntOffset(direction * offsetPx, 0) }
    val exit = fadeOut(tween(durationMillis = SHARED_X_OUT_MILLIS)) +
      slideOut(sharedXSpec) { IntOffset(-direction * offsetPx, 0) }
    return enter to exit
  }

  /**
   * Non-bouncy spring for screen-sized translations (see [sharedXTransition]).
   *
   * Deliberately not read from the MotionScheme: the scheme's default spatial spec is expressive
   * and overshoots, which is fine on a button but shows up as a wobble on a whole screen.
   */
  private val sharedXSpec: FiniteAnimationSpec<IntOffset> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

  /** Horizontal travel of a shared-X screen. Small, because it plays on every tap. */
  private const val SHARED_X_OFFSET_DP = 30

  /** Outgoing fade, short so the incoming content shows through. */
  private const val SHARED_X_OUT_MILLIS = 90

  /** Incoming fade. */
  private const val SHARED_X_IN_MILLIS = 210

  /**
   * Predictive-back transition.
   *
   * Navigation 3 drives the gesture; this only decides the shape the departing screen leaves
   * with, so the gesture preview and the committed pop agree.
   */
  fun predictiveBackTransition(
    context: android.content.Context,
    style: MotionStyle,
  ): Pair<EnterTransition, ExitTransition> =
    screenTransition(context, forward = false, style = style)

  /**
   * Opacity-only transition, for screens where movement of any kind is unwanted (see
   * [xyz.mpv.rex.presentation.MotionStyle.Fade]).
   *
   * Deliberately no scale and no translation: nothing resizes, so text cannot stretch and the
   * screen cannot read as sliding. Both screens fade together, which keeps the change legible
   * without implying a direction that is not there.
   */
  fun fadeTransition(context: android.content.Context): Pair<EnterTransition, ExitTransition> {
    val effects = effectsSpec(context)
    return fadeIn(effects) to fadeOut(effects)
  }

  /**
   * Horizontal push, matching the app's navigation before the container transform landed
   * (see [xyz.mpv.rex.presentation.MotionStyle.Slide]). Incoming arrives from the trailing
   * edge, outgoing leaves towards the leading edge; popped the other way round.
   *
   * The duration is fixed rather than read from the MotionScheme so this reproduces the
   * original motion; reduced motion still snaps.
   */
  fun slideTransition(
    context: android.content.Context,
    forward: Boolean,
  ): Pair<EnterTransition, ExitTransition> {
    if (!animationsEnabled(context)) return fadeTransition(context)
    val spec = tween<IntOffset>(durationMillis = SLIDE_MILLIS)
    val effects = tween<Float>(durationMillis = SLIDE_MILLIS)
    val direction = if (forward) 1 else -1
    val enter = fadeIn(effects) + slideIn(spec) { size -> IntOffset(direction * size.width / 2, 0) }
    val exit = fadeOut(effects) + slideOut(spec) { size -> IntOffset(-direction * size.width / 2, 0) }
    return enter to exit
  }

  /** Push duration used by [slideTransition]; matches the animation this replaced. */
  private const val SLIDE_MILLIS = 220

  /**
   * Entrance for docked chrome (selection bars, mini player, floating bars).
   *
   * The element grows out of the edge it is docked to instead of travelling across the
   * screen, so it reads as attached to the surface rather than flying in. Same axis in and
   * out, which is what the mini player previously got wrong (up in, sideways out).
   */
  fun dockEnter(
    context: android.content.Context,
    dockedToBottom: Boolean = true,
  ): EnterTransition {
    val animation = sizeSpec(context)
    val effects = effectsSpec(context)
    return expandVertically(
      animationSpec = animation,
      expandFrom = if (dockedToBottom) Alignment.Bottom else Alignment.Top,
    ) + fadeIn(effects)
  }

  /** Exit matching [dockEnter]: shrinks back into the docked edge. */
  fun dockExit(
    context: android.content.Context,
    dockedToBottom: Boolean = true,
  ): ExitTransition {
    val animation = sizeSpec(context)
    val effects = effectsSpec(context)
    return shrinkVertically(
      animationSpec = animation,
      shrinkTowards = if (dockedToBottom) Alignment.Bottom else Alignment.Top,
    ) + fadeOut(effects)
  }

  /**
   * Reveal for player side panels.
   *
   * Panels are anchored to a screen edge, so they unfold from that edge instead of sliding
   * the full screen width: a short horizontal expand plus a small scale. Full-width travel
   * read as a new screen arriving on top of the video.
   *
   * @param fromEnd true when the panel is docked to the trailing edge
   */
  fun panelEnter(
    context: android.content.Context,
    fromEnd: Boolean = true,
  ): EnterTransition {
    val animation = sizeSpec(context)
    val effects = effectsSpec(context)
    return expandHorizontally(
      animationSpec = animation,
      expandFrom = if (fromEnd) Alignment.End else Alignment.Start,
    ) +
      scaleIn(animationSpec = spatialSpec(context), initialScale = 0.96f) +
      fadeIn(effects)
  }

  /** Exit matching [panelEnter]. */
  fun panelExit(
    context: android.content.Context,
    fromEnd: Boolean = true,
  ): ExitTransition {
    val animation = sizeSpec(context)
    val effects = effectsSpec(context)
    return shrinkHorizontally(
      animationSpec = animation,
      shrinkTowards = if (fromEnd) Alignment.End else Alignment.Start,
    ) +
      scaleOut(animationSpec = spatialSpec(context), targetScale = 0.96f) +
      fadeOut(effects)
  }
}
