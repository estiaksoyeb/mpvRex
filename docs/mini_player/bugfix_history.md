# Direct Mini Player: Bug-Fix History and Recovery Guide

## Purpose

This document records the Direct Mini Player problems encountered on the
`feature/direct-mini-player` branch, their root causes, the verified fixes, and approaches that did
not solve remaining issues. Use it as a recovery and debugging reference if the same regressions
reappear.

The Direct Mini Player uses a process-global libmpv instance. Most failures came from treating that
singleton like an activity-owned player or bypassing the controller that owns the headless
playlist.

## Architecture

Direct playback follows this path:

```text
Browser selection/tap
  -> MediaUtils
  -> HeadlessPlaybackController
  -> off-window MPVView with vo=null
  -> MiniPlayerStateManager
  -> MediaPlaybackService / notification
```

Full-player handoff follows this path:

```text
Mini player thumbnail
  -> MiniPlayerStateManager.openPlayer()
  -> PlayerActivity
  -> HeadlessPlaybackController.detachForHandoff()
  -> MPVView.attachToExistingSession()
```

Ownership rules:

- `MPVLib` is a process-global singleton.
- Only one owner may create or destroy the native instance.
- `HeadlessPlaybackController` owns MPV during direct mini-player playback.
- `PlayerActivity` becomes the owner after a full-player handoff.
- UI metadata changes are not proof that native playback changed.

## Confirmed Fixes

### 1. Close button terminated the app

#### Symptoms

- Tapping the mini-player close icon immediately killed the app.
- Logs showed `SIGSEGV`, signal 11, or process termination by signal 9.
- The failure occurred during or immediately after `MPVLib.destroy()`.

#### Faulty sequence

```text
MiniPlayer close
  -> MiniPlayerStateManager.closeMiniPlayer()
  -> HeadlessPlaybackController.stop()
  -> MPVLib.command("quit")
  -> MPVLib.destroy()
  -> stopService(MediaPlaybackService)
  -> MediaPlaybackService.onDestroy()
  -> MPVLib.removeObserver() / MPVLib.command("stop")
```

The service called JNI after the global native singleton had already been freed. This was a native
use-after-destroy race. Kotlin `runCatching` cannot catch native signals.

#### Fix

`HeadlessPlaybackController.stop()` was changed to stop playback without destroying MPV:

- Set `idle=yes`.
- Pause playback.
- Send `stop`, not `quit`.
- Set `vo=null`.
- Clear controller playlist and session metadata.
- Retain the native instance idle for reuse.
- Let `MediaPlaybackService` remove its observer while MPV remains valid.

An `ownsNativeSession` state was added so `PlayerActivity` can attach to the retained instance
instead of calling `MPVLib.create()` again.

#### Why MPV is retained

Destroying MPV from the close path is unsafe because service teardown is asynchronous. Keeping it
idle prevents both the observer race and repeated native initialization. `PlayerActivity` can later
take ownership and use its normal teardown path.

#### Commit

`2fa308c` - `Fix direct mini player lifecycle and playback`

### 2. Playback failed after close and replay

#### Symptoms

- Closing no longer crashed.
- Selecting another file updated the title and thumbnail.
- No audio played.

#### Root cause

`BaseMPVView` initializes MPV with `idle=once`. After `stop`, the retained instance could emit
shutdown and stop accepting later `loadfile` commands. The Kotlin object still existed, making the
failure look like a metadata-only problem.

#### Fix

The headless controller sets `idle=yes`:

- After new headless initialization.
- Before reusing an existing native session.
- Before issuing `stop` on close.

This keeps MPV's event loop alive and able to accept the next `loadfile` command.

#### Regression test

1. Start audio in the direct mini player.
2. Close the mini player.
3. Select another audio file.
4. Confirm playback starts, not only metadata updates.
5. Repeat the close/replay cycle several times.

### 3. Explicit Open with mini player action

#### Requirement

The selection overflow menu needed an explicit action that:

- Works for audio and video.
- Works independently of the automatic audio-only preference.
- Uses every selected file as a playlist.
- Preserves selection/list order.

#### Implementation

`MediaUtils.playInMiniPlayer(videos, startIndex)` normalizes file URIs and sends the complete
ordered URI list to `HeadlessPlaybackController`.

`SelectionManager.playSelectedInMiniPlayer()` provides shared selection behavior. The action was
wired into the applicable file-system, video-list, media-library, and playlist-detail menus.

This is intentionally different from ordinary tapping:

- Ordinary tapping follows `playInMiniPlayerDirectly` and is audio-only.
- Open with mini player is explicit and accepts audio or video.

#### Commit

`c43cb8f` - `Add selected files mini player action`

### 4. Notification next/previous changed metadata only

#### Symptoms

- Notification next/previous changed title and thumbnail.
- The same media continued playing.
- Mini-player UI navigation worked.

#### Root cause

The notification used MPV's internal playlist:

```kotlin
MPVLib.command("playlist-next")
MPVLib.command("playlist-prev")
```

Direct mini-player playlists are maintained by `HeadlessPlaybackController` as `activeUris` and
`activeIndex`; they are not loaded into MPV's internal playlist. The notification bypassed the
controller.

#### Fix

When no bound `PlayerActivity` listener exists, `MediaPlaybackService` calls:

```kotlin
miniPlayerStateManager.playNext()
miniPlayerStateManager.playPrevious()
```

