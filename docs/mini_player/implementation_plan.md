# Direct Mini Player Playback — Implementation Handoff

> Self-contained handoff for a fresh chat session. Goal: make tapping a media item
> start playback **directly in the bottom Mini Player bar with ZERO PlayerActivity
> window flicker**, launching full-screen `PlayerActivity` only when the user expands.

---

## 0. Build / workflow rules (from BUILD_PREFERENCES.md — obey strictly)

- Platform: Termux / AndroidIDE on Android.
- **NEVER** run any Gradle command without `-I local-env.gradle.kts`.
  - Quick compile check: `./gradlew compileDebugKotlin -I local-env.gradle.kts`
  - Install to device (do this automatically at end of changes): `./gradlew installDebug -I local-env.gradle.kts`
- Do NOT modify `app/build.gradle.kts` for local hacks — use `local-env.gradle.kts`.
- **NEVER commit or push unless explicitly asked.** Use rebase, not merge. Keep refactors and visual fixes in separate commits.
- Commit style (from user memory): NO `Co-Authored-By` line; reference issues as "Addressed #N".
- Before deep-diving files, consult `knowledge/index.md` (OKF catalog).
- Architecture: Ops/Manager pattern. `PlayerViewModel` is a coordinator delegating to
  `PlaybackManager`, `PlaylistManager`, `SubtitleManager`, `CustomButtonManager`, `HistoryManager`.
  Never scan filesystem directly — use `CoreMediaScanner`. Use `BaseMediaCard` for media lists.

---

## 1. The problem (from DIRECT_MINI_PLAYER_ISSUE.md)

Tapping a video calls `MediaUtils.playFile()/playPlaylist()`, which does
`startActivity(PlayerActivity)`. Any way of launching an Activity and hiding it
(translucent theme, `taskAffinity=":player"` + `FLAG_ACTIVITY_NEW_TASK`,
`moveTaskToBack`, `FLAG_ACTIVITY_NO_ANIMATION`, `overrideActivityTransition(…,0,0)`)
**still makes Android's WindowManager create/animate a window → visible flash/slide**.
`moveTaskToBack` in a shared task minimizes the whole app to Home.

**Conclusion: you cannot fully hide an Activity launch. The only flicker-free fix is
to NOT launch an Activity at all** — run MPV headless and only launch `PlayerActivity`
on expand, attaching its Surface to the already-running MPV session.

The user chose this **"true headless playback"** approach (over refining the hack).

---

## 2. Architecture facts established this session (trust these)

### MPV is a global native singleton
- `is.xyz.mpv.MPVLib` — ALL control is **static** (`MPVLib.command`, `setPropertyX`,
  `getPropertyX`, `observeProperty`, `addObserver`). Only **one** native mpv handle may
  exist. A second `MPVLib.create()` without a prior `destroy()` will crash natively.

### `BaseMPVView` (`mpvRex-libmpv/app/src/main/java/is/xyz/mpv/BaseMPVView.kt`) — DO NOT EDIT
Decision: **app-only changes**. Key behavior of this vendored class:
- `initialize(configDir, cacheDir)` = `MPVLib.create(context)` → set config opts →
  `initOptions()` → `MPVLib.init()` → `postInitOptions()` → `force-window=no`,
  `idle=once` → `holder.addCallback(this)` → `observeProperties()`.
- `surfaceCreated()` → `MPVLib.attachSurface(holder.surface)` → `force-window=yes` →
  if a pending `playFile(path)` was queued, run `loadfile`; **else restore `vo`**.
- `surfaceDestroyed()` → `vo=null`, `force-window=no`, `MPVLib.detachSurface()`.
- `destroy()` = `holder.removeCallback(this)` → `MPVLib.destroy()`.
- **So today: loadfile is coupled to surfaceCreated, and MPV is owned by whichever
  view called `initialize()`.** For headless audio we bypass this by calling
  `MPVLib.command("loadfile", …)` directly with `vo=null` (no surface needed).

