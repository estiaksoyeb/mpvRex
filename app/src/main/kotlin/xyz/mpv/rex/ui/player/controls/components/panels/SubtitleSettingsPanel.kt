package xyz.mpv.rex.ui.player.controls.components.panels

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.fsaf.FileManager
import com.yubyf.truetypeparser.TTFFile
import `is`.xyz.mpv.MPVLib
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.ListPreferenceType
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.preferenceTheme
import org.koin.compose.koinInject
import xyz.mpv.rex.R
import xyz.mpv.rex.preferences.SubtitleJustification
import xyz.mpv.rex.preferences.SubtitlesPreferences
import xyz.mpv.rex.preferences.preference.collectAsState
import xyz.mpv.rex.preferences.preference.deleteAndGet
import xyz.mpv.rex.presentation.components.ExposedTextDropDownMenu
import xyz.mpv.rex.presentation.components.PlayerSheet
import xyz.mpv.rex.presentation.components.SliderItem
import xyz.mpv.rex.ui.player.controls.components.sheets.toFixed
import xyz.mpv.rex.ui.preferences.components.SwitchPreference
import xyz.mpv.rex.ui.theme.spacing

@Composable
fun SubtitleSettingsPanel(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val configuration = LocalConfiguration.current
  val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

  if (isPortrait) {
    PlayerSheet(onDismissRequest = onDismissRequest) {
      Column(
        modifier = modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(top = 4.dp),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium)
            .padding(bottom = MaterialTheme.spacing.small),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = stringResource(R.string.player_sheets_subtitles_settings_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
          IconButton(onClick = onDismissRequest) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(24.dp))
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

        Spacer(Modifier.height(MaterialTheme.spacing.small))

        SubtitleSettingsContent()
      }
    }
  } else {
    SubtitleSettingsSideSheet(
      onDismissRequest = onDismissRequest,
      modifier = modifier,
    )
  }
}

@Composable
private fun SubtitleSettingsSideSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  var isVisible by remember { mutableStateOf(false) }
  val latestOnDismissRequest by rememberUpdatedState(onDismissRequest)

  val scrimAlpha by animateFloatAsState(
    targetValue = if (isVisible) 0.5f else 0f,
    animationSpec = tween(durationMillis = 280),
    label = "sub_side_sheet_scrim",
  )

  val dismissWithAnimation: () -> Unit = {
    scope.launch {
      isVisible = false
      delay(250)
      latestOnDismissRequest()
    }
  }

  BackHandler(enabled = isVisible, onBack = { dismissWithAnimation() })

  LaunchedEffect(Unit) {
    isVisible = true
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = scrimAlpha))
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = { dismissWithAnimation() },
      ),
    contentAlignment = Alignment.CenterEnd,
  ) {
    AnimatedVisibility(
      visible = isVisible,
      enter = slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
      ) + fadeIn(animationSpec = tween(durationMillis = 200)),
      exit = slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(durationMillis = 250, easing = FastOutLinearInEasing),
      ) + fadeOut(animationSpec = tween(durationMillis = 200)),
    ) {
      Surface(
        modifier = modifier
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {}, // Prevent clicking through sheet to dismiss
          )
          .widthIn(max = 380.dp)
          .fillMaxHeight()
          .windowInsetsPadding(
            WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.End),
          ),
        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
      ) {
        Column(
          modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = MaterialTheme.spacing.medium)
              .padding(top = MaterialTheme.spacing.medium, bottom = MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = stringResource(R.string.player_sheets_subtitles_settings_title),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = { dismissWithAnimation() }) {
              Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(24.dp))
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

          Spacer(Modifier.height(MaterialTheme.spacing.small))

          SubtitleSettingsContent()
        }
      }
    }
  }
}

@Composable
private fun SubtitleSettingsContent(
  modifier: Modifier = Modifier,
) {
  val preferences = koinInject<SubtitlesPreferences>()

  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = MaterialTheme.spacing.medium)
      .padding(bottom = MaterialTheme.spacing.large),
    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
  ) {
    SubtitleTypographySection(preferences)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

    SubtitleColorsSection(preferences)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

    SubtitleMiscellaneousSection(preferences)
  }
}

