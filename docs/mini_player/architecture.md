# Mini Player Architecture

## Overview

mpvRex has one Mini Player UI with two playback origins:

1. **Full-player background playback**: `PlayerActivity` created MPV and continues to own it while
   playback is represented in the Mini Player.
2. **Direct mini-player playback**: playback starts without launching `PlayerActivity`.
   `HeadlessPlaybackController` creates and owns MPV until the user opens the full player.

Both origins publish into the same `MiniPlayerStateManager` and use the same `MiniPlayer`
composable. Consumers should not infer the playback owner from the UI alone.

The implementation is built around one critical constraint:

> `MPVLib` is a process-global native singleton. There can be only one live native MPV handle and
> exactly one component responsible for its ownership at any time.

## Goals

- Start direct playback without creating an Android activity window.
- Avoid transition flicker when audio starts from a browser screen.
- Preserve playback while moving between the browser, notification, and full player.
- Expose one reactive Mini Player state to the Compose UI.
- Route commands to the component that owns the active playlist.
- Prevent duplicate `MPVLib.create()` and unsafe native teardown.

## System Context

```text
Browser screens / selection actions
             |
             v
        MediaUtils
        /        \
       /          \
normal launch   direct launch
     |               |
     v               v
PlayerActivity   HeadlessPlaybackController
     |               |
     +-------+-------+
             |
             v
          MPVLib
     process-global singleton
             |
             +----------------------+
             |                      |
             v                      v
 MiniPlayerStateManager    MediaPlaybackService
             |                      |
             v                      v
        MiniPlayer UI        system notification
```

## Main Components

| Component | Responsibility |
|---|---|
| `MediaUtils` | Selects normal or direct playback and normalizes media URIs. |
| `HeadlessPlaybackController` | Owns MPV before full-player expansion, manages the direct playlist, loads files, resolves thumbnails, and starts the foreground service. |
| `MiniPlayerStateManager` | Process-wide reactive UI state and command-routing facade. |
| `MiniPlayer` | Compose presentation and gestures; it does not own playback. |
| `MainActivity` | Hosts `MiniPlayer` above the app navigation content. |
| `MediaPlaybackService` | MediaSession, notification, system media controls, and live MPV property observation. It does not create MPV. |
| `PlayerActivity` | Full player, activity-scoped managers, video surface, and MPV owner after handoff. |
| `MPVView` | Initializes MPV for a new session or attaches a surface to an existing session. |
| `MpvConfigSync` | Prepares shared MPV assets, configuration, scripts, shaders, and related files before initialization. |
| `MPVLifecycleLock` | Coordinates native creation and teardown state. |

Koin registers `MiniPlayerStateManager` and `HeadlessPlaybackController` as singletons in
`DomainModule.kt`.

## Playback Entry Points

### Automatic direct playback

`MediaUtils.playFile()` and `MediaUtils.playPlaylist()` use direct mode only when:

- `PlayerPreferences.playInMiniPlayerDirectly` is enabled; and
- the initial media item is audio.

Normal video taps continue to launch `PlayerActivity`.

```text
tap audio
  -> MediaUtils checks preference and media type
  -> resolve playlist and resume position
  -> HeadlessPlaybackController.startHeadless()
  -> return without startActivity()
```

### Explicit Open with mini player

`MediaUtils.playInMiniPlayer(videos, startIndex)` is a manual command exposed from selection-mode
overflow menus. It deliberately bypasses the automatic audio-only rule.

- Audio and video items are accepted.
- Multiple selected items become one ordered playlist.
- Headless mode still uses `vo=null`, so a video opened this way plays audio with a static
  thumbnail until the full player attaches a surface.

### Normal full-player playback

When direct conditions do not apply, `MediaUtils` builds the existing `ACTION_VIEW` intent and
starts `PlayerActivity`. `PlayerActivity` initializes and owns MPV normally.

## Direct Session Startup

`HeadlessPlaybackController.startHeadless()` performs the following sequence:

1. Validate the URI list and starting index.
2. Finish any background `PlayerActivity` instance that conflicts with new ownership.
3. Store `activeUris`, `activeIndex`, and `activeTitle`.
4. Register Mini Player next, previous, and close handlers.
5. Reuse an existing native session when possible, otherwise initialize one.
6. Set `vo=null` because there is no display surface.
7. Set `idle=yes` so the native event loop survives `stop` and later reuse.
8. Load the selected item with `MPVLib.command("loadfile", playableUri)`.
9. Apply a saved resume position after `MPV_EVENT_FILE_LOADED` when applicable.
10. Publish current, previous, and next metadata to `MiniPlayerStateManager`.
11. Extract current and adjacent thumbnails asynchronously.
12. Start `MediaPlaybackService` as a foreground service.

