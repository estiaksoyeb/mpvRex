# CODEMAP: mpvRex Navigation Reference

**Date of Generation:** Friday, April 24, 2026

## Architecture Summary
mpvRex follows a modular, **Manager-driven architecture** designed for maintainability and scalability. The `PlayerViewModel` serves as a central **Coordinator**, delegating specialized tasks to dedicated managers (`PlaylistManager`, `SubtitleManager`, `PlaybackManager`, etc.). This separation ensures that the playback logic remains decoupled from the UI state and event handling.

Media discovery is powered by the **`CoreMediaScanner`**, a unified engine that synchronizes Android's `MediaStore` with direct filesystem scanning. This builds a hierarchical in-memory tree that provides recursive counts and "NEW" badge status across the application. The UI is built with **Jetpack Compose (Material3)**, utilizing a **Unified Card Architecture** (`BaseMediaCard`) to maintain visual consistency across all media browsers (Filesystem, Network, Playlists).

## Refactor Status
The codebase is mid-transition from a legacy monolithic structure to the current **Ops/Manager pattern**.
- **Player Core:** Mostly `[STABLE]`. Refactored into managers.
- **Browser Layer:** `[IN FLUX]`. Some duplication exists between `FileSystemBrowserViewModel` and `NetworkBrowserViewModel` that hasn't been fully consolidated into `BaseBrowserViewModel`.
- **Network Proxy:** `[IN FLUX]`. The streaming proxy implementation for network protocols is stable but subject to optimization.
- **Legacy Residue:** Some `TODO: strings` and `TODO: refactor` comments remain in the UI layer, particularly in preferences and specialized dialogs.

---

## 📂 Project Structure & Significant Files

### 📦 `app.marlboroadvance.mpvex`
*Base package and core application logic.*

- **`App.kt`**: Application entry point, initializes Koin DI. `[STABLE]`
- **`MainActivity.kt`**: Main entry for the UI, handles navigation. `[STABLE]`

### 📦 `...mpvex.ui.player`
*Player implementation and coordination.*

- **`PlayerActivity.kt`**: entry point for playback; handles lifecycle, system UI, and PIP. `[STABLE]`
  - `playFile(uri)`: Starts playback of a specific URI.
  - `handleBackPress()`: Manages navigation and PIP entry.
- **`PlayerViewModel.kt`**: Coordinator that delegates to managers and holds UI state. `[STABLE]`
  - `togglePause()`: Toggles playback state.
  - `showControls()` / `hideControls()`: Manages UI visibility.
- **`PlaybackManager.kt`**: Handles seeking, speed, and AB loop logic. `[STABLE]`
  - `seekTo(pos)`: Precise or keyframe-based absolute seeking.
  - `seekBy(offset)`: Relative seeking.
- **`PlaylistManager.kt`**: Manages playlist state, shuffling, and windowed loading. `[STABLE]`
  - `setPlaylist(items)`: Initializes or updates the active playlist.
  - `getNextIndex()` / `getPreviousIndex()`: Logic for track navigation.
- **`SubtitleManager.kt`**: Manages local subtitles and online Wyzie search. `[STABLE]`
  - `addSubtitle(uri)`: Adds a subtitle track to the current player instance.
  - `searchSubtitles(query)`: Triggers online search via Wyzie.
- **`MPVView.kt`**: Native wrapper for `libmpv`. `[STABLE]`
- **`PlayerObserver.kt`**: Bridges native MPV events to Kotlin flows. `[STABLE]`

### 📦 `...mpvex.ui.browser`
*Media discovery and browsing UI.*

- **`BaseBrowserViewModel.kt`**: Base logic for all browser views. `[STABLE]`
- **`FileSystemBrowserViewModel.kt`**: Handles local storage navigation. `[IN FLUX]`
- **`NetworkBrowserViewModel.kt`**: Manages remote streaming connections. `[IN FLUX]`
- **`BaseMediaCard.kt`**: Standardized UI component for all media items. `[STABLE]`

### 📦 `...mpvex.utils.storage`
*Low-level media scanning and filesystem operations.*

- **`CoreMediaScanner.kt`**: The heart of media discovery; builds the hierarchy. `[STABLE]`
  - `getFlatMediaFolders()`: Returns a flat list for Album/Folder views.
  - `getFoldersInDirectory()`: Returns children for tree-based browsing.
- **`FileSystemOps.kt`**: Pure filesystem tasks (parsing, storage roots). `[STABLE]`

---

## 🔄 Core Feature Flows

### 1. Video Playback Start
`PlayerActivity.onCreate` → `parsePathFromIntent` → `MPVView.playFile(uri)` → `MPVLib.command("loadfile", path)` → `PlayerObserver` (detects `file-loaded`) → `HistoryManager` (restores position).

### 2. Seek / Scrubbing
`GestureHandler.kt` (onScroll/onDoubleTap) OR `Seekbar.kt` interaction → `PlayerViewModel.playbackManager.seekTo/seekBy` → `MPVLib.command("seek", ...)` → `precisePosition` (StateFlow updates OSD).

### 3. Subtitle Loading
`SubtitleManager.addSubtitle` → `MPVLib.command("sub-add", mpvPath, mode)` → `libmpv` handles rendering via native OSD.

### 4. Gesture Controls (Brightness, Volume, Seek)
`GestureHandler.kt` (intercepts touch) → `PlayerViewModel` updates (e.g., `volume`, `brightness`, `precisePosition`) → UI displays feedback overlays.

