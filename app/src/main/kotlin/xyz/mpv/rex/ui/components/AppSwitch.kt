package xyz.mpv.rex.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import xyz.mpv.rex.ui.theme.LocalUiStyle
import xyz.mpv.rex.ui.theme.UiStyle

@Composable
fun AppSwitch(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier = Modifier,
  thumbContent: (@Composable () -> Unit)? = null,
  enabled: Boolean = true,
  colors: SwitchColors = SwitchDefaults.colors(),
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
  if (LocalUiStyle.current == UiStyle.Miuix) {
    MiuixSwitch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = modifier,
      enabled = enabled,
    )
  } else {
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      modifier = modifier,
      thumbContent = thumbContent,
      enabled = enabled,
      colors = colors,
      interactionSource = interactionSource,
    )
  }
}
