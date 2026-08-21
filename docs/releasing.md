# Releasing the Android APK

Tag a commit, and CI builds a signed APK and attaches it to a GitHub Release you can install from
the phone.

```bash
git tag v0.1.0 && git push origin v0.1.0
```

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

Open the release on the device and download the APK. **The repository is private, so the phone
needs a signed-in GitHub session** — the GitHub app, or a browser logged into the account.

Android will ask to allow installs from that source the first time. Because every release carries
the same signature, it installs over the previous build and the launcher keeps its database.

---

## What runs when

| Trigger | What happens |
|---|---|
| Pull request, push to `main` | Unit tests, lint, instrumented tests on an API 33 emulator, and an unsigned release assemble |
| Push a `v*` tag | Tests again, then a signed APK attached to a GitHub Release |

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