@SuppressLint("MutableCollectionMutableState")
@Composable
private fun SubtitleTypographySection(
  preferences: SubtitlesPreferences,
) {
  val context = LocalContext.current
  val fileManager = koinInject<FileManager>()
  val fonts by remember { mutableStateOf(mutableListOf("Default")) }
  var fontsLoadingIndicator: (@Composable () -> Unit)? by remember {
    val indicator: (@Composable () -> Unit) = {
      CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
    mutableStateOf(indicator)
  }

  LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) {
      val fontsDir = fileManager.fromPath(context.filesDir.path + "/fonts")
      if (fileManager.exists(fontsDir)) {
        fonts.addAll(
          fileManager
            .listFiles(fontsDir)
            .filter { fileManager.isFile(it) && fileManager.getName(it).lowercase().matches(".*\\.[ot]tf$".toRegex()) }
            .mapNotNull {
              runCatching {
                TTFFile.open(fileManager.getInputStream(it) ?: return@mapNotNull null).families.values.first()
              }.getOrNull()
            }.distinct(),
        )
      }
      fontsLoadingIndicator = null
    }
  }

  val isBold by MPVLib.propBoolean["sub-bold"].collectAsState()
  val isItalic by MPVLib.propBoolean["sub-italic"].collectAsState()
  val mpvJustify by MPVLib.propString["sub-justify"].collectAsState()
  val justify by remember {
    derivedStateOf { SubtitleJustification.entries.first { it.value == mpvJustify } }
  }
  val font by MPVLib.propString["sub-font"].collectAsState()
  val fontSize by MPVLib.propInt["sub-font-size"].collectAsState()
  val mpvBorderStyle by MPVLib.propString["sub-border-style"].collectAsState()
  val borderStyle by remember {
    derivedStateOf { SubtitlesBorderStyle.entries.first { it.value == mpvBorderStyle } }
  }
  val borderSize by MPVLib.propInt["sub-outline-size"].collectAsState()
  val shadowOffset by MPVLib.propInt["sub-shadow-offset"].collectAsState()

  Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
      ) {
        Icon(
          Icons.Default.FormatColorText,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = stringResource(R.string.player_sheets_sub_typography_card_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
      TextButton(onClick = { resetTypography(preferences) }) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Default.FormatClear, null, modifier = Modifier.size(16.dp))
          Text(stringResource(R.string.generic_reset))
        }
      }
    }

    Row(
      Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
      IconToggleButton(
        checked = isBold == true,
        onCheckedChange = {
          preferences.bold.set(it)
          MPVLib.setPropertyBoolean("sub-bold", it)
        },
      ) {
        Icon(Icons.Default.FormatBold, null, modifier = Modifier.size(24.dp))
      }
      IconToggleButton(
        checked = isItalic == true,
        onCheckedChange = {
          preferences.italic.set(it)
          MPVLib.setPropertyBoolean("sub-italic", it)
        },
      ) {
        Icon(Icons.Default.FormatItalic, null, modifier = Modifier.size(24.dp))
      }
      SubtitleJustification.entries.minus(SubtitleJustification.Auto).forEach { justification ->
        IconToggleButton(
          checked = justify == justification,
          onCheckedChange = {
            MPVLib.setPropertyBoolean("sub-ass-justify", it)
            if (it) {
              preferences.justification.set(justification)
              MPVLib.setPropertyString("sub-justify", justification.value)
            } else {
              preferences.justification.set(SubtitleJustification.Auto)
              MPVLib.setPropertyString("sub-justify", SubtitleJustification.Auto.value)
            }
          },
        ) {
          Icon(justification.icon, null)
        }
      }
    }

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = MaterialTheme.spacing.extraSmall),
      horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        painterResource(R.drawable.outline_brand_family_24),
        null,
        modifier = Modifier.size(24.dp),
      )
      ExposedTextDropDownMenu(
        selectedValue = font?.ifEmpty { "Default" } ?: "Default",
        options = fonts.toImmutableList(),
        label = stringResource(R.string.player_sheets_sub_typography_font),
        onValueChangedEvent = {
          val actualFont = if (it == "Default") "" else it
          preferences.font.set(actualFont)
          MPVLib.setPropertyString("sub-font", actualFont)
          MPVLib.setPropertyString("secondary-sub-font", actualFont)
        },
        modifier = Modifier.weight(1f),
        leadingIcon = fontsLoadingIndicator,
      )
    }

    SliderItem(
      label = stringResource(R.string.player_sheets_sub_typography_font_size),
      max = 100,
      min = 1,
      value = fontSize ?: preferences.fontSize.get(),
      valueText = (fontSize ?: preferences.fontSize.get()).toString(),
      onChange = {
        preferences.fontSize.set(it)
        MPVLib.setPropertyInt("sub-font-size", it)
      },
      icon = { Icon(Icons.Default.FormatSize, null) },
    )

    ProvidePreferenceLocals(
      theme = preferenceTheme(iconContainerMinWidth = 48.dp),
    ) {
      ListPreference(
        borderStyle,
        onValueChange = {
          preferences.borderStyle.set(it)
          MPVLib.setPropertyString("sub-border-style", it.value)
        },
        title = { Text(stringResource(R.string.player_sheets_subtitles_border_style)) },
        valueToText = { AnnotatedString(context.getString(it.titleRes)) },
        values = SubtitlesBorderStyle.entries,
        type = ListPreferenceType.DROPDOWN_MENU,
        summary = { Text(stringResource(borderStyle.titleRes)) },
        icon = { Icon(Icons.Default.BorderStyle, null) },
      )
    }

    SliderItem(
      label = stringResource(R.string.player_sheets_sub_typography_border_size),
      value = borderSize ?: preferences.borderSize.get(),
      valueText = (borderSize ?: preferences.borderSize.get()).toString(),
      onChange = {
        preferences.borderSize.set(it)
        MPVLib.setPropertyInt("sub-outline-size", it)
      },
      max = 20,
      icon = { Icon(Icons.Default.BorderColor, null) },
    )

    SliderItem(
      label = stringResource(R.string.player_sheets_subtitles_shadow_offset),
      value = shadowOffset ?: preferences.shadowOffset.get(),
      valueText = (shadowOffset ?: preferences.shadowOffset.get()).toString(),
      onChange = {
        preferences.shadowOffset.set(it)
        MPVLib.setPropertyInt("sub-shadow-offset", it)
      },
      max = 100,
      icon = { Icon(painterResource(R.drawable.sharp_shadow_24), null) },
    )
  }
}

