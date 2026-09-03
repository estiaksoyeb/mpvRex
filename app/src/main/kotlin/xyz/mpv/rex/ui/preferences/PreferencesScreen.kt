package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ViewQuilt
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import xyz.mpv.rex.R
import xyz.mpv.rex.presentation.Screen
import xyz.mpv.rex.presentation.components.GroupPosition
import xyz.mpv.rex.presentation.components.GroupedListColumn
import xyz.mpv.rex.presentation.components.GroupedListItem
import xyz.mpv.rex.ui.utils.LocalBackStack

@Serializable
object PreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val searchShape = RoundedCornerShape(24.dp)
    Scaffold(
      topBar = {
        TopAppBar(
          title = { 
            Text(
              text = stringResource(R.string.pref_preferences),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(
                Icons.AutoMirrored.Outlined.ArrowBack, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      val navBarHeight = xyz.mpv.rex.ui.browser.LocalNavigationBarHeight.current
      ProvidePreferenceLocals {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navBarHeight + 24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          // Search bar - full width, prominent placement
          item {
            Surface(
              onClick = { backstack.add(SettingsSearchScreen) },
              shape = searchShape,
              modifier = Modifier
                .fillMaxWidth()
                .clip(searchShape),
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              tonalElevation = 2.dp,
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Icon(
                  imageVector = Icons.Outlined.Search,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                  text = stringResource(R.string.settings_search_hint),
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.outline,
                )
              }
            }
          }

          // UI & Appearance Section
          item {
            PreferenceSection(title = stringResource(R.string.pref_category_ui_appearance)) {
              GroupedListColumn {
                PreferenceItem(
                  position = GroupPosition.FIRST,
                  title = stringResource(id = R.string.pref_appearance_title),
                  summary = stringResource(id = R.string.pref_appearance_summary),
                  icon = Icons.Outlined.Palette,
                  onClick = { backstack.add(AppearancePreferencesScreen) },
                )
                PreferenceItem(
                  position = GroupPosition.LAST,
                  title = stringResource(id = R.string.pref_layout_title),
                  summary = stringResource(id = R.string.pref_layout_summary),
                  icon = Icons.AutoMirrored.Outlined.ViewQuilt,
                  onClick = { backstack.add(PlayerControlsPreferencesScreen) },
                )
              }
            }
          }

          // Playback & Controls Section
          item {
            PreferenceSection(title = stringResource(R.string.pref_category_playback_controls)) {
              GroupedListColumn {
                PreferenceItem(
                  position = GroupPosition.FIRST,
                  title = stringResource(id = R.string.pref_player),
                  summary = stringResource(id = R.string.pref_player_summary),
                  icon = Icons.Outlined.PlayCircle,
                  onClick = { backstack.add(PlayerPreferencesScreen) },
                )
                PreferenceItem(
                  position = GroupPosition.LAST,
                  title = stringResource(id = R.string.pref_gesture),
                  summary = stringResource(id = R.string.pref_gesture_summary),
                  icon = Icons.Outlined.Gesture,
                  onClick = { backstack.add(GesturePreferencesScreen) },
                )
              }
            }
          }

          // Media & Library Section
          item {
            PreferenceSection(title = stringResource(R.string.pref_media_library_title)) {
              GroupedListColumn {
                PreferenceItem(
                  position = GroupPosition.ONLY,
                  title = stringResource(R.string.pref_media_library_title),
                  summary = stringResource(R.string.pref_media_library_summary),
                  icon = Icons.Outlined.VideoLibrary,
                  onClick = { backstack.add(MediaLibraryPreferencesScreen) },
                )
              }
            }
          }

          // Media Settings Section
          item {
            PreferenceSection(title = stringResource(R.string.pref_category_media_settings)) {
              GroupedListColumn {
                PreferenceItem(
                  position = GroupPosition.FIRST,
                  title = stringResource(id = R.string.pref_decoder),
                  summary = stringResource(id = R.string.pref_decoder_summary),
                  icon = Icons.Outlined.Memory,
                  onClick = { backstack.add(DecoderPreferencesScreen) },
                )
                PreferenceItem(
                  position = GroupPosition.MIDDLE,
                  title = stringResource(id = R.string.pref_subtitles),
                  summary = stringResource(id = R.string.pref_subtitles_summary),
                  icon = Icons.Outlined.Subtitles,
                  onClick = { backstack.add(SubtitlesPreferencesScreen) },
                )
                PreferenceItem(
                  position = GroupPosition.LAST,
                  title = stringResource(id = R.string.pref_audio),
                  summary = stringResource(id = R.string.pref_audio_summary),
                  icon = Icons.Outlined.Audiotrack,
                  onClick = { backstack.add(AudioPreferencesScreen) },
                )
              }
            }
          }

          // RexShorts Section
          item {
            PreferenceSection(title = stringResource(R.string.pref_category_rexshorts)) {
              GroupedListColumn {
                PreferenceItem(
                  position = GroupPosition.ONLY,
                  title = stringResource(R.string.pref_category_rexshorts_settings),
                  summary = stringResource(R.string.pref_category_rexshorts_settings_desc),
                  icon = Icons.Outlined.VideoLibrary,
                  onClick = { backstack.add(ShortsPreferencesScreen) },
                )
              }
            }
          }

          // Integrations Section
          item {
            PreferenceSection(title = "Integrations") {
              GroupedListColumn {
                PreferenceItem(
                  position = GroupPosition.ONLY,
                  title = "Jellyfin",
                  summary = "External player sync",
                  icon = Icons.Outlined.VideoLibrary,
                  onClick = { backstack.add(xyz.mpv.rex.jellyfin.ui.JellyfinSettingsScreen) },
                )
              }
            }
          }

          // Advanced & About Section
          item {
            PreferenceSection(title = stringResource(R.string.pref_category_advanced_about)) {
              GroupedListColumn {
                PreferenceItem(
                  position = GroupPosition.FIRST,
                  title = stringResource(R.string.pref_advanced),
                  summary = stringResource(id = R.string.pref_advanced_summary),
                  icon = Icons.Outlined.Code,
                  onClick = { backstack.add(AdvancedPreferencesScreen) },
                )
                PreferenceItem(
                  position = GroupPosition.LAST,
                  title = stringResource(id = R.string.pref_about_title),
                  summary = stringResource(id = R.string.pref_about_summary),
                  icon = Icons.Outlined.Info,
                  onClick = { backstack.add(AboutScreen) },
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PreferenceSection(
  title: String,
  content: @Composable () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
    )
    content()
  }
}

@Composable
private fun PreferenceItem(
  position: GroupPosition,
  title: String,
  summary: String,
  icon: ImageVector,
  onClick: () -> Unit,
) {
  GroupedListItem(
    position = position,
    onClick = onClick,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(42.dp)
          .background(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
          ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (summary.isNotBlank()) {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        modifier = Modifier.size(20.dp),
      )
    }
  }
}
