# Publishing Jargon: IzzyOnDroid and F-Droid

Where things stand on 2026-10-22: `v1.0` and `v1.1` are GitHub Releases with signed APKs
(`jargon-<ver>.apk` with the model, `jargon-<ver>-nomodel.apk` without). Neither store has been
filed yet. The texts to paste are beside this file: `izzyondroid-request.md`,
`fdroid-merge-request.md`, `io.github.comm0n_s3nse.jargon.yml`.

The short version of "what if I file 1.1 and then ship 1.2": nothing breaks. Both stores follow
the GitHub tags and releases from then on; a release that lands while a request is still in a
queue is simply the one they pick up. The rules that matter are at the end.

## IzzyOnDroid (takes the APK you signed; listed in a day or two)

1. Account on Codeberg. Open a new issue at the IzzyOnDroid repo tracker
   (codeberg.org/IzzyOnDroid/repo, "app addition request") and paste `izzyondroid-request.md`.
2. What they need from us, all already true: a GitHub Releases page with a stable asset name
   pattern (`jargon-*-nomodel.apk`, under their 30 MB cap), `fastlane/metadata/android/en-US/`
   in the repo (title, descriptions, `changelogs/<versionCode>.txt`), the signing certificate
   fingerprint (in the request text), a FOSS license.
3. Izzy answers in the issue, sometimes with a question; the app is in their repo at the next
   index update, usually within a day.
4. After that, every GitHub Release with a `jargon-*-nomodel.apk` asset and a higher
   `versionCode` is picked up by their updater on its own. No further filing, ever.

Users install Izzy's copy of exactly the APK you signed, so it updates in place from the GitHub
APK and vice versa.

## F-Droid (builds from source; weeks)

1. Account on GitLab. Fork `gitlab.com/fdroid/fdroiddata` (web UI, "Fork").
2. In the fork, on a new branch, add the file `metadata/io.github.comm0n_s3nse.jargon.yml`
   with the contents of `io.github.comm0n_s3nse.jargon.yml` from this folder.
3. Open a merge request to `fdroid/fdroiddata`: title and body from `fdroid-merge-request.md`.
   The MR pipeline runs `fdroid lint` and builds the app on their infrastructure; the log is on
   the MR. If it fails, tell me what it says, I fix the recipe or the app, you push the fix to
   the branch.
4. A reviewer picks it up. Two to eight weeks is typical. After merge the app appears on
   f-droid.org within a few days.
5. From then on, F-Droid's bot watches our git tags (`UpdateCheckMode: Tags`,
   `AutoUpdateMode: Version` in the recipe) and adds a build entry for each new `vX.Y` tag by
   itself, copying the previous entry's build steps. Nothing to file for an update.

F-Droid signs its builds with its own key, so its copy is a different signature from the GitHub
and Izzy APK: a phone cannot update from one to the other without an uninstall. That is normal
for F-Droid apps; it changes only if we set up reproducible builds later.

## Filing 1.1, then shipping 1.2: what happens where

- **Izzy, request still open:** they take whatever the latest release is when they process it.
  If 1.2 is out by then, 1.2 is what gets listed. Nothing to do.
- **Izzy, already listed:** the updater sees the 1.2 release and lists it. Nothing to do.
- **F-Droid, MR still open:** the recipe in the MR names 1.1. Either leave it (after merge the bot
  adds 1.2 on its own within a day or two, and the first version people can install may already
  be 1.2), or push one more commit to the MR branch that adds the 1.2 `Builds` entry and bumps
  `CurrentVersion` / `CurrentVersionCode` so the reviewer builds 1.2. I'll give you that file.
- **F-Droid, merged:** the bot adds 1.2 by itself.

## Per release, on our side (the rules)

1. `app/build.gradle`: `versionCode` goes up by one every release, no exceptions (1.1 is 2, so
   1.2 is 3); `versionName` is the human version. Both stores refuse a release whose
   `versionCode` is not higher than the last.
2. `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: the changelog Izzy and
   F-Droid show, named by the code (`3.txt` for 1.2).
3. Tag `vX.Y` on the commit the APKs were built from, push `main` and the tag. **Never move a
   tag once it is pushed**: both stores build or list from where the tag pointed when they read
   it. A mistake gets a new release (1.2.1), not a re-tag.
4. GitHub Release on that tag with both APKs and the release notes; the `-nomodel` asset name
   pattern must stay as it is.
5. Recipe: if the F-Droid MR is not merged yet, add the new `Builds` entry there; afterwards the
   bot does it.

## What the GitHub and F-Droid builds share

One codebase, one commit. The GitHub APKs are built with the model bundled and signed by us;
F-Droid builds the same commit without `scripts/fetch-model.sh`, so its copy opens on the
welcome screen and downloads a model; Izzy lists our `-nomodel` APK, which is the same shape.
