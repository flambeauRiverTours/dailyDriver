# Releasing the Android APK

Tag a commit, and CI builds a signed APK and attaches it to a GitHub Release you can install from
the phone.

```bash
git tag android-v0.1.0 && git push origin android-v0.1.0
```

**The `android-` prefix matters.** This is a monorepo with three shippable things in it, and a bare
`v0.1.0` would mean the desktop client could never be versioned independently: tagging a desktop
release would build and publish an Android APK. `desktop-v*` and `backend-v*` are the siblings when
those exist, and their version numbers never have to agree with this one.

The rest of this page is the one-time setup that makes that work.

---

## Why a real signing key, and not the debug one

Android refuses to install an APK over one signed by a different key. The debug keystore is
generated per machine, and a fresh one appears on every CI runner, so debug-signed builds would
each carry a different signature. Updating the launcher would mean uninstalling it first — which
takes its database, and every note that has not synced, with it. Sync does not exist yet (§7), so
that is real data loss on the device you daily-drive.

One stable key, held in repository secrets, makes every release install over the last one.

---

## One-time setup

**You do this, not CI and not an assistant.** The keystore's passwords are the only thing standing
between someone else and the ability to publish an APK that Android will accept as an update to
yours. Nobody needs them but you.

### 1. Create the keystore

```bash
keytool -genkeypair -v -keystore release.jks -alias dailydriver \
  -keyalg RSA -keysize 2048 -validity 10000
```

It asks for a store password, a key password and a name. `-validity 10000` is roughly 27 years:
an expired key cannot sign an update, and re-keying means uninstalling.

**Back this file up somewhere you will still have it in five years.** Losing it means no future
build can ever update an installed copy. This is the one artifact here with no recovery path.

Keep it out of the repository. `*.jks`, `*.keystore` and `*.jks.base64` are gitignored.

### 2. Encode it for GitHub

A secret is a string and a keystore is binary, so it travels base64-encoded.

```bash
base64 -w0 release.jks > release.jks.base64
```

On Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Set-Content release.jks.base64
```

### 3. Add four repository secrets

**Settings → Secrets and variables → Actions → New repository secret**

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | the contents of `release.jks.base64` |
| `ANDROID_KEYSTORE_PASSWORD` | the store password from step 1 |
| `ANDROID_KEY_ALIAS` | `dailydriver` |
| `ANDROID_KEY_PASSWORD` | the key password from step 1 |

Delete `release.jks.base64` afterwards. Keep `release.jks` itself, backed up.

---

## Installing on the phone

**Use adb, not the file manager.** Tapping a downloaded APK goes through Google Play's package
verifier, which is a separate mechanism from the "Scan apps with Play Protect" toggle and which
refused this app repeatedly with nothing but "App wasn't installed". The same file installed first
try over adb. Chasing that further was not worth it once a reliable route existed.

Wireless debugging means no cable is needed. Pairing persists, so this is a one-off:

> Settings → Developer options → **Wireless debugging** → on → **Pair device with pairing code**

```bash
adb pair <ip>:<pairing-port> <code>     # once, ever
adb connect <ip>:<connect-port>         # per session; the connect port differs from the pairing one
```

Then for each release:

```bash
gh release download android-v0.1.0 --pattern "*.apk" --dir ~/Downloads --clobber
adb install -r ~/Downloads/app-release.apk
```

`-r` reinstalls over the top and **keeps the database**, which works only because every release is
signed with the same key. That is the whole reason for the keystore.

The connect port changes when wireless debugging is toggled or the phone reboots; the pairing
survives. If `connect` fails, re-read the port from the Wireless debugging screen.

### ⚠️ It will not appear in the app drawer

`MainActivity` declares `CATEGORY_HOME`, not `CATEGORY_LAUNCHER`. This *is* a home screen, so it
deliberately has no drawer icon and looking for one will convince you the install failed. It did
not. Set it as the home app instead:

> Settings → Apps → ⋮ → **Default apps** → **Home app** → DailyDriver

Keep One UI Home available in that same screen as a fallback (T017). It is the difference between
an experiment and being locked out of your phone.

---

## Getting the command log off the phone

Type **`:export`** at the prompt. It writes JSONL to `Download/dailydriver-command-log-<stamp>.jsonl`
and reports the exact path back in the transcript. Pull it with a file manager, a USB cable, or:

```bash
adb pull /sdcard/Download/dailydriver-command-log-2026-08-22-131257.jsonl
```

**`scripts/export-command-log.sh` will not work here** (T166). It reads app-private storage through
`run-as`, which requires a debuggable build, and this is a signed release — it refuses with
`package not debuggable`. The script stays useful against emulators and debug builds. **Do not make
release builds debuggable to work around this**: that would expose every app-private file on the
phone to anything with adb access, permanently, to save typing one command.

## Version numbers come from the tag

`build.gradle.kts` reads `ANDROID_VERSION_NAME`, which the release workflow sets by stripping the
`android-v` prefix off the tag. So `android-v0.2.0` builds an APK reporting `0.2.0`, and App info on
the phone tells you which release you are running.

**A local build reports `dev`, not a number**, so an APK sideloaded off a workstation is never
mistaken for a release while you are working out what is on the phone.

`versionCode` is derived from the same string, two digits per component: `0.2.0` is `200`, `0.10.0`
is `1000`. Android compares it as an integer and refuses a downgrade, so it has to *rise* with the
version rather than merely differ — which is why each component is padded, and why a naive
concatenation would break the first time a minor number reached double figures.

Until `android-v0.2.0` this was pinned at `versionName = "1.0"` in source, so every release
identified itself identically and the tag was the only way to tell them apart.

## What runs when

| Trigger | What happens |
|---|---|
| Pull request, push to `main` touching `android/` or `fixtures/` | Unit tests, lint, instrumented tests on an API 33 emulator, and an unsigned release assemble |
| Push an `android-v*` tag | Tests again, then a signed APK attached to a GitHub Release |

Tests run again at tag time rather than trusting the branch was green, because a tag can be placed
on any commit, including one that never went through a pull request.

The release job **fails loudly if the keystore secret is missing**, and separately verifies the
built APK is actually signed before publishing. Without that second check a misconfigured secret
produces an unsigned APK, the upload matches the wrong file or nothing, and the first symptom is
"app not installed" on the phone.

---

## Relationship to Azure Pipelines

This does not replace section 10 of [`tasks.md`](tasks.md). Azure Pipelines still does the AZ-400
work — multi-stage environments, approval gates, slot-swap deployment, Bicep what-if — because
that is 50–55% of the exam and the reason those tasks exist. GitHub Actions handles what is wanted
before any of that: fast feedback on a pull request, and an APK you can install today, with no
Azure org or billing link needed first.
