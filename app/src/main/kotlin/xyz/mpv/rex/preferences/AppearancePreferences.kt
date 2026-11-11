package xyz.mpv.rex.preferences

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.preference.PreferenceStore
import xyz.mpv.rex.preferences.preference.getEnum
import xyz.mpv.rex.presentation.MotionStyle
import xyz.mpv.rex.ui.theme.AppTheme
import xyz.mpv.rex.ui.theme.DarkMode
import xyz.mpv.rex.ui.theme.spacing
import kotlinx.collections.immutable.ImmutableList

class AppearancePreferences(
  preferenceStore: PreferenceStore,
) {
  val darkMode = preferenceStore.getEnum("dark_mode", DarkMode.System)
  val appTheme = preferenceStore.getEnum("app_theme", AppTheme.Default)
  val navigationMotionStyle = preferenceStore.getEnum("navigation_motion_style", MotionStyle.ExpressiveParallax)
  val themePreset = preferenceStore.getString("theme_preset", "Default")
  val settingsBadgeShape = preferenceStore.getString("settings_badge_shape", "SQUIRCLE")
  val appLanguage = preferenceStore.getString("app_language", "")
  val onboardingCompleted = preferenceStore.getBoolean("onboarding_completed", false)
  val materialYou = preferenceStore.getBoolean("material_you", true)
  val amoledMode = preferenceStore.getBoolean("amoled_mode", false)
  val matchPlayerControlsToTheme = preferenceStore.getBoolean("match_player_controls_to_theme", false)
  val useSystemFont = preferenceStore.getBoolean("use_system_font", false)
  val unlimitedNameLines = preferenceStore.getBoolean("unlimited_name_lines", false)
  val hidePlayerButtonsBackground = preferenceStore.getBoolean("hide_player_buttons_background", false)
  val showNextPreviousButtons = preferenceStore.getBoolean("show_next_previous_buttons", true)
  val enableGlassPlayerControls = preferenceStore.getBoolean("enable_glass_player_controls", false)
  val enableGlassSeekbarBackground = preferenceStore.getBoolean("enable_glass_seekbar_background", false)
  val enableBounceAnimation = preferenceStore.getBoolean("enable_bounce_animation", false)
  val showCommunityIcon = preferenceStore.getBoolean("show_community_icon", true)
  val communityPromptDismissedPermanently = preferenceStore.getBoolean("community_prompt_dismissed_permanently", false)
  val communityFirstAppOpenTimestamp = preferenceStore.getLong("community_first_app_open_timestamp", 0L)
  val communityAlreadyJoinedTimestamp = preferenceStore.getLong("community_already_joined_timestamp", 0L)

  val showHiddenFiles = preferenceStore.getBoolean("show_hidden_files", false)
  val customProfileName = preferenceStore.getString("custom_profile_name", "")
  val customProfileImagePath = preferenceStore.getString("custom_profile_image_path", "")
  val showUnplayedOldVideoLabel = preferenceStore.getBoolean("show_unplayed_old_video_label", true)
  val unplayedOldVideoDays = preferenceStore.getInt("unplayed_old_video_days", 7)

  // Motion Tuning (Toys / Lab)
  val motionParallaxEnterDuration = preferenceStore.getInt("motion_toy_parallax_enter_duration", 400)
  val motionParallaxExitDuration = preferenceStore.getInt("motion_toy_parallax_exit_duration", 350)
  val motionParallaxFadeDuration = preferenceStore.getInt("motion_toy_parallax_fade_duration", 250)
  val motionParallaxFadeDelay = preferenceStore.getInt("motion_toy_parallax_fade_delay", 50)
  val motionParallaxInitialScale = preferenceStore.getFloat("motion_toy_parallax_initial_scale", 0.92f)
  val motionParallaxRecedingScale = preferenceStore.getFloat("motion_toy_parallax_receding_scale", 0.95f)
  val motionParallaxDivisor = preferenceStore.getInt("motion_toy_parallax_divisor", 4)

  val motionSharedZEnterDuration = preferenceStore.getInt("motion_toy_sharedz_enter_duration", 300)
  val motionSharedZExitDuration = preferenceStore.getInt("motion_toy_sharedz_exit_duration", 250)
  val motionSharedZInitialScale = preferenceStore.getFloat("motion_toy_sharedz_initial_scale", 0.88f)
  val motionSharedZTargetScale = preferenceStore.getFloat("motion_toy_sharedz_target_scale", 1.08f)

  val motionSlideDuration = preferenceStore.getInt("motion_toy_slide_duration", 220)
  val motionSlideDivisor = preferenceStore.getInt("motion_toy_slide_divisor", 2)

  fun syncMotionTuning() {
    xyz.mpv.rex.ui.motion.RexMotion.tuning = xyz.mpv.rex.ui.motion.MotionTuning(
      parallaxEnterDuration = motionParallaxEnterDuration.get(),
      parallaxExitDuration = motionParallaxExitDuration.get(),
      parallaxFadeDuration = motionParallaxFadeDuration.get(),
      parallaxFadeDelay = motionParallaxFadeDelay.get(),
      parallaxInitialScale = motionParallaxInitialScale.get(),
      parallaxRecedingScale = motionParallaxRecedingScale.get(),
      parallaxDivisor = motionParallaxDivisor.get(),

      sharedZEnterDuration = motionSharedZEnterDuration.get(),
      sharedZExitDuration = motionSharedZExitDuration.get(),
      sharedZInitialScale = motionSharedZInitialScale.get(),
      sharedZTargetScale = motionSharedZTargetScale.get(),

      slideDuration = motionSlideDuration.get(),
      slideDivisor = motionSlideDivisor.get(),
    )
  }

  fun resetMotionTuning(preset: MotionStyle) {
    when (preset) {
      MotionStyle.ExpressiveParallax, MotionStyle.Default -> {
        motionParallaxEnterDuration.set(400)
        motionParallaxExitDuration.set(350)
        motionParallaxFadeDuration.set(250)
        motionParallaxFadeDelay.set(50)
        motionParallaxInitialScale.set(0.92f)
        motionParallaxRecedingScale.set(0.95f)
        motionParallaxDivisor.set(4)
      }
      MotionStyle.SharedZ -> {
        motionSharedZEnterDuration.set(300)
        motionSharedZExitDuration.set(250)
        motionSharedZInitialScale.set(0.88f)
        motionSharedZTargetScale.set(1.08f)
      }
      MotionStyle.Slide -> {
        motionSlideDuration.set(220)
        motionSlideDivisor.set(2)
      }
    }
    syncMotionTuning()
  }

  init {
    syncMotionTuning()
  }
  val showNetworkThumbnails = preferenceStore.getBoolean("show_network_thumbnails", false)
  val seekbarStyle = preferenceStore.getEnum("seekbar_style", SeekbarStyle.Standard)
  val playerAlwaysDarkMode = preferenceStore.getBoolean("player_always_dark_mode", true)
  val hidePlayFabShowInHeader = preferenceStore.getBoolean("hide_play_fab_show_in_header", false)

  val thumbnailStrategy = preferenceStore.getEnum("thumbnail_strategy", ThumbnailStrategy.FirstFrame)
  val thumbnailPositionPercent = preferenceStore.getInt("thumbnail_position_percent", THUMBNAIL_POSITION_DEFAULT)

  companion object {
      const val THUMBNAIL_POSITION_DEFAULT = 30
  }

  val topLeftControls =
    preferenceStore.getString(
      "top_left_controls",
      "BACK_ARROW,VIDEO_TITLE",
    )

  val topRightControls =
    preferenceStore.getString(
      "top_right_controls",
      "VIDEO_QUALITY,CURRENT_CHAPTER,DECODER,AUDIO_TRACK,SUBTITLES,MORE_OPTIONS",
    )

  val bottomRightControls =
    preferenceStore.getString(
      "bottom_right_controls",
      "PICTURE_IN_PICTURE,ASPECT_RATIO",
    )

  val bottomLeftControls =
    preferenceStore.getString(
      "bottom_left_controls",
      "LOCK_CONTROLS,BACKGROUND_PLAYBACK,SCREEN_ROTATION,PLAYBACK_SPEED,REPEAT_MODE,SHUFFLE,AB_LOOP",
    )

  val portraitBottomControls =
    preferenceStore.getString(
      "portrait_bottom_controls",
      "VIDEO_QUALITY,DECODER,AUDIO_TRACK,SUBTITLES,PLAYBACK_SPEED,REPEAT_MODE,SHUFFLE,SCREEN_ROTATION,LOCK_CONTROLS,BACKGROUND_PLAYBACK,PICTURE_IN_PICTURE,ASPECT_RATIO",
    )

  val moreSheetControls =
    preferenceStore.getString(
      "more_sheet_controls",
      "VIDEO_QUALITY,SCREEN_ROTATION,DECODER,AUDIO_TRACK,SUBTITLES,PLAYBACK_SPEED,REPEAT_MODE,SHUFFLE,VIDEO_ZOOM,FRAME_NAVIGATION,ASPECT_RATIO,PICTURE_IN_PICTURE,LOCK_CONTROLS,SLEEP_TIMER",
    )

  fun parseButtons(
    csv: String,
    usedButtons: MutableSet<PlayerButton>,
  ): List<PlayerButton> =
    csv
      .splitToSequence(',')
      .map { it.trim().uppercase() }
      .mapNotNull { name ->
        try {
          PlayerButton.valueOf(name)
        } catch (_: IllegalArgumentException) {
          null
        }
      }.filter { it != PlayerButton.NONE }
      .filter { usedButtons.add(it) }
      .toList()
}

enum class ThumbnailStrategy {
  FirstFrame,
  Position
}

@Composable
fun MultiChoiceSegmentedButton(
  choices: ImmutableList<String>,
  selectedIndices: ImmutableList<Int>,
  onClick: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  MultiChoiceSegmentedButtonRow(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(MaterialTheme.spacing.medium),
  ) {
    choices.forEachIndexed { index, choice ->
      SegmentedButton(
        checked = selectedIndices.contains(index),
        onCheckedChange = { onClick(index) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = choices.size),
      ) {
        Text(text = choice)
      }
    }
  }
}
