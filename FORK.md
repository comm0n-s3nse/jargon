# Fork notes

This is a fork of [kaisoapbox/kaiboard](https://github.com/kaisoapbox/kaiboard) (BSD 3-Clause,
copyright 2024 kaisoapbox), started from upstream commit `0a75d47`. The fork's changes are listed
here against that commit. Whisper stays the speech engine and `ggml-tiny.en` stays the default
model; nothing here adds a second engine.

The debug build installs as `kaizo.co.WhisperVoiceKeyboard.fork` and is labeled "Kaiboard Fork", so
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

See `KAIBOARD.md` in the parent project for the plan these came from.
