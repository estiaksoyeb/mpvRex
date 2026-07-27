package xyz.mpv.rex.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf

enum class UiStyle {
  Material,
  Miuix
}

val LocalUiStyle = staticCompositionLocalOf { UiStyle.Material }
