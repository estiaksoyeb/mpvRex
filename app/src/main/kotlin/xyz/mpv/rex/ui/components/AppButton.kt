package xyz.mpv.rex.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import xyz.mpv.rex.ui.theme.LocalUiStyle
import xyz.mpv.rex.ui.theme.UiStyle

@Composable
fun AppButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: @Composable RowScope.() -> Unit,
) {
  if (LocalUiStyle.current == UiStyle.Miuix) {
    MiuixButton(
      onClick = onClick,
      modifier = modifier,
      enabled = enabled,
      content = content,
    )
  } else {
    Button(
      onClick = onClick,
      modifier = modifier,
      enabled = enabled,
      content = content,
    )
  }
}

@Composable
fun AppTextButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: @Composable RowScope.() -> Unit,
) {
  if (LocalUiStyle.current == UiStyle.Miuix) {
    MiuixButton(
      onClick = onClick,
      modifier = modifier,
      enabled = enabled,
      content = content,
    )
  } else {
    TextButton(
      onClick = onClick,
      modifier = modifier,
      enabled = enabled,
      content = content,
    )
  }
}

@Composable
fun AppTextButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  if (LocalUiStyle.current == UiStyle.Miuix) {
    MiuixTextButton(
      text = text,
      onClick = onClick,
      modifier = modifier,
      enabled = enabled,
    )
  } else {
    TextButton(
      onClick = onClick,
      modifier = modifier,
      enabled = enabled,
    ) {
      Text(text)
    }
  }
}