### Off-window MPVView

The controller creates an `MPVView` without adding it to a visible window. It obtains an
`AttributeSet` from `R.layout.shorts_dummy_layout`, matching the established Shorts player
construction technique.

The view is still required because `MPVView.initialize()` provides the application-specific MPV
options and initialization hooks. Playback itself is started directly through `MPVLib`, not
through `BaseMPVView.playFile()`, because no surface lifecycle event is expected.

## Playlist Architecture

### Headless playlist

The current direct implementation uses an application-managed playlist:

```text
activeUris: List<Uri>
activeIndex: Int
```

`playNext()` and `playPrevious()` change `activeIndex` and call `playItem(index)`, which issues a
new `loadfile` command. This is intentionally different from MPV's internal playlist.

Consequences:

- Notification and Mini Player navigation must route through the controller handlers.
- Calling `MPVLib.command("playlist-next")` does not navigate a direct playlist.
- `hasNext`, `hasPrevious`, adjacent titles, and adjacent thumbnails are calculated from
  `activeUris`.
- Playlist order is the order supplied by `MediaUtils` or the selection manager.

### Full-player playlist

`PlayerActivity` uses its activity-scoped `PlaylistManager`. During expansion, the headless URI
list and index are passed as intent extras so the activity can populate that manager.

The headless controller and `PlaylistManager` are separate playlist models. Handoff code must keep
their URI list and index aligned.

## Mini Player State

`MiniPlayerStateManager` owns a `StateFlow<MiniPlayerState>` with:

- playback visibility and expansion state;
- title and artist;
- current position and duration;
- pause state;
- current thumbnail and media path;
- previous/next availability, titles, and thumbnails;
- shuffle and repeat state.

### State producers

| Producer | Updates |
|---|---|
| `HeadlessPlaybackController` | Active path, title, playlist boundaries, adjacent metadata, and thumbnails. |
| `MediaPlaybackService` | Position, duration, pause state, MPV metadata, notification artwork, shuffle, and repeat state. |
| `PlayerActivity` | Full-player metadata, playlist state, thumbnails, and background playback state. |
| `MiniPlayerStateManager` | Immediate optimistic state for user commands such as pause, seek, next, and previous. |

The state manager is a presentation and routing layer, not the authoritative native playback
engine. Native MPV properties remain authoritative for actual playback.

### Command routing

Simple commands operate directly on the global MPV handle:

- play/pause -> `pause`
- seek -> `time-pos`
- repeat-one -> `loop-file`

Playlist navigation uses handlers:

```text
MiniPlayerStateManager.playNext()
  -> onNextHandler, when registered
  -> MPV playlist-next fallback otherwise
```

During direct playback, `HeadlessPlaybackController` registers the handlers. During full-player
playback, `PlayerActivity` replaces them with its own playlist functions.

Handler registration is therefore part of ownership transfer and must be cleared when an owner
relinquishes control.

## UI Hosting and Layout

`MainActivity.Navigator()` hosts `MiniPlayer` in the root `Box`, aligned to the bottom center above
navigation bars and app navigation.

`MainActivity` observes `MiniPlayerStateManager.state` to animate Mini Player height and bottom
padding. `MainScreen` also incorporates active Mini Player height into
`LocalNavigationBarHeight`, allowing browser FABs and scroll content to avoid overlap.

The Mini Player renders a bitmap thumbnail. It does not contain an MPV surface or live video
renderer. This separation is what makes headless playback possible without creating an activity
or embedding native video output in the bottom bar.

## Foreground Service and Notification

`MediaPlaybackService` assumes MPV already exists. It never calls `MPVLib.create()`.

On creation it registers an MPV observer for:

- `pause`
- `media-title`
- `metadata/artist`
- `time-pos`

It publishes these values into both Android's `MediaSessionCompat` and
`MiniPlayerStateManager`.

### Notification commands

- Play, pause, and seek operate on MPV properties.
- Next and previous use a bound `PlayerActivity` listener when present.
- Without an activity listener, next and previous route through `MiniPlayerStateManager`, which
  reaches the headless playlist handlers.

This routing is mandatory because direct playlists are not MPV-native playlists.

### Notification content intent

The controller starts the service with `direct_mini_player=true`.

- Direct-session notification tap -> `MainActivity`.
- Normal background-session notification tap -> `PlayerActivity`.

This returns users to the existing Mini Player instead of unexpectedly expanding direct playback.

## Full-Player Handoff

### Launch

`MiniPlayerStateManager.openPlayer()` starts `PlayerActivity`. For an active direct session it
passes:

- `attach_existing_session=true`
- `playlist`
- `playlist_index`
- `title`

