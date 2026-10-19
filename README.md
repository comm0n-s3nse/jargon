# Jargon

A voice typing keyboard for Android that recognizes your speech **on your phone** and can translate
it. Nothing you say leaves the device; the only thing the app uses the internet for is downloading a
speech model, after you confirm. Three engines (whisper.cpp, parakeet.cpp, transcribe.cpp), models
from Whisper Tiny to Canary 1B, spoken punctuation and emoji, and a strip of three keys that do a
lot: hold, flick and drag.

Jargon is a fork of [Kaiboard](https://github.com/kaisoapbox/kaiboard) by Kai (kaisoapbox), which
is where the keyboard, its interface and the idea come from. [FORK.md](FORK.md) lists what changed
and the credits. Published by [comm0n-s3nse](https://github.com/comm0n-s3nse).

## Get it

- **GitHub Releases:** `jargon-<version>.apk` has the small English model inside and works the
  moment it is installed; `jargon-<version>-nomodel.apk` is the same app without a model, and
  offers the models to download on first open. Both are arm64, signed with the same key.
- **F-Droid:** submitted; the listing appears when the recipe is accepted. That copy is the
  no-model shape.

After installing: Settings → System → Keyboards → enable Jargon, then switch to it from your
keyboard's switcher (on Jargon, flick the enter key up to switch back).

## Use it

| Key | Tap | Hold | Drag |
|---|---|---|---|
| Microphone | start, tap again to finish | talk, let go to finish | up while holding: cancel · left: pick the language (Canary) |
| Backspace | one character | repeats, then whole words | left: undo |
| Enter | send / search / new line | settings | flick up: previous keyboard |

Say "question mark", "period" or "comma" at the start or end of what you say and the mark is
typed. Say "smiley face", "facepalm" or "thumbs up" and the emoji is. The lists are in Settings.

## Build it

```sh
git clone --recurse-submodules https://github.com/comm0n-s3nse/jargon.git
cd jargon
scripts/fetch-model.sh        # bundles Whisper Tiny; skip it for the no-model shape
./gradlew :app:assembleDebug  # or open the project in Android Studio
```

Needs JDK 17, the Android SDK (API 34), NDK r25c and CMake. [FORK.md](FORK.md) has the details
and [docs/RELEASING.md](docs/RELEASING.md) describes how a release is built and signed.

## Credits

Kaiboard by [Kai](https://kaisoapbox.com/) (kaisoapbox), BSD 3-Clause; Kai in turn thanks
[Michael McCulloch](https://github.com/MichaelMcCulloch/WhisperVoiceKeyboard) for the first version
of the keyboard. Speech recognition by [whisper.cpp](https://github.com/ggerganov/whisper.cpp/),
[parakeet.cpp](https://github.com/mudler/parakeet.cpp) and
[transcribe.cpp](https://github.com/handy-computer/transcribe.cpp); models by OpenAI, FluidVoice and
NVIDIA. The app's About screen lists every license. If Kaiboard has been useful to you, Kai takes
support on [Ko-fi](https://ko-fi.com/kaisoapbox).

<details>
<summary>Kai's original README (Kaiboard, 2023)</summary>

### _The best Android keyboard for offline speech recognition_

The power of OpenAI's Whisper model at your fingertips, anywhere, anytime. A native Android keyboard
using [whisper.cpp](https://github.com/ggerganov/whisper.cpp/) for speech transcription. No servers,
full privacy.

This project is developed and maintained with ❤️ by [Kai](https://kaisoapbox.com/).

#### Roadmap

- [x] Pause playing media
- [x] Realtime transcription (partial success)
- [ ] Model and language selection
- [ ] Local logging to assist debugging
- [ ] Transcribe any provided file
- [ ] Setting to save audio recordings as files

#### Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

#### Acknowledgements

Many thanks to [Michael McCulloch](https://github.com/MichaelMcCulloch/WhisperVoiceKeyboard) for his
first version of the keyboard, and to all the folks working
on [whisper.cpp](https://github.com/ggerganov/whisper.cpp/), without whom this app would not exist.

</details>
