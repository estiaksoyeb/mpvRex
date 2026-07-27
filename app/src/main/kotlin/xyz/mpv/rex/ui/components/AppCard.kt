package xyz.mpv.rex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import xyz.mpv.rex.ui.theme.LocalUiStyle
import xyz.mpv.rex.ui.theme.UiStyle

@Composable
fun AppCard(
  modifier: Modifier = Modifier,
  shape: Shape = if (LocalUiStyle.current == UiStyle.Miuix) RoundedCornerShape(18.dp) else CardDefaults.shape,
  colors: CardColors = CardDefaults.cardColors(),
  elevation: CardElevation = CardDefaults.cardElevation(),
  border: BorderStroke? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  if (LocalUiStyle.current == UiStyle.Miuix) {
    MiuixCard(
      modifier = modifier,
      colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
        color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surface
      ),
      content = content,
    )
  } else {
    Card(
      modifier = modifier,
      shape = shape,
      colors = colors,
      elevation = elevation,
      border = border,
      content = content,
    )
  }
}
