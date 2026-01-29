# mpvRex

**mpvRex is a fork of [mpvEx](https://github.com/marlboro-advance/mpvEx), which is based on [mpv-android](https://github.com/mpv-android/mpv-android). It aims to combine the powerful features of mpv with an easy to use interface and additional features.**

## Acknowledgments
This project is built upon the hard work of many open-source contributors. Special thanks to the following repositories:

- [mpvEx](https://github.com/marlboro-advance/mpvEx) (Direct Upstream)
- [mpv-android](https://github.com/mpv-android) (Base)
- [mpvKt](https://github.com/abdallahmehiz/mpvKt)
- [Next player](https://github.com/anilbeesetti/nextplayer)
- [Gramophone](https://github.com/FoedusProgramme/Gramophone)

## Extra Features
mpvRex adds the following features on top of the upstream mpvEx:

- **Player Control Always Dark Mode** ([f1eb7c2](https://github.com/sfsakhawat999/mpvRex/commit/f1eb7c2c1b82d6a24c9b14e5cac0d8dc484bed6a)): Option to force player controls to always use dark theme.
- **Subtitle Seeking Gesture** ([bbbb10b](https://github.com/sfsakhawat999/mpvRex/commit/bbbb10bf22e0f393f00064d524324989ee6207a5)): dedicated gesture action to seek to the next/previous subtitle line.
- **Primary Subtitle Indicator** ([bbbb10b](https://github.com/sfsakhawat999/mpvRex/commit/bbbb10bf22e0f393f00064d524324989ee6207a5)): Visual indicator in the track menu showing the active primary subtitle.
- **Bottom Controls Layout** ([aff35d1](https://github.com/sfsakhawat999/mpvRex/commit/aff35d1e5b411faa0eede9a532bc24d23eed576c)): Option to place playback controls below the seekbar implementation.
- **Accidental Tap Prevention** ([e305c26](https://github.com/sfsakhawat999/mpvRex/commit/e305c26b2a332805151633ac179b709b5a50c6ce)): Preference to ignore single taps on the seekbar to prevent accidental seeks.
- **Relative Seeking** ([e305c26](https://github.com/sfsakhawat999/mpvRex/commit/e305c26b2a332805151633ac179b709b5a50c6ce)): Drag on the seekbar seeks relative to the start position rather than absolute position.
