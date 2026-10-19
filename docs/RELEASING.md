# Releasing

State of play, honestly: the app builds, installs and runs on two phones. What follows is the
checklist for getting it to other people, with what has and has not been checked.

## Before a first release

1. **Choose the name and applicationId** (they are provisional below), then rename in one pass:
   - `applicationId` in `app/build.gradle` (the debug `.fork` suffix can stay) and the labels in
     `app/src/main/res/values/strings.xml` (`app_name`) and `app/src/debug/res/values/strings.xml`
   - `fastlane/metadata/android/en-US/title.txt`
   - `docs/fdroid-recipe-draft.yml` (id, URLs, name) and the README heading
   - A fork must not reuse upstream's `kaizo.co.WhisperVoiceKeyboard`: it would collide with, and
     not be updatable from, Kai's app. The Kotlin package names can stay; only the applicationId matters.
2. **Version.** `versionName` and `versionCode` in `app/build.gradle`, and a matching
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. The draft assumes 1.1.0 / 5.
3. **A signing key** for APKs you publish yourself. Generate it once and keep it safe; losing it
   means users cannot update. Never use the debug key.
   `keytool -genkeypair -v -keystore release.jks -alias release -keyalg RSA -keysize 4096 -validity 10000`
4. Screenshots and a 512x512 icon for the store listing (`fastlane/metadata/android/en-US/images/`).
   None exist yet.

## Where to publish, in order of effort

- **GitHub Releases** (signed APK attached): immediate, and Obtainium can follow it.
- **IzzyOnDroid**: a third-party F-Droid repository that takes APKs from GitHub releases, with
  light review. I could not confirm that Kaiboard itself is on it or on F-Droid proper; its README
  links only Google Play.
- **F-Droid proper**: submit `metadata/<applicationId>.yml` as a merge request to
  gitlab.com/fdroid/fdroiddata. They build from source and sign with their own key. Slowest, with
  real review, and F-Droid's rules are theirs to apply, not ours.

## What has and has not been checked for F-Droid

- The app now has the `INTERNET` permission, used only to download a speech model after the person
  taps Download on a screen naming the source. F-Droid reviewers may ask about it, and about
  downloading from huggingface.co; the answer is that the models are optional, checksummed, and
  never needed to run the built-in one.
- Checked: the recipe lints clean with `fdroid lint` (fdroidserver 2.4.5) against F-Droid's real
  category list. The store text is within its length limits. The app asks only for the microphone
  permission and uses no Google services.
- **Not checked: a build on F-Droid's build server.** Open questions the reviewers will settle:
  - the built-in model is downloaded and checksum-verified by `scripts/fetch-model.sh` in
    `prebuild`, because it is not in git; they may want it handled differently
  - whether `cmake` and NDK `r25c` in the recipe are what their server provides
  - `submodules: true` also fetches parakeet.cpp's own submodules, which are not needed
  - the build compiles whisper.cpp for four ABIs and parakeet.cpp for arm64 (long on a slow machine)

## Linting the recipe

Colima shares only your home folder with Docker, so keep the scratch folder under it. Copy
`docs/fdroid-recipe-draft.yml` to `metadata/<id>.yml`, add `config/categories.yml` from fdroiddata
(plus empty files for the icons it names), then in a `python:3.12-slim` container with `git`:
`pip install fdroidserver && fdroid lint <id>`.