### `MPVView` (`app/src/main/kotlin/xyz/mpv/rex/ui/player/MPVView.kt`) — we CAN edit
- Extends `BaseMPVView`, is a `KoinComponent`, injects all the preference stores +
  `Anime4KManager`. Overrides `initOptions()` (hwdec, vo=gpu/gpu-next, subs, audio,
  Anime4K shaders), `observeProperties()` (big `observedProps` map — **note the comment
  at lines 229-233**: every property consumed via `MPVLib.propX[...]` must be listed here
  because `MPVLib.Property.map` is a process cache that skips re-`observeProperty` on
  fresh native handles), `postInitOptions()`.
- Inherits public `holder: SurfaceHolder` and implements `SurfaceHolder.Callback`.
- **We can add app-side methods here**, e.g.
  `fun attachToExistingSession() { holder.addCallback(this); observeProperties() }`
  to register the surface callback WITHOUT calling `MPVLib.create()/init()` — this is
  the key to attaching PlayerActivity's surface to an MPV created elsewhere.

### `ShortsPlayerHost` (`app/src/main/kotlin/xyz/mpv/rex/ui/browser/shorts/ShortsPlayerHost.kt`)
Proof that an `MPVView` can be built **outside PlayerActivity** from a dummy layout:
```
val parser = context.resources.getLayout(R.layout.shorts_dummy_layout)
// advance to START_TAG, val attrs = Xml.asAttributeSet(parser)
MPVView(context, attrs).apply { layoutParams = MATCH_PARENT×MATCH_PARENT }
…
mpvView.initialize(context.filesDir.path, context.cacheDir.path)
MPVLib.observeProperty(...); MPVLib.addObserver(observer)
// onDispose: MPVLib.removeObserver(observer); mpvView.destroy()
```
Use the same `R.layout.shorts_dummy_layout` + `Xml.asAttributeSet` trick to instantiate
a headless `MPVView` in the controller (see §4).

### `PlayerActivity` (`app/src/main/kotlin/xyz/mpv/rex/ui/player/PlayerActivity.kt`, ~4375 lines)
- `val player by lazy { binding.player }` (line 204) — the `MPVView` is a SurfaceView
  baked into the Activity's view binding.
- `onCreate` ALWAYS calls `setupMPV()` (line ~357), then `viewModel.onMpvCoreInitialized()`,
  `MediaPlaybackService.createNotificationChannel`, `setupAudio`, `setupPlayerControls`,
  `setupPipHelper`, `setupMediaSession`, then sets
  `miniPlayerStateManager.onNextHandler = { playNext() }` / `onPreviousHandler` (367-368),
  and collects `miniPlayerStateManager.state` to `finish()` when playback stops during
  background mode (370-378).
- `setupMPV()` (lines 1027-1069): `MPVLifecycleLock` teardown await → `Utils.copyAssets(this)`
  → `syncFromUserMpvDirectory()` (big private config-sync: mpv.conf/input.conf/scripts/
  script-opts/shaders/fonts, with `copyMPVConfigFromPreferences()` fallback) → hdr-toys
  conf → **`player.initialize(filesDir.path, cacheDir.path)`** (1056) → `mpvInitialized = true`
  → set `osd-level` → `MPVLib.addObserver(playerObserver)`.
- Intent → playback (lines ~440-497): builds playlist from extras/db/folder, extracts
  `fileName`, sets HTTP headers, then:
  - if playable URI present → `player.playFile(playableUri)` (491) (or M3U / loadfile).
  - `isAlreadyPlayingCurrent` (472): **no playable URI in intent AND MPV already has a
    non-null `path`** → `isReady = true; enableVideoAfterBackground()` — this is the
    EXISTING "re-attach to a live session" path; the headless-expand handoff should mirror it.
- Background playback keeps the Activity ALIVE in the back stack:
  - `isManualBackgroundPlayback` / `isInBackgroundPlayback` flags.
  - `cleanupMPV()` (745): returns early / skips `MPVLib.destroy()` when
    `isManualBackgroundPlayback` (line 749). Full teardown path: removeObserver → pause →
    `quit` → `vo=null` → `detachSurface()` → `MPVLib.destroy()` under `MPVLifecycleLock`.
  - `disableVideoForBackground()` (~3484): `vo=null` / disable vid, sets window bg transparent.
  - `enableVideoAfterBackground()` (~3513): restores vid, window bg black.
- Service binding: `mediaPlaybackService`, `serviceBound` (lines 3360-3370);
  `startBackgroundPlayback()`/`endBackgroundPlayback()` start/stop the foreground service.

