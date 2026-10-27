# Fork notes

This is a fork of [kaisoapbox/kaiboard](https://github.com/kaisoapbox/kaiboard) (BSD 3-Clause,
copyright 2024 kaisoapbox), started from upstream commit `0a75d47`. The fork's changes are listed
here against that commit. Whisper stays the speech engine and `ggml-tiny.en` stays the default
model; nothing here adds a second engine.

The app is `io.github.comm0n_s3nse.jargon`; the debug build installs as `io.github.comm0n_s3nse.jargon.debug` and is labeled "Jargon (debug)", so
it can sit next to the Play or F-Droid app.

## Bugs fixed in upstream code

- **Native build broken against its own pinned whisper.cpp.** `lib/src/main/jni/whisper/CMakeLists.txt`
  looked for whisper.cpp one directory too high, and listed ggml sources the pinned commit no
  longer has (`ggml-backend.c` is now `.cpp`; `ggml-cpu.c` and `ggml-aarch64.c` were added).
- **Debug builds ran whisper unoptimized.** `-O3` was only applied to non-debug builds. Whisper
  at `-O0` took 5-10 s for a sentence that takes under a second optimized.
- **The recorder outlived its screen.** `MainScreenViewModel.onCleared()` and the keyboard
  service's `onDestroy()` never stopped the `AudioRecorder` thread, so a swiped-away test screen
  kept recording at full CPU indefinitely.
- **A deadlock closing the settings screen.** `MainScreenViewModel.onCleared()` ran `runBlocking` on
  the main thread and, inside it, called `stopPlayback()`, which needs the main thread to run. The
  main thread waited for itself, the screen froze, and Android reported "not responding" after
  five seconds. It happened whenever the settings screen closed while the keyboard kept the app
  alive. Cleanup there, and in the keyboard service's `onDestroy()`, no longer waits.
- **Quadratic recording buffer.** `Recorder` kept samples in a boxed `MutableList<Short>` and
  copied the whole list on every read. It now uses a growable `ShortArray` and hands the
  transcription callback a copy at most four times a second.

## Behavior changes

- **Text appears only after the checkmark.** The live preview (composing text on partial audio)
  is gone, along with the background transcription passes. It caused duplicated phrases and a
  wrong cursor in some editors, and made the final pass wait behind a preview pass.
- **Dictation is one commit,** with its leading and trailing space inside the committed string.
- **Spacing follows the cursor:** a leading space only after a letter, digit or `. , ; : ! ? ) ] }`;
  no trailing space before whitespace or closing punctuation; none at all in email fields.
- **Sentence-aware capitalization:** capital only at the start of a field, after a newline, or
  after `. ! ?`; otherwise the first letter is lowercased ("I", "I'm" and acronyms keep theirs).
  Skipped in email, URI and password fields, and in Casual mode.
- **Highlighted text is replaced** with no added spaces, and the model's closing period is dropped
  when the highlight does not end a sentence.
- **Backspace:** deletes by code point or the selection. Hold: characters, then whole words.
  Enter runs the field's editor action (send, search, go, done, next) or inserts a newline.
- **Undo by dragging backspace left.** A small stack (3) of dictations and runs of deletions, each
  valid only while the text around the cursor is unchanged. Nothing is recorded in password or
  email fields.
- **Press-to-talk on the mic,** gated at 240 ms: a tap toggles as before; holding records until
  release; dragging up about 1 cm while holding discards the recording. A ring grows from the
  thumb to show the gate, then a thick hollow outline shows hold mode.
- **Silence trimming:** leading and trailing silence is removed before whisper runs, and a
  recording with no speech in it is not transcribed at all.