@Composable
private fun SubtitleColorsSection(
  preferences: SubtitlesPreferences,
) {
  var currentColorType by remember { mutableStateOf(SubColorType.Text) }
  var currentColor by remember { mutableIntStateOf(getCurrentMPVColor(currentColorType)) }

  LaunchedEffect(currentColorType) {
    currentColor = getCurrentMPVColor(currentColorType)
  }

  Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
      ) {
        Icon(
          Icons.Default.Palette,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = stringResource(R.string.player_sheets_sub_colors_card_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
      TextButton(
        onClick = {
          resetColors(preferences, currentColorType)
          currentColor = getCurrentMPVColor(currentColorType)
        },
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Default.FormatColorReset, null, modifier = Modifier.size(16.dp))
          Text(stringResource(R.string.generic_reset))
        }
      }
    }

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
    ) {
      SubColorType.entries.forEach { type ->
        FilterChip(
          selected = currentColorType == type,
          onClick = { currentColorType = type },
          leadingIcon = {
            Icon(
              when (type) {
                SubColorType.Text -> Icons.Default.FormatColorText
                SubColorType.Border -> Icons.Default.BorderColor
                SubColorType.Background -> Icons.Default.FormatColorFill
              },
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
          },
          label = { Text(stringResource(type.titleRes)) },
        )
      }
    }

    SubtitlesColorPicker(
      currentColor,
      onColorChange = {
        currentColor = it
        currentColorType.preference(preferences).set(it)
        MPVLib.setPropertyString(currentColorType.property, it.toColorHexString())
      },
    )
  }
}