No new playable `ACTION_VIEW` URI is required for handoff.

### Ownership transfer

At startup, `PlayerActivity` checks `HeadlessPlaybackController.ownsNativeSession`.

If true:

1. Skip normal `setupMPV()` and therefore skip `MPVLib.create()`.
2. Call `HeadlessPlaybackController.detachForHandoff()`.
3. Call `MPVView.attachToExistingSession()`.
4. Mark the activity MPV state initialized.
5. Restore OSD configuration.
6. Register the activity's MPV observer.
7. Treat the already-loaded MPV path as current playback rather than loading it again.

`attachToExistingSession()` restores GPU video output, registers the activity surface callback,
and re-observes properties. `BaseMPVView.surfaceCreated()` attaches the Android surface to the live
native session.

### Ownership after expansion

After `detachForHandoff()`:

- `HeadlessPlaybackController.ownsNativeSession` is false.
- Direct navigation and close handlers are removed.
- The off-window view reference is released without destroying MPV.
- `PlayerActivity` owns subsequent full-player lifecycle and teardown.

## Lifecycle and Ownership State Machine

```text
NO NATIVE SESSION
  |
  | direct start
  v
HEADLESS ACTIVE
  owner = HeadlessPlaybackController
  isSessionActive = true
  ownsNativeSession = true
  vo = null
  idle = yes
  |
  | close
  v
HEADLESS IDLE
  owner = HeadlessPlaybackController
  isSessionActive = false
  ownsNativeSession = true
  MPV retained, no loaded media
  |
  | new direct start
  +------------------------> HEADLESS ACTIVE
  |
  | normal/direct full-player launch
  v
ACTIVITY OWNED
  owner = PlayerActivity
  isSessionActive = false
  ownsNativeSession = false
  visible surface attached
```

### Close behavior

Closing direct playback intentionally does not call `MPVLib.destroy()`.

The safe sequence is:

1. Remove controller observers and action handlers.
2. Set `idle=yes`.
3. Pause.
4. Send `stop`.
5. Set `vo=null`.
6. Clear headless playlist metadata.
7. Stop `MediaPlaybackService`.
8. Clear Mini Player UI state.

MPV is retained because service destruction asynchronously removes its observer and may issue
native commands. Destroying the global handle first creates a native use-after-destroy race.

### Teardown synchronization

`MPVLifecycleLock` exposes:

- `isNativeInitialized`
- `isTearingDown`
- `onNativeInitialized()`
- `onTeardownStart()` / `onTeardownComplete()`
- `awaitTeardown()`

Any new owner must wait for an active teardown before initializing or attaching.

## Configuration Preparation

Both normal and direct initialization call `MpvConfigSync.prepare(context)` before
`MPVView.initialize()`.

This keeps MPV configuration behavior consistent across owners, including:

- bundled assets;
- user MPV configuration;
- scripts and script options;
- shaders and fonts;
- generated preference-backed configuration.

Configuration preparation performs file I/O and should remain off the main thread when invoked by
the headless controller.

## Concurrency Model

- `HeadlessPlaybackController` uses a `SupervisorJob` on `Dispatchers.Main` for ownership and UI
  transitions.
- Configuration and thumbnail work run on `Dispatchers.IO`.
- Resume uses a one-shot `MPVLib.EventObserver` removed after `MPV_EVENT_FILE_LOADED`.
- Public ownership and playlist fields are `@Volatile` because they are read across activity,
  service, and UI boundaries.
- Native MPV calls remain process-global and must be treated as shared mutable state.

## Architectural Invariants

Changes to Mini Player playback must preserve these invariants:

1. Never call `MPVLib.create()` while `ownsNativeSession` or native initialization indicates a
   live handle.
2. Never destroy MPV from two owners.
3. Do not call JNI after native destruction.
4. A component that relinquishes ownership must remove its observers and command handlers.
5. Direct playlist navigation must use `HeadlessPlaybackController`, not MPV playlist commands.
6. `MediaPlaybackService` observes and controls MPV but does not own or create it.
7. Mini Player UI state is not a replacement for native playback state.
8. Full-player handoff must attach to the live session without reloading the current file.
9. Every MPV initialization path must run `MpvConfigSync.prepare()` first.
10. Local Gradle commands must include `-I local-env.gradle.kts`.

## Known Limitations

### Headless video presentation

Explicit direct playback accepts video, but `vo=null` means the Mini Player displays a static
thumbnail and only audio is rendered until expansion.

### Full-player playlist reconstruction

The activity receives the headless URI list and index, but headless and activity playlist managers
remain separate systems. Changes here require careful index and title synchronization.

### Embedded audio artwork on first handoff