- **Keyboard-switch key removed** from the strip (the system's own switch key remains).
- **Sizes in millimeters:** button height and the dead space under the row follow the phone's
  physical DPI, so Android's "display size" setting cannot shrink the keyboard into the nav bar.

- **Thread auto-tune:** the keyboard times a few thread counts on first use and keeps the fastest
  (8 threads measured up to 3x slower than 6 on one phone). The mic button shows progress, settings
  shows the result, and "Auto" re-tunes. A value you set by hand always wins.
- **Choose the speech model and language.** Settings lists the bundled model plus any whisper.cpp
  `.bin` the user adds from the file picker (checked by its header, copied into the app, kept
  out of backups). One model is in memory at a time; a model that will not load falls back to
  the bundled one. The language can be set or auto-detected, and English-only models always get
  English. Behind this is a small `SpeechEngine` interface so other engines can be added.
- **A second engine: parakeet.cpp.** GGUF models such as Fluid Speech Mini run through a
  `:parakeet` module (JNI over parakeet.cpp's C API) behind the same `SpeechEngine` interface.
  The model picker accepts either a whisper `.bin` or a parakeet `.gguf`, and a phone without the
  needed CPU instructions is told so instead of crashing. Both engines load in one process.
- **Tuner uses only the fast cores.** The efficiency cores of a big.LITTLE chip made 6 to 8
  threads run 5 to 50 times slower than 4 on one phone, so the tuner counts only cores near the
  fastest one's speed, treats anything within 10% of the best as a tie (fewest threads wins),
  and, if a tune is ever interrupted, uses a safe default instead of retrying.
  Each model keeps its own tuned value, so switching models does not tune again.
- **Recommended models you can download.** Settings lists four, each with a plain name, its memory
  use and what it is good for: Whisper Tiny (built in), Fluid Speech Mini, Whisper Small, and
  Canary 1B (shown as coming soon: it needs an engine the app does not have yet). Tapping Download
  shows a screen with the size, memory use, source and the credit its license asks for. The
  download runs in Android's download manager and is checked against a SHA-256 pinned in the app
  (`models/Catalog.kt`) before it is used. This is the app's only use of the network, adds the
  `INTERNET` permission, and happens only after that tap.
- **A third engine: transcribe.cpp** (MIT, pinned at v0.4.0), in a `:transcribe` module. It runs
  the model families that neither whisper.cpp nor parakeet.cpp can, starting with **Canary**, which
  also translates speech into other languages. A "Translate to" setting appears when the active
  model can. **Which engine runs a model** is decided from the file: a `.bin` is whisper.cpp's; a
  `.gguf` with a `parakeet.arch` key in its header is parakeet.cpp's (Fluid); any other `.gguf` goes
  to transcribe.cpp. (`models/GgufInfo.kt` reads only the header, a few kilobytes, so classifying a
  735 MB file is instant. transcribe.cpp's own Parakeet files also say `general.architecture =
  parakeet`, which is why that field alone is not enough.) All three engines load side by side in
  one process, each with its own statically linked ggml.
- **A smaller built-in model.** Whisper tiny.en is bundled as 8-bit `q8_0`: 44 MB instead of 78,
  about a fifth faster and 30 MB lighter in memory, with the same output on test speech.
- **Long dictation is transcribed in pieces while you talk** (1.1). The recorder feeds a
  `Chunker` that cuts at a 600 ms pause once a piece holds three seconds, or at the quietest
  moment near 20 s; each piece is transcribed in the background and only the tail is left when
  you stop. The texts are joined and committed once, through the same pipeline. Measured with a
  real 70 s dictation on a OnePlus 15: Whisper Tiny's wait after the stop fell from about 4 s to
  0.3 s; Canary 180M Flash finished a 12.6 s tail in 0.5 s.
- **Spoken punctuation and emoji** (1.1): "question mark", "period", "comma", "hyphen", "new
  line", "dot dot dot" at the edges (hyphen, ellipsis and new line also mid-sentence), and some
  thirty emoji phrases. Both are Behavior switches with the lists a tap away. Chat shorthand
  (lol, imo, tbh…) stays lowercase.
- **Gesture tutorial** (1.1): a black page in Essential Setup that walks the seven gestures by
  having you do them; the keys report each one. Always shown in a debug build.
- **Update check** (1.1): on demand in About, or whenever settings open if switched on in
  Behavior; it asks GitHub for the newest release and links to its page. The keyboard process never uses the network.
- **Speech model and Translation model** sections (1.1), five recommended models: Whisper Tiny,
  Fluid Speech Mini, Canary 180M Flash (recommended); Whisper Small, Canary 1B. The language rows
  offer every language a model's file lists, named by the system (Canary 1B's 25, Slovenian
  included), not just the short list. Moonshine Tiny was
  tried and dropped: once dictation is transcribed in pieces its speed no longer bought anything, and
  its text was the roughest of the three; transcribe.cpp still runs it through "Add a model file".
- **Vocabulary** (1.2): names and terms the models should know, one per line in Behavior; passed to
  Whisper as its prompt and to the transcribe.cpp models that take a word list (Canary does not).
- **"I speak: Auto" for Canary** (1.2): a 27 MB language detector (VoxLingua107 ECAPA, through
  transcribe.cpp) is offered when Auto is chosen for a translating model and decides once per
  recording, among the languages the model knows. Only a sure read of at least 1.5 s of speech
  locks the recording; a doubtful one is English for that piece and the next piece gets another look.
- **Spoken phrases per language** (1.2): Portuguese and Slovenian have their own emoji and
  punctuation phrases, active only while that language is the one spoken, so one language's sounds
  never trigger another's emoji; the English phrases work everywhere. Drafted from dictionaries,
  corrections welcome. Emoji always stand apart from the word before them.
- **Undo and redo** (1.2): the stack holds eight edits; a drag left takes out the last dictation
  even after backspaces (what you typed over it is put back), a drag right redoes it.
- **Numbers as digits** (1.2): "forty ninety" → 4090, "nineteen eighty four" → 1984, "three point
  one four" → 3.14; a number word on its own below ten stays a word. A Behavior switch.
- **Double-tap on the mic** (1.2) says, in the language you speak, that the offline agent is coming.
- **About and licenses screen** (Settings): credits for Kaiboard, whisper.cpp, Whisper, parakeet.cpp,
  ggml and the Android libraries, each with its license text, as BSD-3 and MIT require.

See `KAIBOARD.md` in the parent project for the plan these came from.

## Building

Submodules: `git submodule update --init whisper.cpp parakeet.cpp transcribe.cpp`, then
`git -C parakeet.cpp submodule update --init --depth 1 third_party/ggml`. Then run
`scripts/fetch-model.sh`, which downloads the built-in model (`ggml-tiny.en-q8_0.bin`, 44 MB, not in git) and
checks its SHA-256. Needs JDK 17, the Android SDK
(platform 34), NDK 25.2.9519653 and CMake 3.22.1.

## Credits

Used today:

- **Kaiboard** by [Kai / kaisoapbox](https://github.com/kaisoapbox/kaiboard), BSD 3-Clause. This
  project is a fork of it and exists because of it: the keyboard, the whisper.cpp integration,
  the interface and the idea are Kai's.
- **whisper.cpp** by Georgi Gerganov and contributors, MIT, and **Whisper** by OpenAI, MIT.

Under evaluation, not shipped (listed so credit is not forgotten if they are):

- **parakeet.cpp** by its authors, MIT.
- **Fluid Speech Mini** by FluidVoice Labs, CC BY 4.0, built on NVIDIA Parakeet TDT 0.6B v3 (CC BY 4.0).
- **Canary 180M Flash** and **Canary 1B v2** by NVIDIA, CC BY 4.0, converted by handy-computer.
- **GitHub and X marks** from Simple Icons, CC0.
  CC BY 4.0 requires crediting it as "Fluid Speech Mini by FluidVoice".