### `MediaPlaybackService` (`app/src/main/kotlin/xyz/mpv/rex/ui/player/MediaPlaybackService.kt`, 611 lines)
- Foreground `MediaBrowserServiceCompat` + `MediaSessionCompat`, and an `MPVLib.EventObserver`.
- **Does NOT create MPV.** Assumes MPV is already alive; in `onCreate` it
  `MPVLib.addObserver(this)` + observes `pause`, `media-title`, `metadata/artist`, `time-pos`.
- On events → `updateMediaSession()` → **`miniPlayerStateManager.updateState(...)`** (341-352)
  + notification. `setMediaInfo(title, artist, thumbnail)` sets the static `thumbnail` and
  refreshes. Notification tap intent → `PlayerActivity` (`FLAG_ACTIVITY_SINGLE_TOP|CLEAR_TOP`).
- `onTaskRemoved` → `MPVLib.command("quit")` + kills process. `onDestroy` →
  removeObserver, stop foreground, `MPVLib.command("stop")`, `miniPlayerStateManager.clearState()`.

### `MiniPlayerStateManager` (`app/src/main/kotlin/xyz/mpv/rex/ui/browser/miniplayer/MiniPlayerStateManager.kt`, Koin singleton)
- Holds `StateFlow<MiniPlayerState>` (title/artist/pos/dur/paused/thumbnail/hasNext/…).
- Drives MPV via static calls: `togglePlayPause`, `seekTo`, `cycleRepeatMode`, `toggleShuffle`,
  `playNext`/`playPrevious` (use `onNextHandler`/`onPreviousHandler` if set, else
  `MPVLib.command("playlist-next/prev")`).
- `openPlayer(context)` launches `PlayerActivity` (currently modified — see §3).
- `clearState()` resets `isPlaybackActive=false`.

### `MiniPlayer` UI (`app/src/main/kotlin/xyz/mpv/rex/ui/browser/miniplayer/MiniPlayer.kt`)
- **Renders a static `thumbnail` bitmap + transport controls** (line ~552 `Image(bitmap=…)`),
  NOT a live video surface. => Headless "mini player playback" is really **background audio
  with a thumbnail**; video decode is only needed on expand. This is what makes the whole
  approach feasible without embedding a surface in the bar.

### `MediaUtils` (`app/src/main/kotlin/xyz/mpv/rex/utils/media/MediaUtils.kt`)
- `object … : KoinComponent`, injects `VideoMetadataCacheRepository`, `PlaybackStateRepository`,
  and (added this session) `PlayerPreferences`.
- `playFile(source, context, launchSource, startInMiniPlayer)` and
  `playPlaylist(videos, startIndex, context, launchSource, playlistId, startInMiniPlayer)` —
  the entry points UI screens call. They build an `ACTION_VIEW` intent and `startActivity`.

### `PlayerPreferences`
- `val playInMiniPlayerDirectly = preferenceStore.getBoolean("play_in_mini_player_directly", false)`
  (added). Settings toggle added in `PlayerPreferencesScreen.kt` (~line 195). **KEEP BOTH.**

---

## 3. Uncommitted "hide the activity" attempt — REVERT these (keep pref + toggle)

Current `git diff` contains a failed attempt to hide the Activity. **Revert all of it
EXCEPT the `playInMiniPlayerDirectly` preference and its settings toggle.** Specifically undo:

- `AndroidManifest.xml`: `android:taskAffinity=":player"` on `PlayerActivity`.
- `res/values/themes.xml`: `NoAnimation` style + `Theme.mpvex.Player.Translucent` style.
- `PlayerActivity.kt`: the `startInMiniPlayer` branches in `onCreate` (setTheme Translucent /
  overrideActivityTransition) and in the intent-handling method (~line 2817) that do
  `disableVideoForBackground()` + `moveTaskToBack(true)`; the `setTheme` split; and the
  `window.setBackgroundDrawableResource(...)` lines added to
  `disableVideoForBackground`/`enableVideoAfterBackground` (evaluate — the transparent-bg
  ones were for the hack; keep only if still needed).