The state manager delegates to the correct owner:

- Headless controller during direct playback.
- `PlayerActivity` during full-player playback.
- MPV playlist commands only as the final fallback.

#### Regression test

1. Start a multi-file direct mini-player playlist.
2. Use notification next and previous.
3. Verify the actual media changes.
4. Verify title, thumbnail, and playlist position change together.

### 5. Notification tap opened the full player

#### Requirement

For a direct mini-player session, tapping the notification should return to the app because the
mini player is already present. It should not open `PlayerActivity`.

#### Fix

The headless controller marks service start intents with:

```text
direct_mini_player=true
```

`MediaPlaybackService` stores the session type and targets:

- `MainActivity` for direct mini-player sessions.
- `PlayerActivity` for normal full-player background playback.

#### Commit

`4220ca6` - `Fix direct mini player notification controls`

## Supporting Behavior

### Audio-only automatic playback

The automatic `playInMiniPlayerDirectly` preference applies only to audio. Normal video taps open
`PlayerActivity` unless the user explicitly chooses Open with mini player.

### Native ownership handoff

`PlayerActivity` checks whether `HeadlessPlaybackController` owns the singleton. If so, it attaches
to the existing session and does not call `MPVLib.create()` a second time.

### Mini Player preferences section

The player preferences UI groups these options in a dedicated section:

- Background Playback Mode
- Play audio in mini player

## Unresolved or Deferred Issues

### Embedded artwork missing after expansion

#### Symptom

1. Start audio with embedded artwork in the direct mini player.
2. Expand into `PlayerActivity`.
3. Playback continues, but artwork is missing.
4. Go to the next track and back.
5. Artwork appears after the file reload.

This proves the artwork is valid. The issue is specific to transferring media loaded while
`vo=null`. Reloading recreates MPV's attached-picture decoder.

#### Attempts that did not solve it

These experiments compiled and installed but did not fix the reported behavior:

1. Set `vid=auto` during `MPVView.attachToExistingSession()`.
2. Send a zero-distance exact seek after surface attachment.
3. Locate `track-list/*/albumart=true` and select its MPV track ID.
4. Seek after selecting the embedded-art track.

These are not confirmed fixes and should not be restored without further evidence.

#### Recommended investigation

- Record `vid`, `video-format`, `video-params/*`, `track-list`, `vo-configured`, and `current-vo`
  before expansion, after surface attachment, and after next/previous.
- Compare handoff events with events emitted by a real `loadfile`.
- Research a supported attached-picture decoder reload for the current track.
- Consider rendering the already-extracted mini-player bitmap as a full-player artwork overlay
  instead of depending exclusively on MPV's attached-picture video output.
- If reloading is unavoidable, preserve `time-pos`, pause state, speed, and selected tracks.

### File explorer currently-playing highlight

An experiment added a playing highlight to `VideoCard`/`BaseMediaCard`, driven by
`MiniPlayerState.videoPath`. The result did not match the requested behavior and was intended to be
reverted. It is not a confirmed fix.

Before revisiting, clarify:

- File-system browser only, or all media lists?
- Highlight row, thumbnail, title, or show a playing icon?
- Should paused media remain highlighted?
- Should full-player and background playback count?
- How should content URIs, file URIs, and canonical paths be compared?

### Full-player playlist after handoff

The full player may not reconstruct the expected playlist after entering from direct playback.
This was explicitly excluded from the notification re-entry request and was not addressed.

## Verification Commands

Every local Gradle invocation must use the project init script.

```bash
./gradlew compileDebugKotlin -I local-env.gradle.kts
./gradlew installDebug -I local-env.gradle.kts
```

Never run local Gradle commands without `-I local-env.gradle.kts`.

## Regression Checklist

Run this checklist after changing native ownership, service teardown, notification controls, or
headless playlist code:

1. Start audio directly in the mini player.
2. Tap a video normally and verify it opens full screen.
3. Use Open with mini player on one audio file, one video, and a mixed multi-selection.
4. Navigate next/previous from the mini-player UI.
5. Navigate next/previous from the notification and confirm actual playback changes.
6. Tap a direct-session notification and confirm it opens the main app.
7. Close the mini player and confirm the app does not crash.
8. Start another file after close and confirm playback starts.
9. Repeat close/replay several times to expose lifecycle races.
10. Expand the mini player and confirm no second MPV instance is created.
11. Confirm regular background-playback notifications still open `PlayerActivity`.

## Relevant Files

- `app/src/main/kotlin/xyz/mpv/rex/ui/player/HeadlessPlaybackController.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/player/PlayerActivity.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/player/MPVView.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/player/MPVLifecycleLock.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/player/MediaPlaybackService.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/browser/miniplayer/MiniPlayerStateManager.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/browser/miniplayer/MiniPlayer.kt`
- `app/src/main/kotlin/xyz/mpv/rex/utils/media/MediaUtils.kt`
- `app/src/main/kotlin/xyz/mpv/rex/ui/browser/selection/SelectionManager.kt`

## Git Reference

| Commit | Summary |
|---|---|
| `2fa308c` | Fix direct mini player lifecycle and playback |
| `c43cb8f` | Add selected files mini player action |
| `4220ca6` | Fix direct mini player notification controls |

This file is the consolidated operational reference for confirmed fixes and known remaining
issues. Older handoff and planning documents may contain additional historical context.
