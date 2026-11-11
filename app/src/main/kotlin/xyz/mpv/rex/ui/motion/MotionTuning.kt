package xyz.mpv.rex.ui.motion

/**
 * Live-tunable parameters for navigation motion presets.
 * Allows adjusting duration, scale, and parallax intensity at runtime without rebuilding.
 */
data class MotionTuning(
  // Expressive Parallax
  var parallaxEnterDuration: Int = 400,
  var parallaxExitDuration: Int = 350,
  var parallaxFadeDuration: Int = 250,
  var parallaxFadeDelay: Int = 50,
  var parallaxInitialScale: Float = 0.92f,
  var parallaxRecedingScale: Float = 0.95f,
  var parallaxDivisor: Int = 4,

  // Shared-Z
  var sharedZEnterDuration: Int = 300,
  var sharedZExitDuration: Int = 250,
  var sharedZInitialScale: Float = 0.88f,
  var sharedZTargetScale: Float = 1.08f,

  // Classic Slide
  var slideDuration: Int = 220,
  var slideDivisor: Int = 2,
)
