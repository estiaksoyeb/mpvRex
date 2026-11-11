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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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

  /**
   * Live-tunable motion parameters.
   * Can be altered on the fly by the Motion Tuning preferences without rebuilding.
   */
  @Volatile
  var tuning: MotionTuning = MotionTuning()

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
    MotionStyle.ExpressiveParallax, MotionStyle.Default -> expressiveParallaxTransition(context, forward = forward)
    MotionStyle.SharedZ -> sharedZTransition(context, forward = forward)
    MotionStyle.Slide -> slideTransition(context, forward = forward)
  }

  /**
   * Expressive parallax: full-width slide with subtle 3D scale (0.92 -> 1.0) while
   * outgoing layer recedes with a 25% parallax shift and 0.96 scale.
   *
   * Inspired by modern fluid navigation: incoming screen glides across while the outgoing
   * screen stays visible behind as an anchor layer rather than sliding all the way off
   * or fading to black.
   */
  fun expressiveParallaxTransition(
    context: android.content.Context,
    forward: Boolean,
  ): Pair<EnterTransition, ExitTransition> {
    if (!animationsEnabled(context)) return fadeTransition(context)
    val enterCurve = EaseOutCubic
    val exitCurve = EaseInCubic
    val enterDuration = tuning.parallaxEnterDuration
    val exitDuration = tuning.parallaxExitDuration
    val fadeDuration = tuning.parallaxFadeDuration
    val fadeDelay = tuning.parallaxFadeDelay
    val inScale = tuning.parallaxInitialScale
    val outScale = tuning.parallaxRecedingScale
    val divisor = tuning.parallaxDivisor.coerceAtLeast(1)

    return if (forward) {
      val enter = slideInHorizontally(
        animationSpec = tween(enterDuration, easing = enterCurve),
        initialOffsetX = { it },
      ) + scaleIn(
        animationSpec = tween(enterDuration, easing = enterCurve),
        initialScale = inScale,
      ) + (if (fadeDuration > 0) {
        fadeIn(animationSpec = tween(enterDuration - fadeDelay, delayMillis = fadeDelay, easing = FastOutSlowInEasing))
      } else {
        EnterTransition.None
      })

      val exit = slideOutHorizontally(
        animationSpec = tween(exitDuration, easing = exitCurve),
        targetOffsetX = { -it / divisor },
      ) + scaleOut(
        animationSpec = tween(exitDuration, easing = exitCurve),
        targetScale = outScale,
      ) + (if (fadeDuration > 0) {
        fadeOut(animationSpec = tween(fadeDuration, easing = FastOutSlowInEasing))
      } else {
        ExitTransition.None
      })
      enter to exit
    } else {
      val enter = slideInHorizontally(
        animationSpec = tween(enterDuration, easing = enterCurve),
        initialOffsetX = { -it / divisor },
      ) + scaleIn(
        animationSpec = tween(enterDuration, easing = enterCurve),
        initialScale = outScale,
      ) + (if (fadeDuration > 0) {
        fadeIn(animationSpec = tween(enterDuration - fadeDelay, delayMillis = fadeDelay, easing = FastOutSlowInEasing))
      } else {
        EnterTransition.None
      })

      val exit = slideOutHorizontally(
        animationSpec = tween(exitDuration, easing = exitCurve),
        targetOffsetX = { it },
      ) + scaleOut(
        animationSpec = tween(exitDuration, easing = exitCurve),
        targetScale = inScale,
      ) + (if (fadeDuration > 0) {
        fadeOut(animationSpec = tween(fadeDuration, easing = FastOutSlowInEasing))
      } else {
        ExitTransition.None
      })
      enter to exit
    }
  }

  /**
   * Material Shared-Z: 3D spatial zoom (scale 0.88 -> 1.0) without horizontal sliding.
   * Gives a sense of drilling directly into a folder or setting.
   */
  fun sharedZTransition(
    context: android.content.Context,
    forward: Boolean,
  ): Pair<EnterTransition, ExitTransition> {
    if (!animationsEnabled(context)) return fadeTransition(context)
    val enterCurve = EaseOutCubic
    val exitCurve = EaseInCubic
    val enterDuration = tuning.sharedZEnterDuration
    val exitDuration = tuning.sharedZExitDuration
    val inScale = tuning.sharedZInitialScale
    val outScale = tuning.sharedZTargetScale

    return if (forward) {
      val enter = scaleIn(
        animationSpec = tween(enterDuration, easing = enterCurve),
        initialScale = inScale,
      ) + fadeIn(
        animationSpec = tween(enterDuration, easing = LinearEasing),
      )
      val exit = scaleOut(
        animationSpec = tween(exitDuration, easing = exitCurve),
        targetScale = outScale,
      ) + fadeOut(
        animationSpec = tween(exitDuration, easing = LinearEasing),
      )
      enter to exit
    } else {
      val enter = scaleIn(
        animationSpec = tween(enterDuration, easing = enterCurve),
        initialScale = outScale,
      ) + fadeIn(
        animationSpec = tween(enterDuration, easing = LinearEasing),
      )
      val exit = scaleOut(
        animationSpec = tween(exitDuration, easing = exitCurve),
        targetScale = inScale,
      ) + fadeOut(
        animationSpec = tween(exitDuration, easing = LinearEasing),
      )
      enter to exit
    }
  }

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
    val duration = tuning.slideDuration
    val divisor = tuning.slideDivisor.coerceAtLeast(1)
    val spec = tween<IntOffset>(durationMillis = duration)
    val effects = tween<Float>(durationMillis = duration)
    val direction = if (forward) 1 else -1
    val enter = fadeIn(effects) + slideIn(spec) { size -> IntOffset(direction * size.width / divisor, 0) }
    val exit = fadeOut(effects) + slideOut(spec) { size -> IntOffset(-direction * size.width / divisor, 0) }
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
