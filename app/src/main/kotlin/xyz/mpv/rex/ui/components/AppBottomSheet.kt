package xyz.mpv.rex.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.window.WindowBottomSheet as MiuixWindowBottomSheet
import xyz.mpv.rex.ui.theme.LocalUiStyle
import xyz.mpv.rex.ui.theme.UiStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  sheetState: SheetState = rememberModalBottomSheetState(),
  title: String? = null,
  containerColor: Color = BottomSheetDefaults.ContainerColor,
  content: @Composable ColumnScope.() -> Unit,
) {
  if (LocalUiStyle.current == UiStyle.Miuix) {
    MiuixWindowBottomSheet(
      show = true,
      onDismissRequest = onDismissRequest,
      modifier = modifier,
      title = title,
      content = {
        Column {
          content()
        }
      },
    )
  } else {
    ModalBottomSheet(
      onDismissRequest = onDismissRequest,
      modifier = modifier,
      sheetState = sheetState,
      shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
      containerColor = containerColor,
      content = content,
    )
  }
}
