package xyz.mpv.rex.ui.preferences

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xyz.mpv.rex.ui.components.AppCard
import xyz.mpv.rex.ui.theme.LocalUiStyle
import xyz.mpv.rex.ui.theme.UiStyle

/**
 * A card container for grouping related preferences, mimicking modern Android settings UI.
 */
@Composable
fun PreferenceCard(
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  val isMiuix = LocalUiStyle.current == UiStyle.Miuix
  val cardShape = if (isMiuix) RoundedCornerShape(18.dp) else RoundedCornerShape(28.dp)
  val cardColors = if (isMiuix) {
    CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface,
      contentColor = MaterialTheme.colorScheme.onSurface,
    )
  } else {
    CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
  }
  val cardBorder = if (isMiuix) {
    BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
  } else null

  AppCard(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp, vertical = 6.dp),
    shape = cardShape,
    colors = cardColors,
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    border = cardBorder,
  ) {
    Column(
      modifier = Modifier.padding(vertical = 4.dp),
      verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
      content()
    }
  }
}

/**
 * A divider to separate preferences within a card.
 */
@Composable
fun PreferenceDivider(
  modifier: Modifier = Modifier,
) {
  val isMiuix = LocalUiStyle.current == UiStyle.Miuix
  HorizontalDivider(
    modifier = modifier.padding(horizontal = 16.dp),
    color = if (isMiuix) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
  )
}

/**
 * A section header for preferences, displayed outside cards.
 */
@Composable
fun PreferenceSectionHeader(
  title: String,
  modifier: Modifier = Modifier,
) {
  val isMiuix = LocalUiStyle.current == UiStyle.Miuix
  Text(
    text = if (isMiuix) title.uppercase() else title,
    style = if (isMiuix) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
    color = if (isMiuix) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
    modifier = modifier.padding(
      horizontal = if (isMiuix) 24.dp else 32.dp,
      vertical = if (isMiuix) 8.dp else 16.dp,
    ),
  )
}