- `MiniPlayerStateManager.openPlayer`: revert to
  `FLAG_ACTIVITY_SINGLE_TOP or FLAG_ACTIVITY_REORDER_TO_FRONT` (drop `NEW_TASK` +
  `reopen_player` extra) — UNLESS §4 handoff needs a dedicated extra (it will; see below).
- `MediaUtils.kt`: remove `start_in_mini_player` intent extra + `FLAG_ACTIVITY_NO_ANIMATION`
  + `NEW_TASK` + the branching anim options. Replace the whole "startActivity when direct"
  branch with a call into the new headless controller (§6). Keep the `playerPreferences`
  injection and the `startInMiniPlayer` PARAMETER (now routed differently).

> Keep it a clean diff: revert-the-hack can be one commit, the headless feature another
> (per "separate commits" rule).

---

## 4. Target design — Headless MPV controller (app-only)

### 4.1 New Koin singleton: `HeadlessPlaybackController`
Location: `app/src/main/kotlin/xyz/mpv/rex/ui/player/HeadlessPlaybackController.kt`
(register in the Koin module where the other singletons/managers are declared — find via
`grep -rn "MiniPlayerStateManager\b" app/src/main/kotlin/**/di* ` or the app module file).

Responsibilities:
1. Own the **single global MPV** when playback starts headless.
2. Build an off-window `MPVView` (dummy layout + `Xml.asAttributeSet`, exactly like
   `ShortsPlayerHost`). It needs a `Context` — pass `applicationContext` in the start call.
3. Run the SAME bootstrap PlayerActivity.setupMPV does — asset copy + config sync (see §5
   for the required refactor to avoid duplication) — then `mpvView.initialize(filesDir, cacheDir)`.
4. Register `MPVLib.addObserver(...)` for the minimal events needed to feed
   `MiniPlayerStateManager` first-load state (or rely on `MediaPlaybackService`, which
   already does this — prefer starting the service and letting it own the observer).
5. Configure playlist/subs/resume as needed (§7), set `vo=null` (audio only, no surface),
   then `MPVLib.command("loadfile", uri)`.
6. `context.startForegroundService(MediaPlaybackService)` with `media_title`/`media_artist`
   extras, and set thumbnail via `MediaPlaybackService.thumbnail` / `setMediaInfo`.
7. Expose:
   - `fun startHeadless(source/playlist, context, launchSource, playlistId)`
   - `val isSessionActive: Boolean` (true once headless MPV created & not handed off/destroyed)
   - `fun detachForHandoff()` — called when PlayerActivity takes over the surface:
     stop being the surface-less owner but **do NOT destroy** MPV; relinquish ownership flag.
   - `fun stop()` — teardown when playback is dismissed and no Activity owns it.

**Ownership model (critical):** at any instant exactly ONE of
{HeadlessPlaybackController, PlayerActivity} "owns" the global MPV handle and is responsible
for `MPVLib.destroy()`. Track with a shared flag (e.g. a `@Volatile var mpvOwner` enum on the
controller, or reuse `MPVLifecycleLock`). Never call `MPVLib.create()` while a session is
active; never `destroy()` from two places.

### 4.2 Handoff to full-screen on expand
`MiniPlayer` expand / `MiniPlayerStateManager.openPlayer(context)` launches `PlayerActivity`
with an extra e.g. `putExtra("attach_existing_session", true)` and NO playable URI.

In `PlayerActivity.onCreate`:
- If `attach_existing_session == true` AND `HeadlessPlaybackController.isSessionActive`:
  - **SKIP `setupMPV()`** (do not `MPVLib.create`/`initialize` again).
  - Set `mpvInitialized = true` manually.
  - Call new `player.attachToExistingSession()` (adds surface callback + re-`observeProperties`)
    → `surfaceCreated()` will `attachSurface` + restore `vo` to the LIVE instance (no loadfile,
    since no pending path). This mirrors the existing `isAlreadyPlayingCurrent` branch
    (lines 472-497) — reuse `enableVideoAfterBackground()` + `isReady = true`.
  - Register `MPVLib.addObserver(playerObserver)` (the Activity still needs its observer).
  - `HeadlessPlaybackController.detachForHandoff()` → Activity now owns teardown.
- Else (normal launch): existing path unchanged (`setupMPV()` etc.).