### 5. Player UI Show/Hide
`PlayerViewModel.showControls()` → `controlsShown` (MutableStateFlow) → `PlayerControls.kt` (animates visibility) → `hideTimer` resets.

### 6. Settings Application
`SettingsManager` / `UiPreferences` (Reactive update) → `PlayerActivity`/`PlayerViewModel` observe flow → `MPVLib.setProperty(...)` applied at runtime.

---

## 🚩 Refactor Smells & Known Duplicates
- **`[DUPLICATE?]`**: `FileSystemBrowserViewModel` and `NetworkBrowserViewModel` have similar `refresh()` and navigation logic that could be further abstracted into `BaseBrowserViewModel`.
- **`[UNCLEAR]`**: `RecentlyPlayedOps` vs `HistoryManager`. `RecentlyPlayedOps` appears to be a thin wrapper and might be redundant after the manager refactor.
- **`[TODO]`**: String externalization in `PlayerButton.kt` and preference screens.

# Last updated: Friday, April 24, 2026. Regenerate this file after major refactor milestones.

### 📦 `...mpvex.ui.preferences`
*Settings screens and configuration management.*

- **`PreferencesScreen.kt`**: Root settings screen with categorized navigation. `[STABLE]`
- **`PlayerPreferencesScreen.kt`**: Core player behavior settings (HW decoding, rotation). `[STABLE]`
- **`AppearancePreferencesScreen.kt`**: UI themes, colors, and visual scaling. `[STABLE]`
- **`GesturePreferencesScreen.kt`**: Configures swipe, tap, and double-tap behaviors. `[STABLE]`
- **`SubtitlesPreferencesScreen.kt`**: Global styling and default search settings for subs. `[STABLE]`
- **`AudioPreferencesScreen.kt`**: Audio boost, delay increments, and channel settings. `[STABLE]`
- **`DecoderPreferencesScreen.kt`**: Low-level hardware/software decoder selection. `[STABLE]`
- **`AdvancedPreferencesScreen.kt`**: Cache sizes, Lua script management, and logs. `[STABLE]`
- **`CustomButtonScreen.kt`**: Interface for defining user-created player buttons. `[STABLE]`
- **`LuaScriptsScreen.kt`**: Lists and toggles for integrated Lua scripts. `[STABLE]`

### 📦 `...mpvex.ui.browser.dialogs`
*Reusable interaction dialogs for the browser layer.*

- **`AddConnectionDialog.kt`**: Forms for FTP/SMB/WebDAV connection details. `[STABLE]`
- **`SortDialog.kt`**: Options for ordering media lists (Name, Date, Size). `[STABLE]`
- **`RenameDialog.kt`**: Unified file/folder renaming utility. `[STABLE]`
- **`DeleteConfirmationDialog.kt`**: Safety gate for media deletion. `[STABLE]`
- **`FilePickerDialog.kt`**: Internal file selector for preferences (e.g., custom fonts). `[STABLE]`

### 📦 `...mpvex.ui.player.controls`
*Player UI components and interaction logic.*

- **`GestureHandler.kt`**: Centralized touch event processor for brightness, volume, and seek. `[STABLE]`
  - `onScroll()`: Handles vertical (vol/bright) and horizontal (seek) scrolls.
  - `onDoubleTapSeek()`: Processes multi-tap seeking increments.
- **`PlayerControls.kt`**: Top-level container for all OSD elements. `[STABLE]`
- **`PlayerPanels.kt`**: Bottom sheets and side panels for subtitle/audio settings. `[STABLE]`

### 📦 `...mpvex.utils.media` & `utils.history`
*Media processing and state persistence "Ops".*

- **`HistoryManager.kt`**: Saves and restores playback positions and track selections. `[STABLE]`
- **`RecentlyPlayedOps.kt`**: Lightweight utility for managing the "Recent" list. `[STABLE]`
- **`MediaInfoOps.kt`**: Extracts technical metadata (codecs, bitrate) from files. `[STABLE]`
- **`CopyPasteOps.kt`**: Manages background file move/copy operations with progress. `[IN FLUX]`
- **`PlaybackStateOps.kt`**: Low-level bridge for syncing Room DB and MPV state. `[STABLE]`
- **`SubtitleOps.kt`**: Helper for locating and matching local subtitle files. `[STABLE]`
- **`M3UParser.kt`**: Specialized parser for IPTV and local M3U playlists. `[STABLE]`

### 📦 `...mpvex.ui.browser.networkstreaming.proxy`
*Network protocol adaptation for MPV.*

- **`NetworkStreamingProxy.kt`**: Local server that proxies SMB/FTP streams for libmpv. `[IN FLUX]`
- **`ProxyLifecycleObserver.kt`**: Binds proxy server lifespan to UI components. `[STABLE]`

### 📦 `...mpvex.di`
*Dependency Injection modules (Koin).*

- **`PreferencesModule.kt`**: Provides all preference stores and settings managers. `[STABLE]`
- **`DatabaseModule.kt`**: Initializes Room DB, DAOs, and migrations. `[STABLE]`
- **`DomainModule.kt`**: Binds repositories to their implementations. `[STABLE]`

### 📦 `...mpvex.repository`
*Data access layer.*

- **`MediaFileRepository.kt`**: Delegator for local file operations. `[STABLE]`
- **`NetworkRepository.kt`**: Manages saved remote connections. `[STABLE]`
- **`WyzieSearchRepository.kt`**: Interface for online subtitle providers. `[STABLE]`

