package xyz.mpv.rex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Surface as MiuixSurface
import xyz.mpv.rex.ui.theme.LocalUiStyle
import xyz.mpv.rex.ui.theme.UiStyle

@Composable
fun AppSurface(
  modifier: Modifier = Modifier,
  shape: Shape = if (LocalUiStyle.current == UiStyle.Miuix) RoundedCornerShape(18.dp) else MaterialTheme.shapes.medium,
  color: Color = MaterialTheme.colorScheme.surface,
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  tonalElevation: Dp = 0.dp,
  shadowElevation: Dp = 0.dp,
  border: BorderStroke? = null,
  onClick: (() -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  if (LocalUiStyle.current == UiStyle.Miuix) {
    if (onClick != null) {
      MiuixSurface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = color,
        border = border,
        content = content,
      )
    } else {
      MiuixSurface(
        modifier = modifier,
        shape = shape,
        color = color,
        border = border,
        content = content,
      )
    }
  } else {
    if (onClick != null) {
      Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        content = content,
      )
    } else {
      Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border,
        content = content,
      )
    }
  }
}
