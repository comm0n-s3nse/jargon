# Keyboard motion: what the keys do while you use them

Decided 2026-10-18 from the live mockups (claude.ai/artifact/JGGsJ4ieK3GLJWJUJUh3Va). The
**Material** profile below is the default and ships first. **Charge** is a second profile
for an Appearance screen, still being designed; its notes are at the end so they are not lost.

Everything is drawn inside the key's own `drawWithContent` and `colors`, timed by the frame clock
(`withFrameNanos`), as the ring already is. No new dependencies. Shipped 2026-10-18.

## Material (default)

Resting keys take `primary` / `onPrimary` from the theme. The live and armed colours are fixed
(`#4F7DF3` blue, `#D32F2F` red, white content) so those states look the same on every phone.

### Hold to record (press, charge, arm, hold, release)

| Moment | What happens |
|---|---|
| Press | The ring grows from the thumb to the far corner over the 240 ms gate, 2.5 dp, in the key's ink colour (as today). |
| Arm (ring reaches the edge) | The key fills over 120 ms to the live colour (a fixed `#4F7DF3`, matched to the mockup; the theme's own blues came out dull or near-black), the icon inverts to the on-colour, and one 1.5 dp edge pulse fades over 150 ms, timed with the vibration that already fires here. |
| Hold | A solid filled key, steady. (A breathing outline was tried and shelved for Charge: at 1.5 dp it pinched the key's sides.) |
| Drag up to cancel | Past 10 mm the whole key turns red with white content (a tint over the dark fill read as purple), with the tick that already fires. |
| Release | The fill eases back to the container colour over 160 ms while "Transcribing" shows. |

### Recording in progress (tap mode)

The key stays in the strong primary fill. Left to right inside it: the check icon, five
level bars, the timer. The bars are 3 dp wide, 7 dp apart, 6 to 28 dp tall, rounded, in
the on-colour, each driven by the **real microphone level** (RMS of each buffer the
recorder reads, published as state; each bar gets its own phase so they do not move as one).
Timer and icon unchanged.

How the level is measured (`LevelMeter` in `Recorder.kt`): RMS of each 40 ms chunk in dB,
shown **relative to the background**: the quietest recent chunk is the floor (it drops at
once and rises 25 dB/s), and a bar is how far above the floor the chunk is, with 25 dB as
full height. A fixed dBFS scale did not work: the phone's gain control put a quiet room at
half height. The first chunks after the hardware starts are digital zeros and are ignored.
The five bars are the last 0.4 s, newest on the right, so they are a record, not a
decoration. Verified on the OnePlus in a quiet room (bars at the floor); speech height is
by calculation (voice runs 15 to 30 dB over room noise) and still to be seen on a phone.

### Backspace: drag left to undo

| Moment | What happens |
|---|---|
| Drag | The key stays put. The backspace icon rolls left and fades out while an undo arrow rolls in from the right (28 dp travel, the same roll as enter's gear), and the key tints from primary toward a dark red in step with the drag. The old slide-and-red-patch is gone. |
| Threshold (11 mm) | The key fills solid red (`#D32F2F`, the same red as cancel; Material's `error` is pale in a dark theme), the icon turns white, and the tick fires. **No edge flash** on this key. |
| Release past threshold | Undo, as today. |
| Release before threshold | Rolls back. |

Tap and hold-to-delete are unchanged.

### Enter

Tap: enter. **Flick up** (8 mm): the return arrow rolls up and out while the keyboard icon rolls in
from below, the key tints and ticks past the line, and release switches to the previous keyboard.
**Hold** (300 ms with the finger still): settings. A small gear (10 dp, 55 %) sits in the key's
top-right corner as the reminder, the way a phone key shows its second character; it fades out
during a flick and can be turned off in Appearance. The slide-left gesture is gone.

## Appearance screen (when Charge exists)

A new settings screen, "Appearance", reached from the main page. One setting to start:
the key profile, **Material** (default) or **Charge**. The code should be structured so a
profile is one object the keys ask for their drawing (`KeyProfile`), which keeps the
screen trivial to add. Until there is a second profile the screen is not shown.

## Charge (not shipped; design notes)

Charge is Material plus energy, not a different keyboard:

- **Hold:** Material's fill and arming pulse, **plus** orbiting "electrons" around the
  key as a thicker, glowing gradient border (the mockup's 1 to 2 px tails are too thin:
  think a 4 to 6 dp luminous band with brighter heads travelling along it, ordered speed
  with slight jitter). Arming flash: a white band the full perimeter, decaying ~200 ms.
- **Recording:** the level-driven scope trace is kept and must be accurate to the live mic
  level, but it must **not run under or over the timer or the icon**: confine it to the
  middle band of the key between them.
- **Backspace undo:** as in the mockup: red charge gathering from the right edge, icon
  morph to undo, white edge flash at the threshold, snap-back on release.
- Colours derive from the theme's primary (hue-shifted toward electric) rather than a fixed
  blue, so it does not fight warm dynamic themes.
