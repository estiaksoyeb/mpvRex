package xyz.mpv.rex.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import xyz.mpv.rex.ui.theme.LocalUiStyle
import xyz.mpv.rex.ui.theme.UiStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
  title: String,
  modifier: Modifier = Modifier,
  navigationIcon: @Composable () -> Unit = {},
  actions: @Composable RowScope.() -> Unit = {},
  scrollBehavior: TopAppBarScrollBehavior? = null,
  colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
    scrolledContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
  ),
) {
  if (LocalUiStyle.current == UiStyle.Miuix) {
    MiuixTopAppBar(
      title = title,
      modifier = modifier,
      navigationIcon = navigationIcon,
      actions = actions,
      color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.background,
    )
  } else {
    TopAppBar(
      title = { Text(title) },
      modifier = modifier,
      navigationIcon = navigationIcon,
      actions = actions,
      scrollBehavior = scrollBehavior,
      colors = colors,
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
  titleContent: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  navigationIcon: @Composable () -> Unit = {},
  actions: @Composable RowScope.() -> Unit = {},
  scrollBehavior: TopAppBarScrollBehavior? = null,
  colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
    scrolledContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
  ),
) {
  if (LocalUiStyle.current == UiStyle.Miuix) {
    MiuixTopAppBar(
      title = "",
      modifier = modifier,
      navigationIcon = navigationIcon,
      actions = actions,
    )
  } else {
    TopAppBar(
      title = titleContent,
      modifier = modifier,
      navigationIcon = navigationIcon,
      actions = actions,
      scrollBehavior = scrollBehavior,
      colors = colors,
    )
  }
}