@Composable
private fun SubtitleMiscellaneousSection(
  preferences: SubtitlesPreferences,
) {
  var overrideAssSubs by remember {
    mutableStateOf(MPVLib.getPropertyString("sub-ass-override") == "force")
  }
  var scaleByWindow by remember {
    mutableStateOf(MPVLib.getPropertyString("sub-scale-by-window") == "yes")
  }
  val openAtVideoLocation by preferences.openPickerAtVideoLocation.collectAsState()
  val forceLtr by preferences.forceLtr.collectAsState()

  val secondarySid by MPVLib.propInt["secondary-sid"].collectAsState()
  val isSecondaryActive = (secondarySid ?: (MPVLib.getPropertyInt("secondary-sid") ?: 0)) > 0

  val subScale by MPVLib.propFloat["sub-scale"].collectAsState()
  val subPos by MPVLib.propInt["sub-pos"].collectAsState()
  val secondarySubScale by MPVLib.propFloat["secondary-sub-scale"].collectAsState()
  val secondarySubPos by MPVLib.propInt["secondary-sub-pos"].collectAsState()

  Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.smaller)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
      ) {
        Icon(
          Icons.Default.Tune,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
          tint = MaterialTheme.colorScheme.primary,
        )
        Text(
          text = stringResource(R.string.player_sheets_sub_misc_card_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
      TextButton(
        onClick = {
          preferences.subPos.deleteAndGet().let {
            MPVLib.setPropertyInt("sub-pos", it)
          }
          preferences.subScale.deleteAndGet().let {
            MPVLib.setPropertyFloat("sub-scale", it)
          }
          preferences.secondarySubPos.deleteAndGet().let {
            MPVLib.setPropertyInt("secondary-sub-pos", it)
          }
          preferences.secondarySubScale.deleteAndGet().let {
            MPVLib.setPropertyFloat("secondary-sub-scale", it)
          }
          val defaultOverride = preferences.overrideAssSubs.deleteAndGet()
          overrideAssSubs = defaultOverride
          MPVLib.setPropertyString("sub-ass-override", if (defaultOverride) "force" else "scale")
          MPVLib.setPropertyString("secondary-sub-ass-override", "force")
          val defaultScaleByWindow = preferences.scaleByWindow.deleteAndGet()
          scaleByWindow = defaultScaleByWindow
          val scaleValue = if (defaultScaleByWindow) "yes" else "no"
          MPVLib.setPropertyString("sub-scale-by-window", scaleValue)
          MPVLib.setPropertyString("sub-use-margins", scaleValue)
          MPVLib.setPropertyString("secondary-sub-scale-by-window", scaleValue)
          MPVLib.setPropertyString("secondary-sub-use-margins", scaleValue)
        },
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Icons.Default.FormatClear, null, modifier = Modifier.size(16.dp))
          Text(stringResource(R.string.generic_reset))
        }
      }
    }

    ProvidePreferenceLocals {
      SwitchPreference(
        overrideAssSubs,
        onValueChange = {
          overrideAssSubs = it
          preferences.overrideAssSubs.set(it)
          MPVLib.setPropertyString("sub-ass-override", if (it) "force" else "scale")
          MPVLib.setPropertyString("secondary-sub-ass-override", "force")
        },
        { Text(stringResource(R.string.player_sheets_sub_override_ass)) },
      )
      SwitchPreference(
        scaleByWindow,
        onValueChange = {
          scaleByWindow = it
          preferences.scaleByWindow.set(it)
          val value = if (it) "yes" else "no"
          MPVLib.setPropertyString("sub-scale-by-window", value)
          MPVLib.setPropertyString("sub-use-margins", value)
          MPVLib.setPropertyString("secondary-sub-scale-by-window", value)
          MPVLib.setPropertyString("secondary-sub-use-margins", value)
        },
        { Text(stringResource(R.string.player_sheets_sub_scale_by_window)) },
        summary = { Text(stringResource(R.string.player_sheets_sub_scale_by_window_summary)) },
      )
      SwitchPreference(
        openAtVideoLocation,
        onValueChange = { preferences.openPickerAtVideoLocation.set(it) },
        { Text(stringResource(R.string.pref_subtitles_open_at_video_location_title)) },
        summary = { Text(stringResource(R.string.pref_subtitles_open_at_video_location_summary)) },
      )
      SwitchPreference(
        forceLtr,
        onValueChange = {
          preferences.forceLtr.set(it)
          val value = if (it) "yes" else "no"
          runCatching {
            MPVLib.setPropertyString("sub-vsfilter-bidi-compat", value)
            MPVLib.command("sub-reload")
          }
        },
        { Text(stringResource(R.string.pref_subtitles_force_ltr_title)) },
        summary = { Text(stringResource(R.string.pref_subtitles_force_ltr_summary)) },
      )
    }

    SliderItem(
      label = stringResource(R.string.player_sheets_sub_scale),
      value = subScale ?: preferences.subScale.get(),
      valueText = (subScale ?: preferences.subScale.get()).toFixed(2).toString(),
      onChange = {
        preferences.subScale.set(it)
        MPVLib.setPropertyFloat("sub-scale", it)
      },
      max = 5f,
      icon = { Icon(Icons.Default.FormatSize, null) },
    )
    SliderItem(
      label = if (isSecondaryActive) stringResource(R.string.player_sheets_sub_primary_position) else stringResource(R.string.player_sheets_sub_position),
      value = subPos ?: preferences.subPos.get(),
      valueText = (subPos ?: preferences.subPos.get()).toString(),
      onChange = {
        preferences.subPos.set(it)
        MPVLib.setPropertyInt("sub-pos", it)
      },
      max = 150,
      icon = { Icon(Icons.Default.AlignVerticalCenter, null) },
    )

    if (isSecondaryActive) {
      SliderItem(
        label = stringResource(R.string.player_sheets_secondary_sub_scale),
        value = secondarySubScale ?: preferences.secondarySubScale.get(),
        valueText = (secondarySubScale ?: preferences.secondarySubScale.get()).toFixed(2).toString(),
        onChange = {
          preferences.secondarySubScale.set(it)
          MPVLib.setPropertyFloat("secondary-sub-scale", it)
        },
        max = 5f,
        icon = { Icon(Icons.Default.FormatSize, null) },
      )
      SliderItem(
        label = stringResource(R.string.player_sheets_secondary_sub_position),
        value = secondarySubPos ?: preferences.secondarySubPos.get(),
        valueText = (secondarySubPos ?: preferences.secondarySubPos.get()).toString(),
        onChange = {
          preferences.secondarySubPos.set(it)
          MPVLib.setPropertyInt("secondary-sub-pos", it)
        },
        max = 150,
        icon = { Icon(Icons.Default.AlignVerticalCenter, null) },
      )
    }
  }
}