### 4.3 Reverse handoff / teardown (who destroys MPV)
- **User collapses back to mini player** (existing back-press path lines 595-608:
  `startBackgroundPlayback()` + `disableVideoForBackground()` + go to MainActivity): keep as
  is. MPV stays alive; service keeps running. Decide whether ownership returns to the
  controller or the Activity simply stays in the back stack as today. Simplest: after the
  first expand, let PlayerActivity retain ownership exactly like current background playback
  (Activity lives in back stack, `isManualBackgroundPlayback` guards destroy). The headless
  controller only owns MPV during the pre-expand window.
- **Dismiss / stop / EOF while headless (no Activity)**: `MediaPlaybackService.onDestroy`/
  `onTaskRemoved` already send `stop`/`quit`. The controller's `stop()` must call the full
  safe teardown (pause → quit → vo=null → detachSurface → `MPVLib.destroy()` under
  `MPVLifecycleLock`) and `miniPlayerStateManager.clearState()`. Ensure only the current owner
  runs destroy.

---

## 5. REUSE the config-sync logic (don't duplicate ~hundreds of lines)

`PlayerActivity.setupMPV()` + `syncFromUserMpvDirectory()` + helpers
(`syncConfigFiles`, `syncScripts`, `syncScriptOpts`, `syncShaders`, `syncFonts`,
`findFileCaseInsensitive`, `copyMPVConfigFromPreferences`, hdr-toys config) are private to the
Activity but only depend on a `Context` + preference stores + `Utils.copyAssets`.

**Refactor:** extract them into a shared object/class, e.g.
`app/src/main/kotlin/xyz/mpv/rex/ui/player/MpvConfigSync.kt`:
```
object MpvConfigSync : KoinComponent {
  // inject AdvancedPreferences, DecoderPreferences
  fun prepare(context: Context) {
    Utils.copyAssets(context)
    syncFromUserMpvDirectory(context)   // + all helpers moved here, Context-parameterized
    configureHdrToys(context)
  }
}
```
Then:
- `PlayerActivity.setupMPV()` calls `MpvConfigSync.prepare(this)` before `player.initialize(...)`.
- `HeadlessPlaybackController` calls `MpvConfigSync.prepare(appContext)` before its
  `mpvView.initialize(...)`.
This is a pure move — keep it a **separate refactor commit** and run
`compileDebugKotlin` after.

---

## 6. `MediaUtils` wiring change

In `playFile()` and `playPlaylist()`, replace the current `start_in_mini_player`
intent/anim branch with:
```
val playDirectlyInMini = startInMiniPlayer ?: playerPreferences.playInMiniPlayerDirectly.get()
if (playDirectlyInMini) {
    headlessController.startHeadless(source=…, context, launchSource, playlistId)  // NO startActivity
    return
}
// else: existing startActivity(PlayerActivity) with fade_in anim (revert to original)
```
Inject `HeadlessPlaybackController` via Koin (`by inject()`). Pass the already-resolved
metadata (width/height/rotation/savedOrientation, resume position) that `MediaUtils` computes
today so the controller doesn't re-query.

---

## 7. First-play correctness: playlist / subtitles / resume / managers

Managers are the Ops/Manager pattern (see `knowledge/index.md`); confirm each is an
injectable Koin singleton vs. Activity-bound before relying on it headless:
- **PlaylistManager** — accessed via `viewModel.playlistManager` in the Activity. Check whether
  `PlayerViewModel`/managers are Koin singletons (likely) so the controller can set the
  playlist the same way (`setPlaylist(items,index,id,titles)`), then loadfile the start item.
  Needed for `hasNext/hasPrevious` + `playNext/playPrevious` in the mini bar.
- **Resume position**: `MediaUtils` already reads `PlaybackStateRepository.getVideoDataByTitle`
  for saved orientation; resume playhead is applied in PlayerActivity's load path / HistoryManager.
  For headless, apply `start=<seconds>` loadfile option or seek after `MPV_EVENT_FILE_LOADED`.
- **Subtitle autoload**: gated by `intent.putExtra("internal_launch", true)` today and handled
  by `SubtitleManager`. Reuse `SubtitleManager` if it's a singleton; otherwise defer subtitle
  autoload until expand (acceptable for audio-first mini playback).