Artwork embedded in audio can be absent immediately after expanding a headless session even though
it appears after reloading the track. Attempts to reselect the attached-picture track have not been
confirmed as a complete fix. This behavior is documented separately in
`bugfix_history.md` and must not be considered an architectural guarantee.

### Shuffle and repeat semantics

Repeat-one is applied directly to MPV. The headless application-managed playlist does not currently
implement full shuffle or repeat-all index traversal itself. UI preference state and actual
headless playlist behavior must be reviewed together before extending these modes.

### History and subtitle managers

`PlaylistManager`, `SubtitleManager`, `HistoryManager`, and related managers are activity-scoped
through `PlayerViewModel`. Direct playback implements the minimum headless path and does not have
the complete activity manager lifecycle before expansion.

## Extension Guidelines

### Adding a new playback command

1. Decide whether it targets native MPV state or playlist-owner state.
2. Put native property commands behind `MiniPlayerStateManager` when shared by both modes.
3. Add an owner handler when behavior differs between headless and activity playback.
4. Update MediaSession routing if the command is exposed through system controls.
5. Test the command before and after full-player handoff.

### Adding headless playlist behavior

Implement it in `HeadlessPlaybackController`, because `activeUris` and `activeIndex` are the
headless source of truth. Do not assume `playlist-pos` or `playlist-count` represents this list.

### Adding new state

1. Add the field to `MiniPlayerState`.
2. Extend `updateState()` with current-value defaults.
3. Identify one authoritative producer.
4. Avoid multiple components overwriting the field with stale values.
5. Verify state restoration during handoff.

### Changing native teardown

Treat teardown changes as high risk. Test service observer removal, close, replay, activity launch,
background playback, and repeated ownership transfers. Kotlin exception handling does not protect
against native segmentation faults.

## Diagnostic Checklist

When playback metadata changes but media does not:

- Check whether the command reached the current playlist owner.
- Inspect `activeUris` and `activeIndex` for headless playback.
- Confirm `MPVLib.path` changed after `loadfile`.
- Do not assume a title or thumbnail update means native playback succeeded.

When the app crashes during close or launch:

- Look for duplicate `MPVLib.create()`.
- Look for JNI calls after `MPVLib.destroy()`.
- Confirm service observer removal order.
- Inspect `ownsNativeSession`, `isSessionActive`, and `MPVLifecycleLock` state.

When expansion shows no video:

- Confirm the activity used `setupMPVForHandoff()`.
- Confirm `detachForHandoff()` did not destroy MPV.
- Confirm `MPVView.attachToExistingSession()` registered the surface callback.
- Inspect `vo`, surface attachment, and video/album-art track selection.

## Verification Matrix

| Scenario | Expected result |
|---|---|
| Automatic preference off, tap audio | Open `PlayerActivity` normally. |
| Automatic preference on, tap audio | Start direct Mini Player without activity flicker. |
| Automatic preference on, tap video | Open `PlayerActivity` normally. |
| Explicit Open with mini player on video | Headless audio plus static thumbnail until expansion. |
| Multi-selection direct playback | Preserve selected order and navigate through every item. |
| Notification next/previous | Change actual media and metadata together. |
| Tap direct notification | Open `MainActivity`, retaining the Mini Player. |
| Close direct Mini Player | Stop playback and notification without native crash. |
| Start another file after close | Reuse idle MPV and play successfully. |
| Expand direct playback | Attach full-player surface without a second native create. |
| Normal background notification | Continue opening `PlayerActivity`. |

Use the required commands for local verification:

```bash
./gradlew compileDebugKotlin -I local-env.gradle.kts
./gradlew installDebug -I local-env.gradle.kts
```

## Source Map

- `app/src/main/kotlin/xyz/mpv/rex/MainActivity.kt`
- `app/src/main/kotlin/xyz/mpv/rex/di/DomainModule.kt`
- `app/src/main/kotlin/xyz/mpv/rex/preferences/PlayerPreferences.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/browser/miniplayer/MiniPlayer.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/browser/miniplayer/MiniPlayerStateManager.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/browser/selection/SelectionManager.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/player/HeadlessPlaybackController.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/player/MediaPlaybackService.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/player/MPVLifecycleLock.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/player/MPVView.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/player/MpvConfigSync.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/player/PlayerActivity.kt`
- `app/src/main/kotlin/xyz/mpv/rex/utils/media/MediaUtils.kt`

## Related Documentation

- [implementation_plan.md](implementation_plan.md): original design and implementation handoff; contains historical assumptions that may no longer match the current code.
- [bugfix_history.md](bugfix_history.md): incident history, confirmed fixes, failed experiments, and regression guidance.
