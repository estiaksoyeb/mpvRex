package xyz.mpv.rex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.window.WindowDialog as MiuixWindowDialog
import xyz.mpv.rex.ui.theme.LocalUiStyle
import xyz.mpv.rex.ui.theme.UiStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppAlertDialog(
  onDismissRequest: () -> Unit,
  confirmButton: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  dismissButton: (@Composable () -> Unit)? = null,
  icon: (@Composable () -> Unit)? = null,
  title: (@Composable () -> Unit)? = null,
  titleText: String? = null,
  text: (@Composable () -> Unit)? = null,
  summaryText: String? = null,
  shape: Shape = AlertDialogDefaults.shape,
  containerColor: Color = AlertDialogDefaults.containerColor,
  tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
) {
  if (LocalUiStyle.current == UiStyle.Miuix) {
    MiuixWindowDialog(
      show = true,
      onDismissRequest = onDismissRequest,
      title = titleText,
      summary = summaryText,
      modifier = modifier,
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        if (icon != null) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
          ) {
            icon()
          }
        }
        if (title != null && titleText == null) {
          title()
        }
        if (text != null) {
          text()
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (dismissButton != null) {
            dismissButton()
          }
          confirmButton()
        }
      }
    }
  } else {
    AlertDialog(
      onDismissRequest = onDismissRequest,
      confirmButton = confirmButton,
      modifier = modifier,
      dismissButton = dismissButton,
      icon = icon,
      title = title ?: (titleText?.let { { Text(it) } }),
      text = text ?: (summaryText?.let { { Text(it) } }),
      shape = shape,
      containerColor = containerColor,
      tonalElevation = tonalElevation,
    )
  }
}