- **HistoryManager**: logs recently-played; make sure headless start still records history
  (RecentlyPlayedOps) — call the same hook `MediaUtils`/Activity uses.

If any manager is Activity-scoped and hard to reuse headless, the minimum viable first cut is:
loadfile + service + mini state, and defer full playlist/subtitle richness until the user
expands (PlayerActivity then wires managers as today via the attach path).

---

## 8. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Double `MPVLib.create()` → native crash | Single-owner flag; controller checks `isSessionActive`/`mpvInitialized` before create; PlayerActivity skips `setupMPV()` on attach path. |
| Teardown race (two owners call destroy) | Route ALL destroys through one owner + `MPVLifecycleLock` (already exists: `isTearingDown`, `awaitTeardown`, `onTeardownStart/Complete`). |
| Surface attach race on expand | Reuse existing `isAlreadyPlayingCurrent` pattern; `attachToExistingSession()` only adds callback — `surfaceCreated` does the attach; guard with `player.isExiting`. |
| `observeProperty` skipped on fresh handle | Already handled by `observedProps` map in `MPVView` (lines 234-278). Ensure `attachToExistingSession()` calls `observeProperties()`; service re-observes its 4 props in `onCreate`. |
| Foreground-service start restrictions | Start service from a foreground context (user tap) and post the notification immediately (service already does `startForeground` in `onStartCommand`). |
| Config sync on main thread | `MpvConfigSync.prepare` does IO — run on `Dispatchers.IO` before init, matching Shorts/Activity behavior; the controller's `startHeadless` should be `suspend` or launch a coroutine. |

---

## 9. Files to touch (summary)

| File | Change |
|---|---|
| `app/.../ui/player/HeadlessPlaybackController.kt` | **NEW** — headless MPV owner (§4.1). |
| `app/.../ui/player/MpvConfigSync.kt` | **NEW** — extracted asset/config sync (§5). |
| `app/.../ui/player/PlayerActivity.kt` | Call `MpvConfigSync.prepare`; add `attach_existing_session` branch skipping `setupMPV`; revert hack branches. |
| `app/.../ui/player/MPVView.kt` | Add `attachToExistingSession()` (addCallback + observeProperties, no create). |
| `app/.../utils/media/MediaUtils.kt` | Route direct-mini to controller; revert intent hack. |
| `app/.../ui/browser/miniplayer/MiniPlayerStateManager.kt` | `openPlayer` passes `attach_existing_session`; revert NEW_TASK/reopen hack. |
| `app/.../ui/player/MediaPlaybackService.kt` | Ensure it can be started by controller (verify extras). |
| Koin module (di) | Register `HeadlessPlaybackController` (+ `MpvConfigSync` if class). |
| `AndroidManifest.xml`, `res/values/themes.xml` | Revert `taskAffinity`, Translucent/NoAnimation styles. |
| `PlayerPreferences.kt`, `PlayerPreferencesScreen.kt` | **KEEP** `playInMiniPlayerDirectly` + toggle. |

Suggested commit split: (1) revert hack, (2) extract `MpvConfigSync` refactor,
(3) headless controller + wiring.

---

## 10. Verification / manual test checklist

Compile fast after each step: `./gradlew compileDebugKotlin -I local-env.gradle.kts`.
Install to device: `./gradlew installDebug -I local-env.gradle.kts` (do automatically).

1. Pref OFF → tap video → full-screen `PlayerActivity` opens as before (regression check).
2. Pref ON → tap video → **NO flash/slide**; mini player bar appears at bottom of
   MainActivity with thumbnail + title; audio plays; notification shows with controls.
3. Mini bar: play/pause, seek, next/prev (playlist), shuffle/repeat all work.
4. Tap/expand mini bar → `PlayerActivity` opens and **video is already playing** (surface
   attaches to live MPV, no reload, no black flash, correct position).
5. Back from full-screen (with automaticBackgroundPlayback) → returns to mini bar, audio continues.
6. Dismiss mini player / stop → MPV torn down cleanly, notification gone, no crash.
7. Swipe app from recents → `onTaskRemoved` quits MPV, process dies cleanly.
8. Rapid tap several videos in a row → no native double-init crash (single-owner flag holds).
9. Start headless, expand, collapse, expand again → no leak / no second `MPVLib.create`.
10. Rotate / PiP from the expanded player → unaffected.

