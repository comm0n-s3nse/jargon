# Jargon

A voice typing keyboard for Android that recognizes your speech **on your phone** and can translate
it. Nothing you say leaves the device; the only thing the app uses the internet for is downloading a
speech model, after you confirm. Three engines (whisper.cpp, parakeet.cpp, transcribe.cpp), models
from Whisper Tiny to Canary 1B, spoken punctuation and emoji, and a strip of three keys that do a
lot: hold, flick and drag.

> **This is a fork** of [Kaiboard](https://github.com/kaisoapbox/kaiboard) by Kai (kaisoapbox),
> which is where the keyboard, its interface and the idea come from. See [FORK.md](FORK.md) for
> what changed and the credits, and [docs/RELEASING.md](docs/RELEASING.md) for how it ships.
> Published by [comm0n-s3nse](https://github.com/comm0n-s3nse).

**Get it:** GitHub Releases (APK with the small English model inside), and F-Droid once the
recipe is accepted (that copy downloads a model on first open).

**Build it:** run `scripts/fetch-model.sh` to bundle the built-in model (skip it for the F-Droid
shape), then the steps in FORK.md.


[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/kaisoapbox)

## _The best Android keyboard for offline speech recognition_

The power of OpenAI's Whisper model at your fingertips, anywhere, anytime. A native Android keyboard
using [whisper.cpp](https://github.com/ggerganov/whisper.cpp/) for speech transcription. No servers,
full privacy.

This project is developed and maintained with ❤️ by [Kai](https://kaisoapbox.com/).

## How to Run

Add the model you want to use under `app/src/main/assets/models/` (e.g. `ggml-tiny.en-q8_0.bin`, which `scripts/fetch-model.sh` downloads). Then,
just open the project in Android Studio, and it should work!

## Build

Select the "release" active build variant, and use Android Studio to run and deploy to your device.

You can use Android Studio to generate a signed app bundle for Google Play deployment (you will
need/create your own signing key) under `Build -> Generate Signed App Bundle or APK`.

## Roadmap

- [ x ] Pause playing media
- [ x ] Realtime transcription (partial success)
- [ ] Model and language selection
- [ ] Local logging to assist debugging
- [ ] Transcribe any provided file
- [ ] Setting to save audio recordings as files

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Acknowledgements

Many thanks to [Michael McCulloch](https://github.com/MichaelMcCulloch/WhisperVoiceKeyboard) for his
first version of the keyboard, and to all the folks working
on [whisper.cpp](https://github.com/ggerganov/whisper.cpp/), without whom this app would not exist.
