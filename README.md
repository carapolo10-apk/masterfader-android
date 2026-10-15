# Fader Macros

An Android app that puts one-tap buttons on top of Mackie's **Master Fader** app (DL16S, DL32S, DL32R, DL1608, DL806).
Each button is a macro: a short list of steps such as *recall snapshot INT, then set channels 1–9 to 75%*.

## How it works (and why this way)

Mackie doesn't publish a protocol for the DL mixers: no OSC, no MIDI, no API. Master Fader is the only thing
that talks to them. So instead of talking to the mixer, Fader Macros **operates Master Fader for you** through
Android's accessibility service. It opens Master Fader, taps the Snapshots button, picks the snapshot, presses
Recall, and moves the faders, the same way you would by hand. Master Fader then sends the changes to the mixer
as usual.

That means:

- It works with whatever mixer and firmware Master Fader already works with.
- It depends on Master Fader's screen layout and wording. Every label and fader position is editable in
  **Settings**, and **Capture Master Fader screen** records exactly what Master Fader exposes so it can be tuned.
- Macros run on the same phone or tablet that runs Master Fader.

The macro engine sits behind a `MixerDriver` interface (`core/.../MacroRunner.kt`). If someone captures the
mixer's network traffic later, a direct network driver can replace the accessibility one without changing macros.

## Steps a macro can use

| Step | What it does |
|---|---|
| Recall snapshot | Opens the Snapshots panel, picks the named snapshot, presses Recall and confirms |
| Open show | Opens the Shows panel and loads the named show |
| Set channel volume | Moves the faders of a channel list (`1-9`, `1-4, 7, 12-16`) to a % of fader travel |
| Mute / unmute | Sets each channel's mute button on or off |
| Tap a button by its text | Escape hatch for anything else in Master Fader |
| Tap a screen point / Swipe | Raw touches, positions as fractions of the screen |
| Wait | Pause in milliseconds |

The sample **Chapel scene** button is preloaded: recall `INT`, then channels 1–9 to 75%.

"75%" means 75% of the fader's travel (0% = bottom, 100% = top), not 75% of loudness.

## Setup on the phone/tablet

1. Install the APK (see *Building* below).
2. Open Fader Macros → **Settings** → **Choose app** → pick Master Fader.
3. Tap **Accessibility** and switch on **Fader Macros automation**. Android shows a warning because this
   permission lets the app operate other apps; it only acts while a macro runs.
4. In Master Fader, connect to the mixer and leave it on the **Mixer** view in landscape.
5. Back in Fader Macros, tap **Chapel scene**. Long-press any button or tap ✎ to edit it.
6. In the editor, **Add to home screen** creates a launcher icon that runs the macro in one tap.

### Tuning faders

Faders are set in one of two ways, tried in order:

1. **Directly**, if Master Fader exposes its faders as sliders to accessibility. This is exact.
2. **By gesture**, using the *Fader layout* numbers in Settings. Run **Capture Master Fader screen** and share
   the file: each element is listed with its `centre=(x,y)` as screen fractions, which is what the layout
   fields want (the leftmost strip's X, the gap between strips, the fader's top and bottom Y, and the
   bank next/previous buttons). Pick **Follow the drag** if a fader moves by the distance you drag, or
   **Jump to touch** if it jumps to where you touch.

If a step can't find something, the macro stops and a message names the step and what was missing.

## Building

The project is standard Gradle (Kotlin 2.0, Jetpack Compose, minSdk 26, targetSdk 35).

- **Android Studio**: open this folder, let it sync, Run ▶ on a connected device.
- **Command line**: `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
  (signed with the debug key so it installs directly).
- **GitHub**: push this folder to a repo; `.github/workflows/build.yml` tests the engine and uploads the APK
  as a build artifact.

`./gradlew :core:test` runs the macro engine tests (channel parsing, JSON round trip, fader maths, runner retry/stop).

## Layout

```
core/  Pure Kotlin: macro model, channel parsing, runner, JSON storage format (unit tested)
app/   Android: Compose UI, accessibility service + driver, home-screen shortcuts
```

Macros and settings live in one JSON file; **Settings → Export / Import** copies them between devices.