---

## 11a. RESOLVED scoping (confirmed)

- Koin singletons (reusable headless): `MiniPlayerStateManager`, `PlaybackManager`
  (`di/DomainModule.kt` lines 25-26), plus `Anime4KManager`, `HdrToysManager`.
- **Activity-scoped (NOT reusable headless)**: `PlaylistManager`, `SubtitleManager`,
  `HistoryManager`, `CustomButtonManager` — all constructed inside `PlayerViewModel`
  (`PlayerViewModel.kt` lines 108-144), which is created per-Activity via
  `PlayerViewModelProviderFactory(host)`.
- **Decision**: headless controller uses **mpv's NATIVE playlist** — append all playlist
  URIs to mpv (`loadfile <first>`, then `loadfile <rest> append`), and drive next/prev via
  `MPVLib.command("playlist-next"/"playlist-prev")`. `MiniPlayerStateManager.playNext/
  playPrevious` already fall back to these when `onNextHandler/onPreviousHandler` are null
  (which they are headless). Set `hasNext/hasPrevious` from `playlist-pos`/`playlist-count`.
- **Resume**: read `PlaybackStateRepository` (singleton) for saved playhead; pass loadfile
  option `start=<seconds>` (or seek on `MPV_EVENT_FILE_LOADED`).
- **Subtitle autoload**: deferred for headless v1 (audio-first). Full autoload happens when
  the user expands and `PlayerActivity` attaches (its normal subtitle path). Revisit if
  subtitles-in-mini is required.

## 11. Open items to confirm in the new session (quick greps)

- Koin module file that registers `MiniPlayerStateManager` (to register the new controller):
  `grep -rn "MiniPlayerStateManager" app/src/main/kotlin --include=*.kt | grep -i "single\|module\|factory"`
- Are `PlayerViewModel` / `PlaylistManager` / `SubtitleManager` / `HistoryManager` Koin
  singletons or Activity-scoped? `grep -rn "class PlayerViewModel\|PlaylistManager\|SubtitleManager\|HistoryManager" app/src/main/kotlin`
- Exact resume-position apply site in PlayerActivity load path (search `getVideoDataByTitle`,
  `time-pos`, `HistoryManager`).
- `R.layout.shorts_dummy_layout` reuse vs. a new dummy layout for the controller.

---

## 12. IMPLEMENTATION STATUS — what has been done (handoff to Gemini)

All code below **compiles cleanly** (`./gradlew compileDebugKotlin -I local-env.gradle.kts`
→ BUILD SUCCESSFUL). It has **NOT** been installed or tested on a device yet.

### 12.1 Done — files created / edited

1. **`ui/player/MpvConfigSync.kt`** (NEW)
   - Extracted the shared config/asset prep out of `PlayerActivity.setupMPV()` so both the
     activity and the headless controller run identical setup (DRY).
   - `object MpvConfigSync : KoinComponent` with public `fun prepare(context)` and
     `fun findSubdirCaseInsensitive(parent, name)`.
   - Import gotcha fixed: uses `com.github.k1rakishou.fsaf.FileManager` (NOT
     `xyz.mpv.rex.utils.storage.FileManager`).

2. **`ui/player/HeadlessPlaybackController.kt`** (NEW) — the core of the feature.
   - `class HeadlessPlaybackController(appContext) : KoinComponent`, registered as a Koin
     `single` in `di/DomainModule.kt`. Injects `MiniPlayerStateManager`.
   - Holds an **off-window** `MPVView` (built from `R.layout.shorts_dummy_layout` +
     `Xml.asAttributeSet`), so MPV runs with NO activity window.
   - `startHeadless(uris, startIndex, title, artist, resumePositionSec=0)`:
     `MpvConfigSync.prepare()` → create off-window view → `view.initialize(filesDir, cacheDir)`
     → `MPVLib.setPropertyString("vo","null")` (audio-first, no surface needed) →
     `loadPlaylist(...)` → `startService(...)`.
   - `loadPlaylist`: appends all URIs to mpv's NATIVE playlist (`loadfile <first> replace`,
     rest `append`), sets `playlist-pos=startIndex`, `force-media-title`, then `scheduleResume`.
   - `scheduleResume(sec)`: if sec>3, one-shot `MPVLib.EventObserver` that on
     `MPV_EVENT_FILE_LOADED` seeks `time-pos` then removes itself.
   - `detachForHandoff()`: removes observers + surface callback, nulls the view,
     `isSessionActive=false`, but **KEEPS global MPV alive** (no destroy) — this is what lets
     PlayerActivity take over the running session.
   - `stop()`: removes observer, `mpvView.destroy()`, clears state, stops the service.
   - Public read-only: `isSessionActive`, `activeUris`, `activeIndex`, `activeTitle`.

3. **`ui/player/MPVView.kt`** (EDITED)
   - Added `fun attachToExistingSession()`: sets `vo` back to gpu/gpu-next, re-adds the
     surface callback, re-runs `observeProperties()` — WITHOUT calling `initialize()`
     (avoids a second `MPVLib.create`, which would crash the native singleton).

4. **`ui/player/PlayerActivity.kt`** (EDITED)
   - `setupMPV()` now delegates to `MpvConfigSync.prepare(this)`; the old inline
     `copyAssets`/`syncFromUserMpvDirectory`/hdr-toys duplicate methods were deleted.
   - Injected `headlessPlaybackController`.
   - onCreate now branches: if intent extra `attach_existing_session=true` AND
     `headlessPlaybackController.isSessionActive`, call `setupMPVForHandoff()` instead of
     `setupMPV()`.
   - `setupMPVForHandoff()`: waits out any teardown, `detachForHandoff()`, then
     `player.attachToExistingSession()`, sets `mpvInitialized=true`, restores osd-level,
     re-adds `playerObserver`. Handoff intent carries no ACTION_VIEW data, so the existing
     `isAlreadyPlayingCurrent` path runs (no reload) and the playlist-from-intent code
     repopulates `PlaylistManager`.

5. **`ui/browser/miniplayer/MiniPlayerStateManager.kt`** (EDITED)
   - Injects `headlessPlaybackController`. `openPlayer(context)`, when a headless session is
     active, adds intent extras `attach_existing_session=true`, `playlist`(activeUris),
     `playlist_index`(activeIndex), `title`(activeTitle) so the expand handoff works.

6. **`utils/media/MediaUtils.kt`** (EDITED) — routing.
   - Injects `playerPreferences` + `headlessPlaybackController`.
   - `playFile()` and `playPlaylist()`: when `playInMiniPlayerDirectly` is ON, call the new
     private `startHeadless(...)` and `return` BEFORE `startActivity` — so no activity ever
     launches. `startHeadless` resolves resume position via
     `playbackStateRepository.getVideoDataByTitle(title)?.lastPosition`.

7. **Preference + settings UI**
   - `preferences/PlayerPreferences.kt`: `playInMiniPlayerDirectly` boolean (default false).
   - `res/values/strings.xml`: `pref_player_play_in_mini_player` (+ on/off summaries).
   - `ui/preferences/PlayerPreferencesScreen.kt`: `SwitchPreference` toggle.

8. **`di/DomainModule.kt`** (EDITED): `single { HeadlessPlaybackController(androidContext()) }`.

### 12.2 NOT done — remaining work for Gemini

- **Device install + end-to-end test** (Task #7). Never ran `installDebug`. Nothing below is
  verified on hardware:
  - Actual flicker-free behavior when tapping a media item with the toggle ON.
  - **Video re-enable on handoff**: confirm that after `attachToExistingSession()` the surface
    gets `vo=gpu` and `surfaceCreated()` actually shows video (check `voInUse` defaults and the
    `surfaceCreated()` else-branch — the file was loaded while `vo=null`).
  - Resume seek accuracy and playlist-pos alignment after handoff.
- **Wire `HeadlessPlaybackController.stop()`** to a mini-player dismiss/close action. Right now
  nothing calls `stop()`, so a headless session is never torn down from the UI.
- **Subtitle autoload in headless mode**: deferred (audio-first). Autoload currently only
  happens once PlayerActivity attaches on expand. Revisit if subs-in-mini are required.
- **Ownership edge cases**: double-check exactly one of {HeadlessPlaybackController,
  PlayerActivity} ever calls `MPVLib.destroy()` across expand→collapse→re-expand cycles.
