# CLI Launcher + Notes — Task List

> **Operational source of truth for task state.** Claude Code reads this file; it cannot
> see Todoist. Todoist mirrors this list as a dashboard — if the two disagree, **this file wins**.

Companion to `docs/design.md` (HLD v3). Task IDs are stable and referenced in commit
messages, ADRs, and the design doc.

## How to use this file

- **Work top to bottom.** Sections are in intended execution order.
- **One Claude Code session per task or tight cluster.** Start fresh per task rather than
  running one marathon session per section — long sessions accumulate stale context.
- **Check the box** (`- [ ]` → `- [x]`) when a task is done, in the same commit as the work.
- **Respect `after:`** — it lists hard prerequisites. Nothing else is ordered.
- If a task turns out to be wrong, **change it here and say so in the commit** rather than
  silently working around it.

## Conventions

| Field | Meaning |
|---|---|
| `P0` | Critical path |
| `P1` | Important, not blocking |
| `P2` | Nice to have / optional |
| `S` | Half a day or less |
| `M` | 1–2 days |
| `L` | 3+ days |
| `after:` | Hard prerequisites |

**Commit format:** `<type>(<scope>): Txxx <summary>` — e.g. `feat(desktop): T052 scaffold WPF project`

## Hard constraints — do not violate without an ADR

1. **Azure Functions: isolated worker model only.** In-process support ends **2026-11-10**.
2. **Cosmos DB: provisioned throughput, free tier opted in at creation.** Serverless capacity
   mode forfeits the free tier permanently and cannot be changed after the fact.
3. **The SLM never sits on the launcher fast path.** T0/T1/T3 must work with the model absent.
4. **Markdown is the note storage format.** Not rich-text JSON.
5. **`Notes.Shared` is the one definition of the contract.** Do not duplicate DTOs or merge rules.
6. **Never commit secrets.** Key Vault or user-secrets only.

## Decision gates

These stop and think rather than build. Do not blow through them.

| Task | Question |
|---|---|
| **T037** | Does LoRA load on the target device? Determines whether section 12 happens at all. |
| **T039** | Gemma-2 2B or Gemma-3 1B? Record the rationale. |
| **T084** | Fine-tuning timebox expiry — ship the number you have. |
| **T109** | Azure Boards vs Todoist for work-item traceability. |

## Milestones

- **T063** — desktop usable offline, standalone. *The .NET job-hunt artifact.*
- **T073** — all sync scenarios pass.
- **T083** — benchmark comparison table populated. *The portfolio artifact.*

---


## 01 · Repo & Claude Code

**Do this first.** Cheap, high leverage, everything else depends on it.

- [x] **T098** — Create monorepo skeleton (android/ desktop/ backend/ infra/ docs/)  
  `P0` `S`
  - START HERE — ten tasks below depend on it. `android/` `desktop/` `backend/` `infra/` `docs/` `.github/`. Monorepo, not three repos, so the shared contract and fixture corpus live with everything that consumes them.
- [x] **T099** — Root CLAUDE.md  
  `P0` `S` · after: T098
  - Auto-loaded into every Claude Code session. Project summary; **CURRENT PHASE** (keep updated — highest-value line in the file); pointers to `docs/design.md` and `docs/tasks.md`; the hard constraints below; commit convention.
- [x] **T100** — android/CLAUDE.md  
  `P0` `S` · after: T098
  - Kotlin/Compose conventions, Room patterns, T0–T3 tier rules, MediaPipe GPU-backend + LoRA constraints, target device.
- [x] **T101** — desktop/CLAUDE.md  
  `P0` `S` · after: T098
  - C# conventions, MVVM via CommunityToolkit.Mvvm, EF Core patterns, *UI never awaits the network*, outbox-in-same-transaction invariant.
- [x] **T102** — backend/CLAUDE.md  
  `P0` `S` · after: T098
  - Isolated worker (state the 2026-11-10 deadline), Cosmos partition key + RU budget, rev assignment rules, Notes.Shared is the contract — do not duplicate DTOs.
- [x] **T103** — Commit design doc + task list into docs/  
  `P0` `S` · after: T098
  - `docs/design.md` and `docs/tasks.md`. Claude Code reads local files and cannot see Todoist. **This file is the operational source of truth; Todoist is the dashboard.**
- [x] **T104** — .gitignore, .editorconfig, .gitattributes  
  `P1` `S` · after: T098
  - `.editorconfig` matters more than usual — stops the IDE and Claude Code fighting over formatting across two languages.
  - **Follow-up (process-efficiency pass, 2026-08-14):** `android/.idea/` was still tracked and kept drifting every Android Studio session (`deploymentTargetSelector.xml`, `gradle.xml` changed on nearly every commit in this project's history; `appInsightsSettings.xml`, `inspectionProfiles/` showed up untracked). Solo project, no value in sharing IDE state — added a blanket `.idea/` line to `android/.gitignore` and untracked the 10 files that were still committed, same `git rm -r --cached` approach as the `obj/`/`.vs/` cleanup this task already did for the .NET projects.
- [x] **T105** — Directory.Build.props + Central Package Management  
  `P1` `S` · after: T098
  - `Directory.Packages.props` with `ManagePackageVersionsCentrally`. *AZ-400 D3: package management.*
- [x] **T106** — ADR directory + backfill decisions already made  
  `P0` `M` · after: T098
  - `docs/adr/`. Backfill: WPF over Avalonia/WinUI, MediaPipe over llama.cpp, Markdown over rich-text JSON, conflict-copies over plain LWW, monorepo over split repos. These are the questions an interviewer asks.
- [x] **T107** — PR template + commit convention referencing task IDs  
  `P1` `S` · after: T098
  - `feat(desktop): T052 scaffold WPF project`. *AZ-400 D1: traceability.*
- [x] **T108** — Branch strategy + protection rules  
  `P0` `M` · after: T098
  - Trunk-based, short-lived branches. Protect `main`: require PR, require status checks, no direct pushes — even solo. *AZ-400 D2.*
- [x] **T109** — DECIDE: Azure Boards vs Todoist for work-item traceability  
  `P1` `S`
  - AZ-400 D1 wants commit→work-item→build linkage; Todoist can't do it. Suggested compromise: mirror only the *active* section into Boards, then judge whether it earned the duplication.
  - **Decision (2026-08-13):** Stick with Todoist only, for now — not standing up the Boards mirror. Accepts the AZ-400 D1 commit→work-item→build gap rather than paying for a second system to close it; `Txxx` in commits/PRs/tasks.md remains the actual traceability mechanism in daily use. Revisit if AZ-400 study reaches D1 and the gap needs to be demonstrated hands-on rather than just reasoned about.
- [x] **T110** — README skeleton  
  `P1` `S` · after: T098
  - Stub now, fill in T088.


## 02 · Launcher shell

Daily-drivable as your real home screen, zero ML.

- [x] **T001** — Create Android project (Compose, pinned min/target SDK)  
  `P0` `S`
  - Pin the ONE target device here; all benchmarks cite it.
  - **Target device (2026-08-14):** Samsung Galaxy S20 FE 5G, model SM-G781V (Snapdragon 865), Android 13 (API 33). `minSdk`/`targetSdk` pinned to 33 in `android/app/build.gradle.kts` to match.
  - **Correction (T003):** `compileSdk` couldn't stay at 33 — `core-ktx` and other already-present AndroidX deps require `compileSdk 36`, a hard build failure, not a warning. `compileSdk` is build-time-only (API surface visible to the compiler) and independent of `minSdk`/`targetSdk` (actual runtime behavior), so this doesn't undo the device-pinning decision — only `compileSdk` moved, to 36.
- [x] **T002** — Register HOME activity intent filters  
  `P0` `S` · after: T001
  - `ACTION_MAIN` + `CATEGORY_HOME` + `CATEGORY_DEFAULT`.
- [x] **T003** — Build app index from PackageManager  
  `P0` `M` · after: T001
  - Cache it; refresh on package install/uninstall broadcast.
  - `AppEntry`/`AppIndex` (pure, unit-tested) + `AppIndexRepository` (PackageManager query + dynamically-registered package-change `BroadcastReceiver`, `RECEIVER_NOT_EXPORTED` required by targetSdk 33). Wired into `MainActivity`'s lifecycle at T012.
  - **Correction (device-target audit, 2026-08-14):** the claim that "CATEGORY_HOME apps are auto-exempt from package-visibility filtering" was wrong — checked against developer.android.com's actual package-visibility docs and found no such exemption exists. `android:lint`'s `QueryPermissionsNeeded` check caught the consequence. Fixed with a real `<queries>` declaration in `AndroidManifest.xml` (`ACTION_MAIN` + `CATEGORY_LAUNCHER`, matching what `AppIndexRepository` actually queries) rather than relying on a nonexistent exemption.
- [x] **T004** — CLI shell UI: scrollback + input line, IME auto-open  
  `P0` `M` · after: T002
  - `shell/` package: `TranscriptEntry` (sealed Input/Output) + `Transcript` (immutable, pure, unit-tested — blank input ignored, input trimmed) + `ShellViewModel` (Compose state; ViewModel lifetime deliberately matches the "cleared on process death" rule) + `ShellScreen` (stateless, `LazyColumn` bottom-anchored, `BasicTextField` input line, `❯` prompt, monospace).
  - IME auto-open via `FocusRequester` + `windowSoftInputMode="adjustResize"` + `imePadding()`. Added `androidx.lifecycle:lifecycle-viewmodel-compose` to the version catalog.
  - Dark-only forced at the `DailyDriverTheme(darkTheme = true, dynamicColor = false)` call site, **not** in the theme itself — stripping the light palette and Material You out of `Theme.kt` is still T005's job.
  - Submitting appends a placeholder output line — originally `(T006)`, then `(T140)` once that was split out below, now `(T008)` once T140 itself landed and the remaining gap is T1 (T008), not the dispatcher. The real cascade replaces it once T008 exists.
  - **On-device check (2026-08-14):** IME opens correctly, insets/padding correct, `❯` renders correctly on the S20 FE.
  - **Follow-up critical pass:** disabled IME autocorrect on the input field (`autoCorrectEnabled = false`) — load-bearing, not cosmetic, since T0 is exact-match and silent autocorrect would make aliases/reserved commands miss unpredictably. Added `ShellTestTags` (test-tag anchors for future Compose UI tests, per the semantics-over-text-matching convention in `android/CLAUDE.md`). Fixed an inline fully-qualified `SolidColor` reference. Added 5 tests: `Transcript` internal-whitespace preservation and receiver-immutability; `ShellViewModel` input-without-submit is inert, double-submit doesn't duplicate, submit produces Input-then-Output structurally.
  - **Verified:** 16 unit tests pass (was 11), `assembleDebug` succeeds.
- [x] **T005** — Dark-only theme tokens  
  `P1` `S` · after: T004
  - No light palette exists by design.
  - `Color.kt`: deliberate token set (background/surface/onBackground/onSurface/onSurfaceVariant/primary/secondary/tertiary), no literal `#000000`/`#FFFFFF` anywhere — house style per explicit request, near-black/near-white tones instead. `Theme.kt`: dropped `darkTheme`/`dynamicColor` params entirely (there is exactly one theme now), removed Material You dynamic color and the light `ColorScheme`.
  - Found and fixed while in here: `themes.xml` (the pre-Compose XML theme controlling window background) was still inheriting `android:Theme.Material.Light.NoActionBar` — a real light palette causing a white flash on cold launch, not just a Compose-level gap. Switched to the non-Light Material parent with `windowBackground`/`colorBackground` explicitly set to match `Color.kt`'s `Background` token — the platform's default dark parent alone uses literal black, which would've just traded the white flash for a pure-black one. Also removed unused scaffold colors (`purple_*`, `teal_*`, literal `black`/`white`) from `colors.xml`.
  - **Follow-up:** on-device check found the IME (soft keyboard) still rendering in light mode — a separate app (Gboard), keyed off this app's own `Configuration` night-mode flag, not its Compose theme. Fixed via `AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_YES)`, which required `MainActivity` to extend `AppCompatActivity` (was `ComponentActivity`) and `themes.xml`'s parent to become `Theme.AppCompat.DayNight.NoActionBar` (AppCompatActivity crashes on a non-AppCompat theme; only the DayNight variant responds to `setDefaultNightMode`). Adds `androidx.appcompat:appcompat` as a dependency.
  - **Verified:** `assembleDebug` + all 16 unit tests pass, including after the AppCompat/DayNight change (theme-parent resolution failures show up at build time, not just runtime). **Not verified on-device** — cold-launch flash, on-screen color rendering, and specifically whether Gboard actually respects the forced night-mode flag all need a real run. This is IME-dependent behavior (well-documented for Gboard; no universal guarantee across every keyboard app), so it's worth confirming rather than assuming.
- [x] **T011** — Launch app by package intent  
  `P0` `S` · after: T003
  - `apps/AppLauncher.kt`. Resolves via `PackageManager.getLaunchIntentForPackage`, `FLAG_ACTIVITY_NEW_TASK` (launcher apps always give the target its own task), catches `ActivityNotFoundException` and returns `false` rather than crashing — the app being tapped can be uninstalled/disabled since the index was last refreshed, and a launcher crashing takes the whole home screen down with it. No unit test: no separable pure logic, same as `AppIndexRepository` at T003. Wired into the `:apps` browse view at T012 (tap a row to launch); still not reachable from the T0/T1 pipeline generally, that's T140/T141.
  - **Verified:** `assembleDebug` + all 16 existing unit tests still pass.
- [x] **T012** — Open all-apps list view  
  `P1` `M` · after: T003
  - **UI decisions (2026-08-14, user check-in):** not a static list — `:apps` (typed at the same input line, no separate search field) clears the transcript for real and enters a live-filter browse mode; text typed after `:apps ` narrows the list on every keystroke via `AppFiltering.filteredBy` (case-insensitive substring, deliberately not fuzzy — that's T008/T1's job). Text-only monospace rows, no icons. Flat list, no section headers; a left-side fast-scroll A-Z rail appears only past 20 apps. Tap a row to launch (T011's `AppLauncher`). Enter/Send no-ops while browsing — selection is tap-only, by design, not yet Enter-to-launch-top-match.
  - **Scope note:** building the full interaction above meant pulling a `:apps`-specific slice of command recognition forward from T140/T141 (`apps/AppsCommand.kt`) rather than shipping an inert, unwired list view. **T140 needs to absorb this one-off parser into its general reserved-command registry** rather than leave two mechanisms. **T141 is effectively already done** as a side effect — revisit its scope once T140 lands rather than duplicate this work.
  - New: `apps/AppsCommand.kt` (parse `:apps` + query, pure, tested), `apps/AppFiltering.kt` (`filteredBy`, pure, tested), `apps/FastScrollIndex.kt` (letter indexing, pure, tested), `shell/AppListView.kt` (the browse UI). `ShellViewModel` gained `appsCommand` (derived from `input`) and the clear-on-enter/no-op-submit behavior, all unit-tested (transition-only clear verified via object identity, not just "stays empty").
  - `MainActivity` now actually constructs and registers `AppIndexRepository` (T003) and `AppLauncher` (T011) for the first time — neither had a real lifecycle owner before this. Registered `onCreate`/unregistered `onDestroy`, deliberately not `onStart`/`onStop`: this Activity *is* the home screen, paused/resumed on every app switch but rarely destroyed, so pairing with `onStart`/`onStop` would mean constant re-registration for a broadcast that only needs to be eventually consistent, not fresh-per-visit.
  - **Known, deliberate gap:** `appIndexRepository.apps` is a plain (non-reactive) getter, not backed by `State`/`Flow`. A background package-install/uninstall while sitting idle at the plain prompt won't visually refresh until something else triggers recomposition (e.g., the next keystroke) — acceptable for this task's scope; revisit if it's ever actually noticeable in practice.
  - **On-device crash + fix (2026-08-14):** `IllegalArgumentException: Key "com.google.android.googlequicksearchbox" was already used` — a single package can expose more than one `CATEGORY_LAUNCHER` activity (confirmed on-device via Google's own app), so `queryIntentActivities` returned it twice and `AppListView`'s `items(apps, key = { it.packageName })` correctly rejected the duplicate key. Real bug in `AppIndex`, not just a UI workaround: `AppLauncher` can only ever launch a package's one default activity regardless of which duplicate is tapped, so two rows for the same package would've been misleading even without the crash. Fixed with `distinctBy { packageName }` in `AppIndex.replaceAll`, applied after sorting so the kept duplicate is deterministic (alphabetically-first label). My first hypothesis (an unguarded `.first()` on a blank label in `FastScrollIndex`) was a real, legitimate bug worth fixing but **not** the one that actually crashed — worth being explicit that it was a plausible guess, confirmed wrong once the actual stack trace came in, not a hit.
  - Also added while addressing user feedback: completing `:apps` by typing forward now auto-inserts the separating space, so the next keystroke goes straight into the filter query. Guarded against re-triggering while backspacing back down to exactly `:apps` (would otherwise make the just-deleted space reappear and get backspace stuck).
  - **Reachability tweak (2026-08-14, on-device feedback):** short/filtered results were pinning to the *top* of the list area, leaving dead space at the bottom right above the input line/thumb — farther to reach than necessary. `AppListView`'s `LazyColumn` now uses `Arrangement.Bottom` (matching how the transcript already behaves). Taken further per explicit request: the list is now reverse-alphabetical (Z→A top-to-bottom), so the alphabetically-*first* match is always the physically closest to the input line, not just whichever short list happens to be showing. Reversal happens only inside `AppListView` (`apps.reversed()`) — `AppIndex`, `AppFiltering.filteredBy`, and everything else stay canonical A→Z; this is a presentation-only decision, not a data-layer one. The fast-scroll rail is fed the same reversed list so its top-to-bottom letter order still corresponds to the list's own order.
  - **Bottom-anchoring didn't actually work on the first attempt** — `Arrangement.Bottom` alone was a no-op, because `verticalArrangement` only arranges *slack*, and the `LazyColumn` had `fillMaxWidth()` but no `fillMaxHeight()`. Inside a `Row` that leaves it sized to its content and top-aligned, so there was no slack to arrange. The pattern was copied from the transcript's `LazyColumn` without noticing that one gets its full height from `Column`'s `weight(1f)` instead — the `weight(1f)` passed into `AppListView` applies to the enclosing `Row`, not the list. Fixed by adding `fillMaxHeight()`. **Not unit-testable** (pure Compose layout), which is exactly why it shipped claimed-working but broken — the class of bug an `androidTest` Compose UI test would catch and the JVM suite structurally cannot.
  - **Auto-launch on unique match (2026-08-14, user request):** once the filter narrows to exactly one app, Enter opens it immediately, or it opens on its own after a 1s pause in typing. `AppFiltering.soleAutoLaunchTarget` is the pure, tested decision; the timer is a `LaunchedEffect` + `delay` in `ShellScreen`. Three non-obvious calls, each decided rather than asked: (1) a **blank query never auto-launches** — bare `:apps` on a one-app device would otherwise open it instantly when you asked to browse; (2) **launching clears the input** (`ShellViewModel.onAppLaunched`), which is correctness rather than tidiness — leaving `:apps chrome` in place means returning to the launcher, or rotating, re-fires the effect on the still-unique match and bounces straight back out, with no way to reach the prompt; (3) the **debounce keys on the query, not just the matched app** — `chr`/`chro`/`chrom` all resolve to the same target, so keying on the target alone would leave the first timer running and fire mid-word.
  - **Verified (final):** 58 unit tests pass (was 16 before this task), `assembleDebug` succeeds, lint shows no new findings beyond the pre-existing set. **On-device, user-confirmed:** the duplicate-key crash is gone, bottom-anchoring works, live filtering works. Not yet confirmed in the hand: whether the 1s auto-launch delay is the right feel — it's a single constant (`AUTO_LAUNCH_DELAY_MS` in `ShellScreen.kt`) if it wants tuning.
- [x] **T013** — Open Settings via intents  
  `P1` `S`
  - The intent-launching mechanism itself — no dependency on the T0 parser existing yet. T142 wires this in as `:settings`.
  - `settings/SettingsLauncher.kt`. Same shape as `AppLauncher` (T011) for consistency, though `ACTION_SETTINGS` failure is far less likely than an arbitrary package's. Standalone — not yet wired into the pipeline (T140/T142).
  - **Verified:** `assembleDebug` + all 16 existing unit tests still pass.
- [x] **T015** — BACK/HOME key behaviour  
  `P1` `S` · after: T004, T020
  - BACK is a no-op at the prompt, but **closes the editor overlay** when open — the only state where BACK does anything. HOME re-focuses input.
  - **BACK needed no new code.** A no-op at the bare prompt is just the default behaviour of a HOME activity with no `BackHandler` registered — nothing to build. The editor-overlay case was already shipped by T020's `BackHandler` (design check-in, 2026-08-19), for the reasons recorded there.
  - **HOME re-focuses the input via window-focus regain, not `onNewIntent`** (design check-in, 2026-08-20). `MainActivity.onWindowFocusChanged` already existed to re-hide the system bars whenever the window comes back to the foreground (T149) — the identical hook covers "the HOME button was pressed," but also BACK-from-another-app and dismissing the notification shade, all of which are "you're back at the launcher" and deserve the same treatment. A `focusRegainedSignal: Int` counter, bumped there, is threaded into `ShellScreen` as one more key on the *same* `LaunchedEffect` T020 added for "closing the editor restores focus to the prompt" — same bug, general form: regaining the window can leave the prompt unfocused and the keyboard down exactly the way closing the editor did, since Android doesn't automatically restore IME visibility when a window regains focus after losing it to another app.
  - **Focus only, not a full reset to prompt** (design check-in, 2026-08-20) — matches the design doc's literal wording. The input line is already always composed under `:apps`/`:notes`/`:notif` browse mode (those views replace the transcript, not the input row), so it never needs a reset to regain focus; and the refocus is skipped outright while the editor is open (the same guard the effect already had), so an open draft — and whatever the user was mid-typing into it — is left completely alone.
  - **Verified on-device**, Pixel 5 (API 33) with dailyDriver set as the HOME role holder: opened Settings (stealing window focus, IME down), pressed HOME, and the prompt's keyboard came back up with no tap. Repeated with the editor open instead — it stayed open and untouched across the same focus-loss/gain cycle, confirming the guard.
- [x] **T016** — Scrollback cap + clear on process death  
  `P2` `S` · after: T004
  - Ephemeral, never synced.
  - **Clear-on-process-death needed no code** — `ShellViewModel` holds the transcript, and a `ViewModel` survives configuration changes while dying with its process, which is exactly §4.1's wording. That was already true and already documented in that class; T016's only real work was the cap.
  - **200 entries = 100 commands, capped in `record` rather than at render time.** Trimming only in the UI would leave the retained list growing without bound behind it, which is the actual leak: this is the home screen, so the process is resumed and paused for months rather than restarted.
  - **The cap is even on purpose.** Every `record` appends exactly two entries (input + output), so an even cap trimmed from the front always drops whole exchanges; an odd one would eventually orphan an output whose input had already fallen off, leaving the scrollback opening on an answer to a question you can't see. There's a test asserting that pairing survives heavy trimming.
- [x] **T146** — Status line: time + date above the transcript, flashing colon  
  `P1` `S` · after: T004
  - **New (2026-08-16, user request)** — not in the design doc; §4.1 describes only the transcript and input line. A fixed status line at the top of the shell surface showing the current time and date, with the time's colon blinking on a one-second cycle (the classic digital-clock/terminal look).
  - **Keep the formatting pure and unit-tested** — `(Instant, ZoneId, colonVisible: Boolean) -> String` is exactly the kind of Android-independent logic `android/CLAUDE.md`'s testing section says belongs in `src/test/`. Only the ticking itself needs Compose.
  - **Battery matters here in a way it doesn't elsewhere in this app.** This is the home screen; a naive `while(true) { delay(1s) }` recomposing forever is a real drain, and unlike a normal app there's no "user closed it" to save you. Tick from a `LaunchedEffect` that stops when the Activity isn't resumed, and scope recomposition to just the status line rather than the whole shell.
  - **Design check-in (2026-08-19), all four open questions answered:** (1) **follow the system setting** via `DateFormat.is24HourFormat` rather than pinning a format; (2) date renders as **`Wed 19 Aug`** — short weekday, unpadded day, short month (ISO was offered and declined); (3) **500ms on / 500ms off** — a full blink per second, what a real digital clock does; (4) **stays visible in `:apps`/`:notif` browse mode** — it's a status line, and browse mode replaces the transcript, not the whole surface.
  - **Layout: time at the left edge, date at the right** (`Arrangement.SpaceBetween`), as the first child of `ShellScreen`'s `Column` — outside the browse-mode branch, so it persists. **The left edge is deliberately undecorated so T148's dot can slot into this same `Row`** rather than floating over it.
  - **The blink swaps the colon for a space, not for nothing** — in monospace both occupy one cell, so `14:32` → `14 32` keeps the same width and the row doesn't jitter twice a second.
  - **Local state, deliberately breaking `android/CLAUDE.md`'s state-hoisting default.** Hoisting the tick to `ShellViewModel` would recompose all of `ShellScreen` — transcript, app list, input line — twice a second for a clock. State lives inside `StatusLine` so recomposition is scoped to that one `Row`, which is the whole point on an always-on-screen surface. This is the exception the convention's "where practical" clause is for.
  - **Battery: the tick stops when the launcher isn't resumed**, via `repeatOnLifecycle(Lifecycle.State.RESUMED)`. A bare `LaunchedEffect` would keep waking twice a second for as long as the process lives — and this Activity is paused on every app switch but rarely destroyed, so that's the common case, not an edge one. Restarting on RESUME also means returning to the launcher paints a current time immediately rather than a stale one.
  - **New dependency: `androidx.lifecycle:lifecycle-runtime-compose`** (2.10.0, shares the existing version ref). Needed for the non-deprecated `LocalLifecycleOwner` — the `androidx.compose.ui.platform` one warns. **Attempted and reverted:** bumping the lifecycle ref to 2.11.0 to also clear lint's stale-version hits, which fails — 2.11.0 requires `compileSdk 37` *and* AGP 9.1.0 (this build is on 36 / 8.10.0). Caught by rebuilding rather than assuming, same lesson as T007's Room bump; the version ref now carries a comment so the next person doesn't retry it.
  - **Lint baseline moved 9 → 10 warnings**, entirely from that new artifact drawing its own `GradleDependency` "newer version available" hit. No new *categories*, still 0 errors, and not actionable without an AGP major upgrade. Future tasks should compare against 10, not 9.
  - `shell/StatusLineFormat.kt` (pure `statusLineText(instant, zone, is24Hour, colonVisible, locale)` → `StatusLineText(time, date)`, 9 tests covering both formats, midnight/noon, the colon swap, and zone handling including a date that rolls backwards across a timezone); `shell/StatusLine.kt` (the composable and its tick). `java.time` needs no desugaring — API 26+, well below `minSdk` 33.
  - **Verified:** 185 unit tests pass (was 176, +9), `assembleDebug` succeeds, lint 0 errors / 10 warnings (see above). Rendered output eyeballed via a scratch test across all four format×blink combinations before removing it. **Not verified on-device** — the blink cadence's feel, whether the row reads as chrome rather than content, and the resumed-only tick actually pausing all need a real run.
- [x] **T149** — Immersive fullscreen: hide the system status and navigation bars  
  `P2` `S` · after: T146
  - **New (2026-08-19, user request)** — not in the design doc. Spotted from a device screenshot: the system status bar's clock sat directly above T146's status line showing the same time, so the launcher was displaying the time twice.
  - **Both bars, per design check-in** — status bar *and* navigation bar, not just the top one. A launcher built to look like a terminal shouldn't be framed by system chrome on either edge.
  - **`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, not a hard hide.** Swiping from an edge still brings the bars back temporarily, which is what keeps the notification shade and quick settings reachable — important since this is the home screen, not an app you can back out of. Gesture navigation is unaffected either way: the bars are hidden, not disabled.
  - **Re-applied in `onWindowFocusChanged`, not just `onCreate`.** The system restores the bars whenever the window loses focus — returning from another app, dismissing the shade — so a one-shot call at launch would silently stop working after the first app switch. This is the part that's easy to get wrong and looks fine in a fresh-launch test.
  - **Display-cutout padding still applies.** `safeDrawingPadding()` in `ShellScreen` now resolves to the cutout inset rather than the (now-zero) status-bar inset, so the status row sits below the camera hole-punch instead of under it. The bottom reclaims its full height since the nav bar inset goes to zero.
  - **Thematically nice side effect:** with the shade one step further away, T147's `:notif` becomes the natural in-app route to notifications, and T148's dot the natural indicator that there's anything to look at.
  - **Verified:** builds and all 187 tests pass, lint unchanged. Android window-API glue with no separable pure logic, so no unit tests of its own — same call as `SettingsLauncher`/`NotificationRepository`. **Not verified on-device**, and this one needs it most: whether the bars stay hidden across app switches and shade pulls is exactly what a build can't tell you.
- [x] **T150** — Test coverage sweep: audit the suite for gaps, fix what it uncovers  
  `P1` `M` · after: T148, T149
  - **New (2026-08-19, user request)** — a review of the whole unit suite for gaps, then filling them. **187 → 410 tests** (2.2×), 0 failures, lint unchanged at 0 errors / 10 warnings.
  - **Every pure-logic file already had a test file**, so the gaps were *inside* them, not missing coverage wholesale. Three findings were substantive rather than edge-case padding:
    - **`AliasListing` sorted case-sensitively** (`toSortedMap()` → codepoint order), so every capitalised alias sorted ahead of every lowercase one — `Zoo` before `apple`. Inconsistent with `AppIndex`, which sorts case-insensitively, and meaningless for user-chosen names where casing is incidental. **Fixed** to `sortedBy { it.key.lowercase() }`; `sortedBy` is stable, so names differing only in case keep their relative order.
    - **`displayLine()` treated an empty-string title as present.** `title ?: text` only falls back on `null`, so a notification carrying `EXTRA_TITLE = ""` alongside real body text rendered as `Messages: ` — the exact case the fallback exists for, and the one where it didn't fire. Real notifications do ship empty extras. **Fixed** with `takeIf { it.isNotBlank() }` on both fields.
    - **`editSimilarity` had no direct tests at all** — only `damerauLevenshtein` was covered, despite `editSimilarity` carrying its own documented `"" vs ""` → 1.0 guard and being what actually decides whether T1's TYPO rung fires. Now 12 tests including that guard, the 0.70 floor, and the design doc's `chr`/`chrome` = 0.5 worked example.
  - **Registry-wide invariants added** (`ReservedCommandParserTest`) — these test no single command, they're guardrails for the *next* one: no duplicate tokens, every registered token actually dispatches, only `?` may skip the `:` prefix, and each token resolves to its own variant except the two deliberate help spellings. T141–T145/T147 were each added by copying the entry above, which is exactly the workflow that quietly introduces a duplicate.
  - **Claims in doc comments turned into tests.** `evaluateAliasDefinition` asserts it prevents cycles of *any* length, but only the two-alias case was tested — three- and four-alias cycles now are. `ShellViewModel`'s auto-space was generalized across the registry at T140 but only ever tested for `:apps` — now a property over `reservedCommandTokens`, so a token added later is covered on registration.
  - **A wart found and documented rather than "fixed":** `parseAliasDefinition`'s greedy `\S+` backtracks to the last `=` in an unspaced run, so `alias a=b=c` yields the name `a=b`. Harmless (you'd have to type it deliberately) and tightening the pattern risks the main path, so there's a test recording the behaviour instead.
  - **One test caught a wrong test, not wrong code:** an intended SUBSTRING-band ordering check used `"xoogley"`, which is only 2 edits from `"oogle"` (0.71 similarity) and therefore lands on TYPO. The rewritten version asserts the band precondition explicitly so the premise fails loudly rather than the assertion quietly checking something else.
  - Deleted `ExampleUnitTest` (`assertEquals(4, 2 + 2)` — Android Studio template cruft). Kept `ExampleInstrumentedTest`: it guards the `com.example.dailydriver` namespace that `android/CLAUDE.md` flags for a deliberate future rename, and it's the only thing proving the instrumented harness runs at all.
  - **Review pass over the new tests afterwards**, which removed four as redundant or misleading — a sweep that only ever grows is how a suite gets slow and untrustworthy:
    - *"a token that is a prefix of another still resolves to itself"* — **name claimed something it didn't test.** No registered token is actually a prefix of another (`:note` and `:notif` diverge at the fourth character), so the assertions only re-checked what `every registered token actually dispatches` already covered. Deleted; the forward-looking reasoning moved into the existing whole-word test's comment, where it's accurate.
    - *"every minute of the day formats without throwing"* — named "every minute" but stepped by 7, and asserted only `isNotBlank()`, which a badly broken formatter would pass. Fully subsumed by the two hour-sweeping tests beside it that assert real content.
    - *"a whitespace-only query never auto-launches"* — used a four-app list, so `singleOrNull()` returns null regardless and the test would have passed even with the blank-query guard deleted. The pre-existing single-app version is the one that actually exercises that guard.
    - *"is symmetric"* and the two `:apps`-specific auto-space tests — strict subsets of the property tests added in this sweep. Their explanatory comments were folded into the property tests that replaced them.
  - Also converted five negated `assertTrue(!x)` / `assertTrue(x.not())` to `assertFalse(x)`, which gives a usable failure message instead of "expected true, was false".
- [ ] **T017** — Install as daily driver; keep fallback launcher installed  
  `P0` `S` · after: T011
  - Never ship an untested build to your only home screen.


## 03 · Command pipeline (T0/T1/T3)

Tiered cascade. **The SLM is not the front door.**

- [x] **T140** — T0 dispatcher: exact-match router + T1 fallthrough  
  `P0` `S` · after: T004
  - **Replaces T006** (2026-08-14) — was one lump task for all reserved commands; split into one task per command (T141–T145 below) so each can land independently instead of all-or-nothing.
  - The mechanism itself: recognize a leading `:` (or a bare `?` — see T145), hold a small registry of reserved-command handlers, dispatch on exact match, fall through to T1 when nothing matches. T141–T145 all plug into this; none of them make sense before it exists.
  - **Built (2026-08-16):** `command/ReservedCommand.kt` (sealed interface, one `Apps(query)` variant today) + `command/ReservedCommandParser.kt` (the registry — an ordered list of token→builder pairs tried in sequence, pure and tested, generalizing the `matchCommandToken` shape every T0 command shares). Retired the `:apps`-only one-off parser pulled forward at T012 (`apps/AppsCommand.kt`, deleted, not kept alongside) — `ShellViewModel`, `ShellScreen`, and `MainActivity` all migrated from `AppsCommand`/`parseAppsCommand` to `ReservedCommand`/`parseReservedCommand` in the same commit.
  - **Judgment call, not generalized despite the temptation:** transcript-clear-on-entry stays scoped to `ReservedCommand.Apps` specifically, not "any reserved command." `:settings` (fires an intent and leaves) and `:note` (opens an overlay *over* the visible transcript, per design doc §4.6) have no reason to want it, and generalizing it would have been guessing at their UX ahead of T142/T143 actually deciding it. Auto-space-on-completion, by contrast, **was** generalized to every registered token — pure convenience, no plausible downside even for a command that turns out to take no argument.
  - Still no real T1 (T008) or T2 — the fallthrough placeholder ShellViewModel writes on an unmatched submit is unchanged in spirit, just re-labeled to say `(T008)` instead of `(T140)`.
  - **Verified:** 59 unit tests pass (was 58 — net +1: `AppsCommandTest`'s 7 replaced by `ReservedCommandParserTest`'s 8), `assembleDebug` succeeds, lint shows no new findings. Not yet checked on-device — the `:apps` browse experience should be byte-for-byte unchanged from the user's perspective, since this was a pure refactor of the recognition mechanism underneath it, but that's exactly the kind of claim worth a real run to confirm rather than assume.
  - **Bug found during that on-device check, predating T140** — the auto-space-after-`:apps` feature (added at T012) turned out to actually be broken: `InputLine`'s `BasicTextField` used the plain-`String` overload, which has no explicit cursor tracking. When `ShellViewModel` programmatically grew the text (appending the space) without a matching user keystroke, Compose's implicit diff-based cursor inference left the cursor at its pre-injection position — right after `s`, before the space — so the next keystroke landed *before* the space (`:appsc `) instead of after it (`:apps c`). Fixed by switching to the `TextFieldValue`-based overload and explicitly forcing the cursor to end-of-text on every value change, removing the implicit inference entirely rather than special-casing the auto-space path. Not unit-testable (Compose layout/cursor behavior); needs on-device confirmation like the rest of this task's UI surface.
- [x] **T141** — `:apps` reserved command  
  `P1` `S` · after: T140, T012
  - Routes to the all-apps list view T012 builds.
  - **Confirmed done as a side effect of T140** (2026-08-16), per the note left at T012 — `:apps` is registered in `ReservedCommandParser`'s registry exactly as T141 would have built it. No separate work needed.
- [x] **T142** — `:settings` reserved command  
  `P1` `S` · after: T140, T013
  - Routes to the Settings intent T013 builds.
  - **No query, unlike `:apps`.** `ReservedCommand.Settings` is a `data object`, not a `data class` — there's nothing to filter, it just fires and returns to the prompt. Registered into T140's registry the same shape as every other token (`":settings"` → `SETTINGS_TOKEN`), so it gets the generalized auto-space-on-completion behavior for free; a query typed after it (`":settings foo"`) still resolves to `Settings`, just with the trailing text discarded rather than rejected.
  - **Enter-fires, not tap-to-select** — the opposite of `:apps`' browse-and-tap shape, since there's no list to browse. `ShellScreen` gained an `onSettingsCommand` callback, checked in the submit `when` block ahead of the alias branches (the two states never actually overlap, since alias names can't start with `:`). `MainActivity` wires it to `settingsLauncher.open()` followed by `viewModel.onSettingsLaunched()` — same shape as `onAppClick`, including ignoring `open()`'s `Boolean` result, which `onAppClick` also does for `AppLauncher.launch()`.
  - **`ShellViewModel.onSettingsLaunched()`** just clears the input, mirroring `onAppLaunched()` — `:settings` hands off to another app entirely, so there's nothing to return to and nothing for the transcript.
  - **Verified:** 132 unit tests pass (was 127, +5), `assembleDebug` succeeds, lint unchanged (0 errors, 9 warnings, same categories as baseline). **Verified on-device (2026-08-19)** — Enter on `:settings` opens the system Settings app and returns to an empty prompt.
- [x] **T143** — `:note` reserved command  
  `P1` `S` · after: T140, T020
  - **Scope cut back, 2026-08-19 user request:** opening the real editor overlay depends on T020, which doesn't exist yet — rather than block T143 on it, `:note` is recognized and registered now but just records a placeholder ("will open notes in the future (T020)") until T020 lands and gives it something real to open. The bare-`:note`-vs-`note <name>` question from the design doc's worked example (§4.6 `note standup`, vs the colon-prefix table in §4.2) is still unresolved — deferred to T020, since it doesn't affect a placeholder either way.
  - **`ReservedCommand.Note` keeps its `query`**, unlike `:settings`' argument-less `Settings` — the design doc's own worked example gives notes a name argument, so the field is captured now (`":note standup"` → `Note("standup")`) even though nothing reads it yet, rather than adding it later as a breaking change to the type.
  - **`ShellViewModel.onSubmit()` restructured** from a blanket `if (reservedCommand != null) return` into an exhaustive `when` over `ReservedCommand?` (`Apps`/`Settings`/`Note`/`null`), each branch stating its own reasoning — `Note` is the first reserved command that actually wants `onSubmit()`'s normal record-and-clear behavior rather than bypassing it (`:apps`' sole-match and `:settings` are both intercepted earlier, in `ShellScreen`). No `ShellScreen` or `MainActivity` change needed: `:note` has no browse mode and no external `Context` to reach for, so it falls straight through the existing `else -> onSubmit()` fallback like any other unmatched-tier input.
  - **Verified:** 138 unit tests pass (was 132, +6), `assembleDebug` succeeds, lint unchanged (0 errors, 9 warnings, same categories as baseline). **Verified on-device (2026-08-19).**
- [x] **T144** — `:alias` reserved command  
  `P1` `S` · after: T140, T007
  - Lists stored aliases (§4.7). Distinct from T007's user-alias *resolution* (typing `gm` and having it resolve) — this is the no-argument `:alias` listing what's defined.
  - **`alias/AliasListing.kt`**: pure `Map<String, String>.formatAliasListing()` — one `name = command` line per alias, sorted alphabetically for a stable, testable listing regardless of map iteration order; `"no aliases defined"` when empty. `=` matches the definition syntax (`alias gm = chrome`) rather than inventing new notation for the same relationship.
  - **Formatted in `ShellScreen`, not `ShellViewModel`** — unlike `:note`, `:alias` needs real data (the live aliases map), which only `ShellScreen` holds (passed down from `MainActivity`/`AliasRepository`); the `ViewModel` has no route to it and shouldn't grow one just for this. `ReservedCommand.Alias` is intercepted in the submit `when` block the same way `:settings` is, calling a new `onAliasListRequested(listing)` callback with the already-formatted string — `ShellViewModel.onAliasListRequested` just records it and clears input, the same shape as `onAliasDefined` recording a result computed elsewhere.
  - **`ReservedCommand.Alias` is a `data object`, no query** — like `Settings`, unlike `Note`: nothing in the design doc or this task suggests filtering the listing.
  - **Verified:** 146 unit tests pass (was 138, +8), `assembleDebug` succeeds, lint unchanged (0 errors, 9 warnings, same categories as baseline). **Verified on-device (2026-08-19)** — `:alias` renders a real alias set as expected.
- [x] **T145** — `:help` / `?` reserved command  
  `P1` `S` · after: T140
  - **New — not in the original design doc's reserved-command list.** Two aliases, `:help` and a bare `?`, route to the same handler. Owns the actual help text; T010's T3 fallback reuses this content rather than defining its own copy when nothing else matches.
  - **Built with T010 on one branch** (`feat/T145-T010-help-and-t3-fallback`) — T010's only remaining blocker was this task, and it exists to consume this task's output, so splitting them would have shipped a help text with no second caller.
  - **The help listing is generated from T140's registry, not hand-written.** `RegisteredCommand` gained a nullable `help: CommandHelp?` (`usage` + `description`); `reservedCommandHelp` is `registry.mapNotNull { it.help }`, and `command/HelpText.kt` renders it with the usage column padded to a common width for monospace alignment. Registering a command *is* documenting it — the next command to land can't forget to update a separate constant. A test asserts the inverse property too: every documented usage starts with a token `parseReservedCommand` actually accepts, so help can't describe something undispatchable.
  - **`usage` is spelled out rather than derived from `token`** — a bare token can't express `:apps [query]`, and `:help, ?` documents both spellings on one line. The `?` registry entry carries `help = null` so it's hidden from the listing rather than repeating itself as a second row.
  - **A static footer covers what the registry can't**: `alias <name> = <command>` and bare-name fuzzy launching are T0/T1 behaviors with no token to hang a `CommandHelp` off, but they're most of day-to-day use — a command-only listing would document the smaller half of the app.
  - **`?` is the one registered token with no `:` prefix**, which T140's registry already anticipated. It goes through `matchCommandToken` unchanged, so it obeys the same whole-token rule (`?x` and `what?` don't resolve) and picks up T140's auto-space-on-completion (`?` → `? `). Left uniform rather than adding an opt-out flag to the registry for one character of cosmetics — same call T142 made for argument-less `:settings`.
  - **No `ShellScreen` change**: `helpText()` is pure and needs no `Context` or repository, so `ShellViewModel` calls it directly in `onSubmit`'s `when`, the same shape as `:note` — unlike `:alias`, which needed `ShellScreen` to supply the live aliases map.
  - **Verified:** 176 unit tests pass (was 159, +17 across T145 and T010), `assembleDebug` succeeds, lint unchanged (0 errors, 9 warnings, same categories as baseline). Rendered output eyeballed via a scratch test before removing it. **Verified on-device (2026-08-19)**, covering T010's fallback output as well as `:help`/`?`.
- [x] **T147** — `:notif` reserved command — list + open active notifications  
  `P1` `M` · after: T140, T012
  - **New (2026-08-16, user request)** — not in the design doc. Lists current notifications as a tappable list in the same shape as T012's `:apps` browse view (bottom-anchored, monospace, tap to act), registered into T140's registry like any other reserved command.
  - **⚠ Special-access permission — first in the project.** `NotificationListenerService` + `BIND_NOTIFICATION_LISTENER_SERVICE`, granted by hand under Settings → Notification access (no runtime dialog). `notifications/DailyDriverNotificationListenerService.kt` publishes a nullable `instance` while Android has it connected — the only way the rest of the app can reach `activeNotifications`, since the service's lifecycle is entirely system-owned. `notifications/NotificationRepository.hasAccess()` checks via `NotificationManagerCompat.getEnabledListenerPackages`; `openNotificationAccessSettings()` deep-links via `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`.
  - **Design check-in (2026-08-19), three open questions resolved:** (1) filter is `isOngoing()` alone, not also `!isClearable()` — simpler, matches the original request's examples. (2) **No live filter as you type** — unlike `:apps`, notification counts are usually small and there's no single obvious field to filter by; `ReservedCommand.Notif` is a `data object`, no query. (3) **No explanation screen** when access isn't granted — Enter deep-links straight to Settings, same as `:settings`' fire-and-forget shape; there's no browse view to show in that state at all (`ShellScreen`'s `notifCommand` is null unless `hasNotificationAccess` is also true, so it falls back to the plain transcript with Enter wired to the deep-link instead of `onSubmit()`).
  - **Tapping fires the notification's own `contentIntent`**, and — a detail beyond the original request, needed to match real notification-shade behavior — also cancels it via `cancelNotification(key)` when the notification's own `FLAG_AUTO_CANCEL` says it should disappear once acted on. Firing the `PendingIntent` ourselves bypasses SystemUI, which is what normally handles that; without this, tapping in `:notif`'s list would silently behave differently from tapping the same notification in the real shade. No-ops on a null `contentIntent` (not every notification has one) or a `CanceledException`.
  - **Bottom-anchored, oldest-first (`newestLast()`)** — mirrors `AppListView`'s "closest row to the input is the most relevant one" reasoning, with recency standing in for alphabetical order. No fast-scroll rail (nothing alphabetical to jump to).
  - **Split the way T012 did:** `notifications/NotificationRecord.kt` (plain data, mirrors `AppEntry`), `NotificationFiltering.kt` (`excludeOngoing`/`newestLast`) and `NotificationDisplay.kt` (`displayLine`, title-preferred-over-text-preferred-over-placeholder) are pure and unit-tested; `DailyDriverNotificationListenerService`/`NotificationRepository` are Android glue in the `AppIndexRepository` mould, not unit-tested.
  - **Verified:** 159 unit tests pass (was 146, +13), `assembleDebug` succeeds, lint unchanged (0 errors, 9 warnings, same categories as baseline) — including the new manifest `<service>` entry, no new `ExportedService`-style findings. **Not verified on-device** — this is the task most worth a real run before merging: granting/revoking access, the deep-link round-trip, tapping a real notification's `contentIntent`, and the auto-cancel behavior all depend on real system state a unit test can't cover.
- [x] **T148** — Unread-notification indicator dot  
  `P2` `S` · after: T147, T146
  - **New (2026-08-16, user request)** — a small dot in the top-left when any non-persistent notification is present, using the same filtered set T147 builds (so "is there anything?" is a boolean over that list, not a second source of truth).
  - **T146 landed first (2026-08-19), so this one adapts to it** — as that task's note said whichever went second should. The status row is a `Row` in `shell/StatusLine.kt` with `Arrangement.SpaceBetween` (time left, date right), and its **left edge was deliberately left undecorated for this dot**. Add it as a leading child of that `Row`, before the time `Text`, rather than building a separate overlay. Note the row's state is local to `StatusLine` and ticks twice a second; the dot's source is `NotificationRepository.notifications` (T147's already-filtered set), which will need passing in — `StatusLine` currently takes no data parameters.
  - **On "white":** the request said white, but T005 established that this project uses no literal `#FFFFFF` anywhere — use the existing `OnBackground` token (`#E4E1E6`) or add a deliberate token, rather than reintroducing pure white for one dot. **Resolved as `onBackground`** — the dot is the row's only undimmed element, which is what makes it read as the thing worth noticing next to the dimmed clock and date.
  - **`•` (U+2022) as a leading `Text` in T146's status `Row`**, exactly where that task reserved space for it. `shell/StatusLineFormat.kt`'s `unreadIndicator(hasUnread)` returns `"• "` or `"  "` — **always two cells wide either way**, so a notification arriving doesn't shove the clock sideways. Same invariant as T146's blinking colon becoming a space rather than vanishing, and it's what the unit test actually asserts.
  - **Event-driven, not polled.** `DailyDriverNotificationListenerService` maintains a `hasUnread` field, refreshed on `onListenerConnected`/`onNotificationPosted`/`onNotificationRemoved` (guarded against `SecurityException`, since `activeNotifications` throws if a disconnect races an in-flight callback and taking the home screen down over that would be unacceptable). The status line reads it on the tick it already runs, so the dot costs a **field read** per blink rather than a binder call.
  - **Passed as `() -> Boolean`, not `Boolean`.** `NotificationRepository` is a non-reactive getter, so a plain value would freeze the dot until something else recomposed `ShellScreen` — a keystroke, typically. The lambda lets `StatusLine` re-read on its own 500ms tick, reusing T146's timer instead of adding a second one.
  - **Deliberate near-duplication:** the service tests `!isOngoing` directly rather than going through T147's `excludeOngoing()`. Answering yes/no through that path would materialize a `NotificationRecord` per notification — each with a `PackageManager` label lookup — just to check whether a list is empty. The shared rule is documented on both sides.
  - **Verified:** 187 unit tests pass (was 185, +2 — the width invariant and the dot's presence rule), `assembleDebug` succeeds, lint 0 errors / 10 warnings (unchanged from T146's revised baseline). **Not verified on-device** — needs a real notification arriving and clearing to confirm the dot appears and disappears without the clock shifting.
- [x] **T007** — Alias store (Room) + T0 resolution  
  `P1` `M` · after: T140
  - Device-local, not synced.
  - **First use of Room in this project** — added `androidx.room` + KSP (not kapt) to the version catalog. `alias/Alias.kt` (`@Entity`) + `AliasDao.kt` + `AliasDatabase.kt` (`exportSchema = false`, no migrations exist yet) + `AliasRepository.kt` (Context-owning glue, mirrors `AppIndexRepository`'s shape — takes `applicationContext` specifically since the DB instance is meant to outlive any one Activity, unlike the short-lived `AppLauncher`/`AppIndexRepository` calls). Room version bumped from an initial guess (2.6.1) to 2.8.4 after lint reported it during a normal pass — verified by rebuilding, not just taking the suggestion on faith.
  - **Definition syntax, decided against the design doc's one example:** bare `alias <name> = <command>` (`alias/AliasDefinition.kt`), no colon prefix — deliberately a different shape than T140's `:token` registry, so there's no precedence question between reserved commands and aliases at T0 (their input shapes don't overlap). Alias names can never start with `:`, enforced at parse time, for the same reason.
  - **Conflict/cycle checking, per explicit request:** self-reference (`alias a = a`) and pointing at any *existing* alias are both hard-rejected; redefining an existing name is allowed but reported as `Overwritten(previousCommand)` rather than silently merged into `Created` — "verify" read as detect-and-report, "prevent" (for cycles) read as actually block, matching the two different verbs used when this was requested. The existing-alias-only check is sufficient to prevent cycles of *any* length, not just direct pairs: completing a cycle requires its last link to target something already stored (every link can't be created simultaneously), so whichever definition would close the loop is always the one caught. Proven by construction, not by graph traversal — see the reasoning in `AliasDefinition.kt`'s doc comment.
  - **T0 resolution is wired into live dispatch.** It initially wasn't, on the reasoning that "nothing downstream (`create_note`, T2) exists yet to execute a resolved command" — **that was wrong**, and on-device testing caught it immediately: `alias a = :apps` points at a reserved command that *already works*, so resolution was useful the moment it existed. The mistake was generalizing from the design doc's `create_note` example to all aliases. `ShellViewModel.onAliasExpanded` substitutes the expansion via `onInputChange` (inheriting auto-space and the enter-apps-mode transcript clear for free, so `a` behaves identically to typing `:apps`) and then dispatches in the same Enter — two Enters would defeat the muscle-memory purpose T0 exists for. Cannot recurse: aliases can't point at aliases, enforced at definition time.
  - **Deliberate, minor lossiness in the transcript:** expanding a non-command alias records the *expansion* (`maps`) as the input line rather than what was typed (`b`), since that's what actually ran. Revisit if it proves confusing in practice — showing both would take a third transcript entry or a new entry type.
  - `ShellViewModel` gained `onAliasDefined(submittedInput, result)`, deliberately taking the submitted text as a parameter rather than reading its own `input` — the Room write it follows is async, so by the time the result is known the live `input` may already be something else the user's typed since. `ShellScreen` captures the input and launches the write via `rememberCoroutineScope()`, matching the pattern already used for the fast-scroll rail in `AppListView`.
  - **Verified:** 89 unit tests pass (was 59), `assembleDebug` succeeds, lint clean (Room's own `GradleDependency` hits resolved by the version bump, no new finding categories). **On-device:** alias *definition* confirmed working (`alias a = :apps` → "alias 'a' created" persisted). Resolution wired in afterward and **still needs an on-device run** — typing `a` should open the app browser, and `b` (pointing at a plain app name) should show the T008 placeholder until T1 exists.
- [x] **T008** — T1 fuzzy matcher (edit distance + prefix/acronym boost)  
  `P0` `L` · after: T003
  - The 90% path. Must feel instant.
  - **Deliberate departure from the design doc's one-line spec** ("normalized edit distance + prefix/acronym boost"). Edit distance measures typo-similarity between comparable-length strings, which is the wrong instrument for the doc's *own* example: `chr` vs "Chrome" is 3 edits over 6 characters — 0.5 similarity, barely above noise — because `chr` isn't a typo of Chrome, it's an abbreviation. Restructured as a **ladder of strategies where the best one wins**, rather than one base score accumulating boosts: strategies are competing explanations for a match, not evidence to sum, and a max is far easier to tune and debug than interacting weights. Edit distance keeps its place as the rung that rescues genuine typos, which is what it's actually good at.
  - **Bands** (`apps/AppMatching.kt`): `EXACT` 1.0 · `PREFIX` 0.85–0.95 · `ACRONYM` 0.80–0.85 · `TYPO` 0.56–0.80 · `WORD_PREFIX` 0.70–0.78 · `SUBSTRING` 0.60–0.68 · `SUBSEQUENCE` 0.50–0.58. Within a band, score scales by coverage, so `ca` ranks Camera (6 chars) above Calculator (10).
  - **Damerau-Levenshtein, not plain Levenshtein** (`apps/EditDistance.kt`) — adjacent transposition ("chorme") is the most common typing error, and plain Levenshtein charges it 2 edits rather than 1; on a 6-character word that's 0.67 vs 0.83 similarity, i.e. the difference between missing and rescuing the typo. Optimal-string-alignment variant; the unrestricted algorithm's extra generality is irrelevant at label lengths.
  - **camelCase word splitting** so single-token product names have usable initials — "PayPal" → `[pay, pal]` → `pp`. Split from the *original* label, not a lowercased copy; lowercasing first destroys the exact case boundaries the splitter needs (a bug I introduced and caught before it shipped).
  - **UI decisions (2026-08-16, user check-in):** matching runs live as you type, with the best match shown dim above the prompt (`→ Chrome`) so Enter is never a blind commit — and aliases preview their expansion the same way, for the same reason. Originally: when several candidates are near-tied, Enter lists them instead of guessing (`ca` → Camera/Calendar/Calculator) rather than picking one.
  - **Reversed (2026-08-19, explicit user request):** Enter or a 1-second typing pause now always launches the top-ranked match, full stop — no ambiguity gate. `confidentMatch` (score ≥ 0.70 **and** a ≥ 0.15 lead over the runner-up) is gone, replaced by `topMatch` (`apps/AppMatching.kt`), which is just `rankedMatches(query).firstOrNull()`. The candidate-listing path is fully dead and was deleted rather than left stranded: `ShellViewModel.onAppCandidates`, its two tests, `MainActivity`'s wiring, and the `MAX_LISTED_CANDIDATES`/`MIN_CONFIDENT_SCORE`/`MIN_CONFIDENT_LEAD` constants are all gone. The preview line's `+N more` suffix (`matchPreview`) is now the *only* ambiguity signal — it tells you other candidates exist so you can type further before the pause fires, rather than the app refusing to guess for you.
  - **Performance is a non-issue at this scale and was deliberately not engineered for:** ~150 apps × a few string ops plus one ~90-cell DP matrix is sub-millisecond against a 50 ms budget. No trie, no index, no caching — that would be complexity buying nothing.
  - **Verified:** 122 unit tests pass (was 89) as originally landed; 120 after the 2026-08-19 revision (net -2: three "confidence" tests and two `onAppCandidates` ViewModel tests removed, three `topMatch` tests and one flipped weak-match assertion added). `assembleDebug` succeeds, lint unchanged (0 errors, 9 warnings, same categories as baseline) after both rounds. **Not verified on-device** — the preview line's feel while typing, and whether always-launch-top-match feels right in practice (vs. the originally-designed listing), both still need a real run.
- [x] **T009** — T1 → T2 escalation rule  
  `P0` `S` · after: T008
  - **Scope shifted twice.** First (2026-08-16) to calibrating T008's provisional confidence-threshold constants (`MIN_CONFIDENT_SCORE`/`MIN_CONFIDENT_LEAD`) against real usage. Then (2026-08-19), the user asked for the best match to always win on Enter/pause, which removed the confidence-gate concept from T008 entirely — there's no threshold left to calibrate. What remains is just the design doc's other T1/T2 boundary rule: input containing whitespace with no T0/T1 hit goes straight to T2, not T3. Downgraded `M` → `S` to match the now-smaller remaining scope.
  - **What "goes to T2" means before T2 exists:** neither T2 (needs the SLM) nor T3 (T010) has a real handler yet, so this task can't route anywhere new — it can only decide, correctly and testably, *which* gap an unmatched submit hits. `command/EscalationTier.kt` adds a pure `escalationTier(input): EscalationTier` (`T2` if the trimmed input contains any whitespace, else `T3`) — trivial by design, since T1 (T008) already had its shot at matching the whole string against app labels, so a miss on multi-word input means the words don't add up to an app name and T3's app-search fallback has no better odds than T1 already didn't. `ShellViewModel.onSubmit()` — already the fallthrough point once T0 and ShellScreen's own T1 check have both missed — picks between two distinct placeholder strings (`"no natural-language understanding yet (T2)"` / `"no command match (T3)"`) instead of the one generic `NO_PIPELINE_YET` line T140 left behind. No `ShellScreen`/UI change: this is purely which placeholder text an already-existing fallthrough writes.
  - **Verified:** 127 unit tests pass (was 120; +7 — 5 for `escalationTier` covering single-word/multi-word/tab-and-newline whitespace/surrounding-whitespace-only/blank, 2 for `ShellViewModel.onSubmit` picking the right placeholder). `assembleDebug` succeeds, lint unchanged (0 errors, 9 warnings, same categories as baseline). Not verified on-device — there's no new UI surface to check, just transcript text on an already-exercised path.
- [x] **T010** — T3 fallback handler + help output  
  `P0` `S` · after: T009, T145
  - The safety net that makes the SLM shippable. Built with T145 on one branch (`feat/T145-T010-help-and-t3-fallback`) — see T145 for why.
  - **T3's "app-name search" half is structurally unreachable, and was deliberately not built.** The design doc defines T3 as "treat as app-name search, else show help", but T1 (T008) has already run by the time anything reaches the fallback, and its ladder bottoms out at `SUBSEQUENCE` — matching any label containing the query's characters in order. That is a *looser* app-name search than T3 would perform, so T3's search can never find something T1 missed. Implemented as help-only rather than as a second search that can't fire; the reasoning is recorded in `command/UnmatchedResponse.kt`'s doc comment so it isn't rediscovered as a "missing feature" later.
  - **Deviation from the task's original wording — a pointer, not the full help text.** "Same text whether the user typed `:help` or fell through" reads well on paper, but a typo is the common case on a daily driver, and dumping the whole command list every time you fat-finger an app name wears out fast. T3 answers with one line: `no match for 'chrmoe' — type :help or ? for commands`. Discoverability is preserved (the pointer names both spellings); the noise isn't.
  - **T2 now degrades here instead of dead-ending.** T009 shipped `"no natural-language understanding yet (T2)"` as a terminal placeholder, which contradicted the root hard constraint that the launcher stay fully usable while the model is absent. Both tiers now end with the same actionable pointer, while keeping a tier-specific lead — `"call mom tonight"` isn't an app-name typo, so saying "no match" about it would misdescribe what happened. T009's classification is unchanged and still the seam T036 will point at the real T2 handler; only the destination for its T2 arm changed.
  - `command/UnmatchedResponse.kt` (`unmatchedResponse(input, tier)`, pure and tested); `ShellViewModel`'s two `*_NOT_BUILT_YET` placeholder constants for T2/T3 are deleted rather than left unused, per this project's precedent on dead code.
  - **Verified:** covered by T145's run above — 176 unit tests pass, `assembleDebug` succeeds, lint unchanged. **Verified on-device (2026-08-19)** alongside T145.
- [ ] **T014** — Log (raw_input, resolved_intent) for every T0/T1 hit  
  `P0` `S` · after: T009
  - **THIS IS TRAINING DATA.** Do it now or section 12 has nothing real to learn from.


## 04 · Notes storage

Room schema, Markdown format, outbox from day one.

- [x] **T018** — Room schema: notes, outbox, aliases  
  `P0` `M` · after: T007
  - Outbox from day one — retrofitting it later is painful.
  - **Entities** (`notes/`): `Note` (mirrors §6.2's document model), `NoteTag`, `OutboxEntry`, `SyncState`. No checklist-items table — §6.2 is explicit that items derive from the Markdown body, one source of truth. **No `userId` column** either: it's the Cosmos partition key, constant for a single user, so the sync layer attaches it at push time rather than storing the same value on every local row.
  - **Design check-in (2026-08-19), four decisions:**
    1. **The outbox coalesces** — keyed by `noteId`, one row per dirty note rather than an append-only mutation log. §4.6's editor autosaves on a debounce, so a note edited offline for a few minutes would otherwise queue hundreds of rows all pushing the same final state, straight into the fixed free-tier RU/s budget §6.3 protects. Two consequences fall out: **no operation column** (create/update/delete is derivable — `rev == null` means the server has never seen it, `deleted` means a tombstone), and `enqueue` uses `IGNORE` rather than `REPLACE` so `baseRev` stays pinned to when the note *first* went dirty, which is the comparison point §7.2's conflict detection needs.
    2. **One database.** Notes and the outbox *must* share one — a transaction can't span two SQLite files, and that transaction is the entire guarantee. Aliases ride along rather than keeping a second connection pool and migration history for nothing.
    3. **Tags in a `note_tags` join table**, not a delimited column mirroring the Cosmos array. A delimited column forces `LIKE '%work%'`, which also matches `#workout` — a wrong-results bug for T026, not a tidiness preference.
    4. **Aliases do not go through the outbox** — resolving a contradiction between two of our own docs. `android/CLAUDE.md` said "every mutation to notes/checklists/**aliases**", but §4.7 and T097 both say aliases are device-local and never synced; an alias outbox row would never drain. The CLAUDE.md wording predated T097 and has been corrected.
  - **The invariant is enforced structurally, not by comment.** `NoteDao`'s raw insert/tag/enqueue operations are `protected`, so callers can only reach `@Transaction` methods that already pair the change with its outbox row. `applyRemote` deliberately *doesn't* enqueue — pulled state is already the server's, and queueing it would push it straight back and make every pull look like a local edit for conflict purposes.
  - **`confirmPushed` guards the one hazard coalescing introduces:** if the user edits a note while its push is in flight, no second outbox row appears (that's the point of coalescing), so clearing the row unconditionally would strand that edit forever. It clears only when the note's `updatedAt` still matches what was actually sent.
  - **Renaming `aliases.db` → `dailydriver.db` needed a real data import, not a Room migration.** Room migrations operate *within* a file; pointing Room at a new name creates an empty database and orphans the old one, silently dropping every alias. `data/LegacyAliasImport.kt` copies the rows in a `onCreate` callback and deletes the legacy file, so it runs exactly once on the transition.
  - **🐛 The instrumented tests caught a real bug, and it was the central one.** `NoteDao.insert` originally used `@Insert(onConflict = REPLACE)`. SQLite implements `INSERT OR REPLACE` as *delete the conflicting row, then insert* — and both `outbox` and `note_tags` hold `ON DELETE CASCADE` foreign keys onto `notes`. So **every re-save silently deleted the note's pending outbox row**, which was then re-created with a freshly captured `baseRev`. The `IGNORE` on `enqueue` was correct and simply unreachable, because no row survived to conflict with. That is exactly the failure this task's own doc comments described as "would quietly make conflicts undetectable" — shipped, and invisible to the compiler. `applyRemote` had the same hazard from the other side: a pull would have wiped a pending local edit's queue entry. Fixed with `@Upsert` (UPDATE on conflict, no delete, no cascade), plus two regression tests that assert row *identity* survives — asserting the row merely exists would still pass against a re-created one.
  - **`exportSchema` enabled, then backed out — tracked as T151.** Turning it on works exactly once: a clean build exports `1.json` fine, and every build after that fails with `AbstractMethodError: …FieldBundle$$serializer… typeParametersSerializers()`. Room 2.8.4's schema bundles use kotlinx-serialization and the version on the KSP processor classpath is incompatible; *reading back* an existing schema is what trips it, which is why the first build looks fine. Reverted rather than left as a booby-trapped build, and the committed `1.json` removed (a schema file that stops regenerating would silently start lying). This was a discretionary addition of mine, not one of the four approved decisions — worth solving before the first real migration.
  - **Caught while writing it:** `SupportSQLiteDatabase.insert` takes the platform `SQLiteDatabase.CONFLICT_*` constants, *not* Room's `OnConflictStrategy` — they don't share values (`REPLACE` is 5 vs 1), so mixing them applies the wrong conflict algorithm rather than failing to compile.
  - **Verified:** **20/20 instrumented tests pass** on an API 36 emulator (`pixel_7_-_api_36`), covering the atomic note+tags+outbox write, coalescing, `baseRev` pinning, the mid-push edit hazard, `#work` vs `#workout` exact matching, tombstone visibility, and the two cascade regressions above. 410/410 unit tests still pass, `assembleDebug` succeeds, lint unchanged (0 errors / 10 warnings). Run instrumented tests with `./gradlew :app:connectedDebugAndroidTest` — note that `adb` isn't on PATH here, but Gradle finds it via `sdk.dir` in `local.properties`, so only a booted device is required.
- [x] **T151** — Re-enable Room schema export (blocked on a kotlinx-serialization conflict)  
  `P2` `S` · after: T018
  - **Root cause was the KSP processor's classloader, not a dependency version.** The T018 note guessed at "the version resolved onto the KSP processor classpath is older than the generated serializers expect" — the Gradle dependency report disproves that half: `room-migration:2.8.4` resolves `kotlinx-serialization-json:1.8.1` cleanly, no conflict, nothing to pin. The older copy arrives outside Gradle's resolution entirely. **KSP1 loads annotation processors into the Kotlin compiler's own classloader**, and `kotlin-compiler-embeddable` carries its own kotlinx-serialization, which shadows the 1.8.1 that Room's `FieldBundle$$serializer` was compiled against — hence `AbstractMethodError` on a method the newer version supplies a default for. Forcing a version on the `ksp` configuration would never have fixed it.
  - **Fixed by `ksp.useKSP2=true`** (one line in `gradle.properties`), which gives the processor its own classloader. Reproduced the failure on KSP1 first so the fix could be shown to fix something, then confirmed a clean build followed by repeated incremental rebuilds — the read-back path that previously failed every time — all pass.
  - **The exported `1.json` is committed**, so a schema change shows up as a reviewable diff.
  - **`SchemaExportTest` guards it going stale.** The failure mode this task warned about isn't the file vanishing, it's the file *staying* while stopping tracking the entities — a lost flag or a Room upgrade that reintroduces the conflict, after which every migration is written against a schema that no longer describes the database. `MigrationTestHelper.createDatabase` validates the exported JSON against the compiled entities, so that fails there instead of at the first real migration. Needs the schemas dir on the androidTest **assets** path (`MigrationTestHelper` reads from the test app's assets at runtime), plus `room-testing`.
  - **Not yet run on-device** — `SchemaExportTest` is instrumented and needs a booted device; it compiles, but has not been executed. Run `./gradlew :app:connectedDebugAndroidTest` to confirm.
  - **New (2026-08-19, found during T018.)** `exportSchema = true` plus `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` exports correctly on a *clean* build and then fails every subsequent one with `java.lang.AbstractMethodError: Receiver class androidx.room.migration.bundle.FieldBundle$$serializer does not define or inherit … typeParametersSerializers()`. Room 2.8.4's schema bundles are serialized with kotlinx-serialization, and the version resolved onto the KSP processor classpath is older than the generated serializers expect. Reading an existing schema back is the failing path — hence "works once".
  - **Why it matters:** without the exported JSON, `MigrationTestHelper` has nothing to verify against, so the first real notes migration can only be checked by hand against a device that happens to hold old data. Worth fixing *before* that migration exists, not after.
  - Likely fix is pinning a compatible `kotlinx-serialization-json` on the KSP classpath, or a Room version bump — but note Room shares the AndroidX release train that already blocks the lifecycle 2.11.0 bump (needs `compileSdk 37` + AGP 9.1.0), so check that before assuming an upgrade is cheap.
- [x] **T019** — Markdown as storage format + hashtag parsing to tags  
  `P0` `M` · after: T018
  - Load-bearing: keeps desktop parity with Markdig, diffs cleanly, no proprietary lock-in.
  - **Scope narrowed by design check-in (2026-08-19): hashtags only.** Checklist parse/render (`- [ ]` / `- [x]`) was offered and deferred — it lands with T023/T024, which own the operations that need it. `notes/TagParsing.kt` is the whole deliverable.
  - **The rules** (all agreed at the check-in): `#` plus Unicode letters/digits/`_`/`-`; **must contain at least one letter**, so `fixed #123` and `#1 priority` stay prose; **lowercased**, since `note_tags` is keyed `(noteId, tag)` and would otherwise hold `#Work` and `#work` separately; the `#` must follow **start-of-input, a line start, or whitespace**, which excludes `example.com#anchor` and `C#`; and **fenced code blocks are skipped** (``` or `~~~`), which is what stops a pasted `#include` or CSS `#fff` from tagging the note.
  - **This is a cross-implementation contract, not a local detail.** §7.3 lists tag parsing among the rules that live in `Notes.Shared` and that "Android reimplements in Kotlin", calling that reimplementation "the seam where bugs will live". So the rules are pinned in **`fixtures/tag-parsing.json`** at the repo root — 26 cases that the Kotlin suite executes now and the C# suite executes at T042. Change the fixture and both implementations; never one alone. Distinct from T070's corpus, which is scoped to sync scenarios (`base`/`local`/`remote`/`expected`).
  - **Known limits, recorded in the fixture rather than fixed:** indented (4-space) code blocks aren't skipped, inline code spans aren't parsed, and `(#work)` doesn't match because `(` isn't whitespace. Each additional rule is another thing C# has to match exactly — the §7.3 divergence risk is the reason to keep the grammar small.
  - **`NoteDao.upsertLocal` no longer takes tags** — it derives them from the body inside the transaction. T018 had them as a parameter, which let a caller supply a set disagreeing with the body; deriving them makes "the body is the single source of truth" structurally true rather than a convention, in the same spirit as that DAO's `protected` raw operations. `applyRemote` still takes tags explicitly, and that asymmetry is deliberate: locally-authored notes need tags immediately (§7.1 is local-first — T026's search would otherwise miss anything unsynced), while synced notes defer to whatever the server sent.
  - **On moving tag assignment server-side** (raised at the check-in as the eventual preference): this stays necessary either way. Under §7.1 a note written offline needs its tags before it ever reaches Azure. The natural shape is the same one `rev` already uses — compute locally, let the server be authoritative once it has seen the note — and the shared fixture is what keeps the two answers identical. Note this is *separate* from §6.5's optional AI auto-categorisation (`POST /api/categorize`, T093), which infers semantic tags rather than extracting literal hashtags.
  - **New test-only dependency: `org.json`** — for reading the shared fixture. Deliberately not `kotlinx-serialization`, which would need the serialization compiler plugin and drags in the very version conflict tracked as T151. `org.json` has no transitive dependencies. The repo-root `fixtures/` directory is put on the unit-test classpath via a `sourceSets` entry in `app/build.gradle.kts`, so tests load it as a resource instead of walking relative paths out of the module.
  - **Verified:** 418 unit tests pass (was 410, +8 — 26 fixture cases run as one parameterised test, plus properties the fixture format can't express and a guard that fails if the fixture ever stops loading), instrumented suite re-run after the DAO change, `assembleDebug` succeeds, lint unchanged.
- [x] **T025** — Note list + open by name  
  `P0` `M` · after: T018
  - **Built with T026 in one pass** — `:apps` (T012) already established that a browse view and its live filter are one thing, not two; splitting these would have meant building a list and then threading a filter back through it.
  - **`:notes [query]` browses, `:note` still writes** (design check-in, 2026-08-20). "note" makes one, "notes" shows yours. `:note <name>` now **opens-or-creates** — `:note standup` twice returns to the same note instead of making a second one wearing the same name, which is what "open by name" meant. Lookup is `COLLATE NOCASE` (you're typing a name from memory) and skips tombstones (naming a new note after a deleted one shouldn't resurrect it).
  - **Two lines per row — title plus a snippet of the body**, unlike the other list views. Blank titles default to a `Thu 20 Aug - 12:24 PM` stamp (T021), so a journal-heavy list is a list of identical timestamps; on-device, all three test notes had the same title and were only distinguishable by their snippet. `noteSnippet` takes the first *non-blank* line, so a body that opens with newlines doesn't preview as empty.
  - **Filtering down to one note auto-opens it** — Enter, or a one-second pause — exactly the affordance `:apps` has (user request, 2026-08-20). **Including the blank-query guard**: bare `:notes` never auto-opens, even with a single note stored, because it means "show me my notes" and you should be able to just look. (Initially built without that guard on a reading of "in any state"; corrected mid-implementation.) Its own `LaunchedEffect` rather than folding into the app one — that resolves to an `AppEntry` and ends in `onAppClick`, so unifying them would mean a nullable-of-either to share four lines. Keyed on the note's *id*, not the `Note`, so an autosave elsewhere changing `updatedAt` doesn't restart the timer for what is still the same note.
  - **The editor expands to full height when opening an existing note**, rather than growing from the ~2-line quick-capture size. Re-reading a journal entry through a six-line window is miserable; new notes keep the small start that T020 exists for.
- [x] **T026** — Local note search  
  `P1` `M` · after: T018
  - **`LIKE`, not FTS.** A personal store is hundreds of rows, and an FTS table means a schema migration — awkward while T151 has schema export broken. Revisit if it ever gets slow.
  - **A lone `#tag` filters by tag; anything else is free text over title and body.** The tag branch hits the indexed `note_tags` table, so it's exact — `#work` won't return `#workout`, which is the bug T018 chose that table to avoid. **The tag grammar isn't re-implemented**: `parseNoteQuery` defers to T019's `parseTags`, so searching for a tag and writing one can't drift. A consequence worth knowing: `#123` isn't a valid tag (T019 needs a letter), so it falls through to text search and finds notes literally containing "#123" — what someone typing an issue number actually wants.
  - **`ESCAPE` on the `LIKE` is load-bearing.** `%` and `_` are wildcards, so unescaped, searching "50%" returns every note and "snake_case" also matches "snakeXcase". `escapeLike` handles the escape character first, since doing it later corrupts terms containing a backslash. All three cases have instrumented tests against real SQLite — this is exactly the class of bug that looks fine until someone searches for a percentage.
  - Case-insensitivity comes from SQLite's `LIKE`, which folds ASCII only, so "CAFÉ" won't match "café". Accepted for a personal tool rather than adding `COLLATE` gymnastics.
  - **🐛 Caught a real bug in the auto-space feature.** `:notes` is the first token that is a strict *prefix* of another (`:note` + `s`). Auto-space fires on completing a token, so typing `:notes` one character at a time hit `:note`, auto-spaced to `:note `, and the following `s` landed after the space — producing `:note s`, a new note titled "s". **`:notes` was literally untypeable.** Fixed by restricting auto-space to `autoSpaceableTokens` — those no other token extends. `:note` loses its auto-space (so `:note standup` needs the space typed, one keystroke) in exchange for a reachable command. Found by T150's registry property test, which failed the moment the token was registered.
  - **Verified:** 464 unit tests (was 434, +30), 31 instrumented (was 21, +10), lint back at 0 errors / 10 warnings after fixing a `ModifierParameter` finding I introduced. **Verified on-device**: three notes created, list renders with distinguishable snippets, `#work` narrows to one, tapping opens it expanded with title and body loaded.


## 05 · Notes editor (Keep-style)

**v3 design.** Google Keep-style touch editor rendered *inline* in the CLI surface. Entry is still a CLI command; the editor is an overlay, not a separate Activity.

- [x] **T020** — Inline editor overlay (Compose, same Activity)  
  `P0` `M` · after: T004
  - No `startActivity`, no window transition, no extra Recents entry. Transcript stays visible behind. On dismiss, restore focus to the input line with IME open.
  - **Built with T021 in one pass** (design check-in, 2026-08-19) — T020 alone renders a box you can't type in, so there'd be no UX to evaluate, and it needs a dismiss path bolted on regardless or the overlay is a trap. Also unblocks T015, whose BACK-closes-the-overlay behaviour is partly handled here for the same reason.
  - **Bottom-anchored, grows with content** — the answer to serving quick capture and long-form journalling from one surface. A one-line note barely disturbs the screen; a journal entry expands upward to a ceiling (420dp) past which the body scrolls internally rather than pushing the transcript off. Matches the bottom-anchored convention `AppListView`, `NotifListView` and the transcript already follow.
  - **A layout swap, not a floating `Box`.** §4.6's requirements are about Activities and windows, both satisfied. A scrim-and-float would sit *over* the transcript; growing in place keeps it legible above and gets "grows with content" free from the parent `Column`'s weights. The editor takes the prompt's place while open, which is what makes §4.6's "returns to the prompt with focus restored" mean anything.
  - **`:note` opens it, `:note standup` pre-fills the title** — resolving the bare-vs-named question T143 explicitly deferred here. Opening an *existing* note by name stays T025's job.
- [x] **T021** — Title + body fields with debounced autosave  
  `P0` `M` · after: T020
  - **Autosave is the real save.** Debounce to Room continuously so process death / a call / a swipe-away loses nothing. The buttons mean *done*, not *commit*.
  - **Body-first**: focus lands in the body, because `:note` should mean "start writing", not "start naming". The title is an optional field above it.
  - **A blank title becomes a `Wed 19 Aug - 14:32` stamp** (user's answer at the check-in, in preference to deriving from the first line). Materialised into the `title` column at save time, so T025 and the C# side just read the column. **Derived from `createdAt`, never `now()`** — a journal entry begun at 23:58 and finished at 00:05 keeps the date you started, and a stable answer stops the title silently drifting on every autosave. Formatting reuses T146's `statusLineText`, so a note title and the status line can't disagree about how this app writes a date.
  - **500ms debounce, plus an immediate flush** on close and on Activity pause — §4.6 promises a killed process loses nothing, and a pending debounce would break exactly that.
  - **Nothing is persisted until there's content.** No row is created on open, so an accidental `:note` leaves no trace and T025's list never fills with blanks. `NoteDraft.persisted` tracks whether a row exists, which is what tells discard the difference between "forget this" and "soft-delete a real note".
  - **✓ keeps, ✗ discards** (check-in) — genuinely different, rather than §4.6's literal `[x]`/`[check]` which autosave would make identical. ⚠️ **Discard is a soft delete, and asks first when there's content to lose.** T138's undo snackbar is `after: T023, T024` and doesn't exist yet, so the one case that could destroy writing confirms; the row survives as a tombstone either way (§7.1). **That confirmation should be removed once T138 lands** — confirmations on reversible actions are pure friction.
  - **BACK keeps rather than discards.** It's the ambiguous gesture — people mean "go back", not "throw this away" — and autosave has already written the text, so treating it as ✓ is both the safe and the truthful reading.
  - `editor/NoteDraft.kt` is the pure, tested half (16 tests: blank-vs-content, the stamp's zone/locale/12h handling, createdAt stability, conversion to a row). `NoteEditorViewModel` owns the debounce; `NoteEditorView` is the Compose surface. `notes/NotesRepository.kt` finally exists now that T018's DAO has a caller — it also holds `is24Hour()`, since it's already the layer with a `Context` and the alternative was handing the ViewModel supplier lambdas to read one boolean.
  - **🐛 Bug caught by on-device verification, invisible to every other check.** Focus restoration used `LaunchedEffect(Unit)`, which fires once at startup and never again — so closing the editor left the prompt unfocused with the keyboard down, breaking the one behaviour T020's own task description names ("On dismiss, restore focus to the input line with IME open"). Re-keyed on `editor.isOpen`. Compiles, unit tests, lint and the instrumented suite all passed the broken version; only looking at the screen found it.
  - **Verified on-device (2026-08-20, emulator, driven via `adb`)** — screenshots at each step: `:note journal` opens with the title pre-filled; bare `:note` shows the `untitled` placeholder; typing lands in the body without touching the title; the editor occupies ~2 lines for a short note with the transcript and status line still visible; ✗ on a note with content raises the confirmation row; cancelling returns to editing; ✓ closes with the prompt refocused and the IME up. Persistence confirmed by pulling the WAL and finding both notes' titles and bodies in it. 434 unit tests pass (was 418, +16), 21 instrumented pass, `assembleDebug` succeeds, lint unchanged (0 errors / 10 warnings).
  - **Header redesigned after review (2026-08-20, user request):** ✗ hard left, title centred, ✓ hard right. The two actions had been adjacent at the right edge, which put a destructive control a thumb-width from the one you press constantly. Opposite edges make the pairing read as leave/commit and put the whole title between them. The actions also became 48dp touch targets — a bare `Text` with 10dp padding is a ~20dp hit area, which is unpleasant to hit and one of these deletes your note.
  - **⚠️ The first attempt at that redesign ANR'd**, from `Modifier.fillMaxWidth()` on the placeholder inside the title field's `decorationBox`. A `singleLine` `BasicTextField` measures its inner content with *unbounded* width so it can scroll horizontally, and `fillMaxWidth` against an infinite constraint hangs the measure pass. Replaced with a wrapping `Box(contentAlignment = Center)`, which sizes to its children and needs no width constraint. **Causation is inferred, not proven** — the environment had two emulators running at the time — but the construct is a genuine hazard regardless and the fix is correct on its own terms.
  - **Not yet exercised**: long-form scrolling past the 420dp ceiling, and the pause-flush path (`onPause` → immediate write) — both need a longer real session than an `adb`-driven one.
- [x] **T022** — Top-right action bar: close, confirm, overflow  
  `P0` `M` · after: T020
  - `[x]` closes, `[check]` confirms and closes. Overflow: delete, convert note↔checklist, hide checked items.
  - **Closed as delivered by T020/T021, with the overflow container moved to T024** (design check-in, 2026-08-20). No new code was written for this task; the assessment is the deliverable.
  - **Close and confirm shipped with the editor**, because an overlay needs a way out on day one. Two recorded deviations from §4.6's literal spec, both user decisions at the T020/T021 check-ins: the actions sit at **opposite edges rather than both top-right** (adjacent put a destructive control a thumb-width from the one you press constantly), and **`×` genuinely discards rather than merely closing** ("make them genuinely different: ✓ keeps, ✗ discards"), with `DiscardConfirmation` standing in until T138's undo lands.
  - **Delete is already live** — that second deviation is what delivers it. `confirmDiscard()` soft-deletes through `notes.discard()`, tombstone and outbox row included. §4.6 filed delete under the overflow; the check-in gave it a dedicated affordance instead, which is the same capability by a better route.
  - **The overflow's two remaining items are T024's title** — convert note↔checklist and hide-checked-items. Building the container now would ship a menu whose only working entry duplicates `×`, and whose real shape can't be judged until it holds its actual contents. T024 builds the menu along with what goes in it.
  - **Unblocks T027**, which needs only the close/confirm actions that exist.
- [x] **T023** — Checklist items: add, check, delete, drag-reorder  
  `P0` `L` · after: T020, T019
  - Keep behaviour: checking an item moves it *below* the unchecked ones. Serialises to `- [ ]` / `- [x]` lines.
  - **Built with T024 in one pass** — T024 owns conversion *into* this surface and the overflow row that reaches it, so shipping T023 alone would have meant a checklist you could edit but never create.
  - **No item list exists.** `notes/ChecklistParsing.kt` derives items from the body on every read and renders straight back to it, so §6.2's one-source-of-truth rule holds structurally rather than by discipline. Re-parsing per read is a few string ops on a grocery list; buying consistency with that is obviously the right trade. Item ops in the ViewModel all go through the body.
  - **A marker line is an item even with empty text.** `- [ ]` alone is the row you're about to fill in, and dropping it at parse time would make new items vanish as you created them. Empties are dropped on close instead, so nothing half-typed is stored.
  - **Any non-blank line is an unchecked item.** That's what makes T024's note→checklist conversion a parse rather than a special case, and it means a body synced from a desktop that wrote plain prose still opens as something you can tick off.
  - **`fixtures/checklist-parsing.json` — a second cross-language corpus**, same pattern as T019's. §7.3 doesn't enumerate checklist parsing, but root `CLAUDE.md` calls `- [ ]`/`- [x]` "the wire and storage format for both Android and desktop" and §4.6 has both UIs rendering "two views of one Markdown body" — the same drift seam by another name. 18 parse cases plus render, conversion and normalize sections.
  - **The unchecked-above-checked rule is an invariant re-applied after every mutation**, not a one-off move on check — so drag can't leave the list in a state ticking a box could never produce. Stable partition, so a newly checked item lands at the *top* of the checked group: the smallest move that satisfies §4.6.
  - **🐛 Three bugs that only a device could find**, all invisible to the compiler, 503 unit tests and lint:
    1. **Drag didn't work at all.** It was first built as long-press-anywhere-on-the-row, which loses to the `BasicTextField` inside that row: a text field claims the long-press for its own selection handles before a parent gesture detector sees it. On-device, long-pressing an item raised the text cursor. Fixed with a dedicated `≡` handle that owns the gesture — and since nothing competes for it any more, a plain drag rather than a long-press one.
    2. **The overflow row wasn't on screen for any note opened from `:notes`.** `fillMaxHeight()` on the body took the whole column and left the row below it zero height. `weight(1f)` instead, so the row claims its height first.
    3. **`+ item` produced a row you couldn't type into.** Requesting focus in a `LaunchedEffect` on a row composed that same frame hits `FocusRequester is not initialized` — which *logs and does nothing* rather than throwing, so the row appeared, the keyboard didn't, and the next thing typed went nowhere. A `withFrameNanos { }` before the request lets the modifier node attach.
  - **🐛 A fourth, found by the user in ordinary use: you couldn't type a space.** Item text is edited *through the body* — the editor renders to Markdown and re-parses on every keystroke, which is exactly what keeps the body authoritative — and `parseChecklist` trimmed each item, so the space was stripped before the next character arrived. "peanut butter" came out "peanutbutter". Trailing whitespace is now preserved at parse time and tidied on close instead (`cleaned`, replacing `withoutEmpties`), where it costs nothing because the typing has stopped. The regression test types the phrase one character at a time through a full render/parse cycle per keystroke — the loop the bug actually lived in. A *leading* space still can't be typed, recorded as a known limit in the fixture: it carries no meaning in a list item and, unlike a trailing one, never blocks the text that follows.
  - Also fixed: the drag distance used a hardcoded `40.dp` row height against rows that lay out around 52.dp, so a drag committed about a third fewer positions than the finger travelled. Measured with `onSizeChanged` instead.
  - **Verified on-device** (Pixel 5, API 33, `adb`-driven, screenshots at each step): prose → checklist; ticking sinks the item below the unchecked ones, struck through and dimmed; hide/show checked with the label flipping; drag by the handle moving exactly one position; `+ item` landing focused with the keyboard up; per-row delete; and the whole list surviving close/reopen with its type. 508 unit tests (was 476, +32), 32 instrumented, lint unchanged at 0 errors / 10 warnings. **The space fix landed after that device pass and has not itself been rechecked on-device** — it's covered by unit tests only.
- [x] **T024** — Hide-checked-items + convert note ↔ checklist  
  `P1` `M` · after: T023
  - Conversion flips `type` and reformats the body; must round-trip without losing content.
  - **Also owns the editor's overflow menu** (moved here from T022, 2026-08-20). Both of this task's features live in that menu and are the only things left to put in it — delete already has its own affordance via `×`. Building the container here means it's designed against real contents rather than as an empty shell.
  - **Conversion strips the markers** (design check-in, 2026-08-20). `note → checklist → note` is exactly lossless. `checklist → note → checklist` returns every item's text but comes back unchecked — prose has nowhere to record a checkbox, so "round-trip without losing content" is satisfied for the words, which is the only reading the destination format allows. The alternative considered was leaving `- [ ]` in the body: perfectly lossless, but hands back a "note" full of the litter you converted away from, and contradicts §4.6's "conversion reformats the body". Recorded as a test, not just a comment.
  - **Hide-checked is transient**, resetting when the note closes (check-in). Persisting it would need a new column, schema v2, and a §6.2 document-model change the C# side must match — a lot of moving parts for a display toggle, and one whose absence you'd notice within a day of daily-driving.
  - **`ChecklistDisplay.kt` is where the interesting bug would have lived.** With checked items hidden, the third row you can *see* isn't the third row that *exists*, so an untranslated tap ticks or deletes the wrong item — silently, on rows you can't see. Extracted from the ViewModel as pure functions and tested directly (including a property asserting every visible row resolves back to the item it's showing) rather than left as private helpers needing a coroutines dispatcher to reach.
  - **Not a popup menu.** `⋯` opening a floating list is the conventional answer, but this surface is a terminal where everything is a labelled line you tap, `DiscardConfirmation` already set the inline-row precedent, and two actions don't earn being hidden behind a tap that reveals them. Labels name the destination (`→ checklist`), not the operation, so the direction isn't a guess.
  - **🐛 T151's schema guard failed the first time it ever ran**, which is the whole reason it was written. `SchemaExportTest` threw the *same* `AbstractMethodError: …typeParametersSerializers()` as T151 — but at runtime in the test APK, an entirely separate classpath from the KSP one KSP2 fixed. `MigrationTestHelper` reads the schema through `room-migration`, whose serializers are compiled against kotlinx-serialization 1.8.1, while a transitive BOM constrained `-core` to 1.7.3 here, where that method is still abstract rather than defaulted. Fixed by forcing core to 1.8.1 on the test configurations — `force` rather than a plain dependency, since the pin is a BOM *constraint* an ordinary declaration doesn't outrank, and the bare `-core` module doesn't resolve standalone. Note this is the fix T018 originally *guessed* at ("pinning a compatible kotlinx-serialization-json on the KSP classpath") — wrong classpath, right idea, and it only became true on the one T151 never touched.
  - **Verified on-device** alongside T023 — see that entry.
- [ ] **T027** — Keyboard shortcuts so the editor stays keyboard-finishable  
  `P0` `S` · after: T022
  - `Esc` closes, `Ctrl+Enter` confirms, `Tab`/`Enter` walks items. A note started at the prompt must be finishable without reaching for the screen.
- [ ] **T138** — Undo snackbar for destructive actions  
  `P1` `M` · after: T023, T024
  - Autosave removed the `:w` safety net — a mis-tap is now instantly durable and syncs. Undo replaces it. Soft-delete + timer, reusing the tombstone mechanism.
- [ ] **T139** — Tag chips row + inline #hashtag parity  
  `P1` `M` · after: T021, T019
  - Chips and inline `#work` must produce the same `tags` array — one operation, not two sources of truth.


## 05a · Tasks (`:task` / `:tasks`)

**Added 2026-08-21 (user scope addition).** Due-dated tasks, creatable from the prompt and pulled out of existing checklists. Settled at a design check-in the same day, and written up in [ADR-0006](adr/0006-tasks-as-dated-checklist-items.md) plus §4.8 *before* any code, which is the first time that ordering has been followed rather than backfilled.

**The whole feature rests on one decision: a task is a checklist item with a due date, authored in the note body and nowhere else.** No `NoteType.TASK`, no change to §6.2's document model. §6.2 already forbids storing checklist items outside the body, and a task that could be edited both as a table row and as a `- [ ]` line would be two copies of one fact. The payoff is that the two creation routes, `:task` and dating an existing item, produce byte-identical lines, so there is no way to end up with two classes of task that behave differently. Sync also gets it for nothing, since a due date is just body text moving through the existing outbox and conflict machinery.

**↻ Reversed within a day: querying now goes through a derived index** ([ADR-0007](adr/0007-derived-task-index.md), superseding [ADR-0006](adr/0006-tasks-as-dated-checklist-items.md); user call, 2026-08-21). ADR-0006 had `:tasks` scanning every note body and accepted "a due date is not queryable" as a fair price at personal scale. Reviewed before building `:tasks`, that judgement doesn't survive contact with the actual requirement: **"what is due today" is the entire reason the feature exists**, and it had been put on the slowest possible path. The objection was never throughput, it was that the primary query had no efficient expression at all — "it's fast enough" is not an answer to "this cannot be indexed."

**What reversed is only the "no table" half.** A task is still a dated checklist item, still authored in the body, still one source of truth; the `tasks` table is an *index*, written only by deriving it from a body, rebuildable from scratch, and never synced. That is the escape hatch ADR-0006 itself named in advance, and it is **the same shape as `note_tags`**, which already parses hashtags out of bodies and indexes them for exact querying — proven in this codebase rather than novel.

**New failure mode to design against:** a stale index means `:tasks` lies. Bounded by deriving inside the note's own transaction, keeping a rebuild available, and letting the body win in any disagreement. Belongs in instrumented tests, like the outbox.

- [x] **T153** — Due-date parsing + shared fixture corpus  
  `P1` `M` · after: T023
  - `@YYYY-MM-DD` inside a checklist line, alongside the `#hashtags` §4.6 already puts there. Date-only, so timezones stay out of the wire format and out of sync, where clock skew is already a hazard (§7).
  - **`fixtures/due-date-parsing.json` is written and is the contract** — 12 stored cases, 3 render cases, 21 input cases. Third corpus in the §7.3 pattern, after tags and checklists.
  - **Its two halves are not equally binding, and the file says so.** `storedCases` define what is persisted and every client must implement them. `inputCases` define shortcut spellings that never reach storage, so a client can skip them and still be correct, just less pleasant to type into.
  - **Shortcuts are input sugar, never a storage format** (check-in): `@today`, `@tomorrow`, `@next week`, `@next month`, `@next year`, `@t+N` days, `@w+N` weeks, `@m+N` months, `@y+N` years. All rewritten to ISO before the body is written, so a note means the same thing next week as today, and C# only ever parses a literal date.
  - **Month arithmetic clamps rather than overflowing** — 31 Jan `m+1` is 28 Feb, and 29 Feb `y+1` is 28 Feb. Noted in the corpus as non-reversible, since clamping loses the original day.
  - **Garbage is never silently dropped.** An `@` token that is neither a valid date nor a known shortcut stays as literal text, so `email jack@example.com` and `ask @bob` survive untouched.
  - **Spaced shortcuts only resolve at end of line**, which is why every one of them has a hyphenated form (`@next-week`). Recorded as a known limit.
  - **🐛 The rule changed from first-date-wins to last-date-wins, and it had to.** A round-trip property test caught it: rendering writes the due token at the *end* of the line, so on a line carrying two dates a first-wins parse picks one date, renders the other one last, and the two swap places on every parse/render cycle. Since the body is the live editing buffer, that is a line that visibly rewrites itself as you type — the same class of bug T023 shipped with trailing spaces. Last-wins makes parse and render an identity. It also reads better: appending a date to a line that already has one is how you change your mind about when something is due, so the one just typed should win. Fixture rule and case updated alongside the implementation.
  - **`parse ∘ render = identity` is now written into the fixture as a rule**, not just tested, since it is the property the whole body-as-buffer design depends on and the one a future change is most likely to break silently.
  - **`cleaned()` now keeps an item that has a date but no text.** It previously dropped anything blank, which would have deleted a row the moment you set its date before typing what it was.
  - Verified: 533 unit tests (was 508), 0 failures; lint unchanged at 0 errors / 10 warnings. **Instrumented tests not run and not needed** — `ChecklistItem` is derived from the body, not a Room entity, so no schema, DAO or migration surface changed.
- [x] **T154** — Due dates in the checklist editor  
  `P1` `M` · after: T153, T024
  - Add, change and clear a date on any checklist row. The date shows on the row; the token stays in the body.
  - Reuses the T023 pipeline exactly: parse the body, transform the item, render back. Nothing new is cached beside the body.
  - **No date-picker UI, because the row is already a text field.** You type `@tomorrow` into the item and it becomes a real due date. That keeps the keyboard-first premise intact and adds nothing to a row that is already carrying a handle, a checkbox and a delete.
  - **Shortcuts resolve on focus loss, never on keystroke** — which is how the T023 trap gets sidestepped rather than fought. Rewriting text under a live cursor is exactly what broke typing before, and this field is a `BasicTextField(String)` whose cursor snaps to the end on any external change. Resolving on the way *out* of a row means the text only ever changes while nobody is typing into it.
  - **Also resolved on close**, because the last row you type into may never lose focus — you type `@fri` and hit ✓. A shortcut stored verbatim would be a task with no due date that looks like it has one.
  - **A no-op when nothing resolves**, deliberately. Commit fires on every focus loss, so returning a changed string would dirty the body and fire an autosave just for tabbing through a row.
  - **Tapping the date clears it**, and that is the only way to remove one: the token is lifted out of the text on parse, so it can't be backspaced away in the field. Changing a date needs no affordance at all — type another `@...` and T153's last-wins takes it.
  - **`shortDate` extracted so there is one definition of how this app writes a date.** The status row, the blank-note title stamp and a due date all render through it. `android/CLAUDE.md` already made this point for note titles; a second formatter that happens to agree today is one that drifts later.
  - **`today` / `tomorrow` as words, the date otherwise.** "What is due today" is the question the whole feature exists to answer (ADR-0007), so making the reader scan a column of `Fri 21 Aug` for it is work the label should be doing. They are also shorter than the date they replace, on a row with little width to spare.
  - **Overdue is coloured but still shows its date.** Colour is never the only signal, and "how late" is the thing you actually need to judge.
  - **🐛 Caught by its own test:** the two-word tail replacement swallowed the separating space, so `move house @next week` resolved to `move house@2026-08-28`. The single-word path was fine; only the tail branch re-assembled the string by hand.
  - Verified: 549 unit tests (was 533), 0 failures; lint unchanged at 0 errors / 10 warnings. **Not yet verified on-device** — focus-loss commit and the tap-to-clear target are exactly the kind of thing that has broken on hardware three times in this editor already (T023).
- [ ] **T155** — `:task` command  
  `P1` `S` · after: T153
  - `:task buy milk @fri` appends one dated line to a note titled `tasks`, opening or creating it by name. That is T025's existing `byTitle` path, so there is no new storage code.
  - Registers in T140's command table, which carries its own help text, so `:help` cannot drift out of step.
- [ ] **T156** — `:tasks` list view  
  `P1` `M` · after: T153, T155
  - Queries the T157 index, grouping results overdue / today / upcoming, soonest first. An indexed lookup on a date column, not a scan.
  - **Ticking from this list writes back to the source note body**, because that is the only place the item is authored. The index follows from that write rather than being updated beside it.
  - Reuses the `:notes` and `:apps` list surface rather than inventing a third one.

- [ ] **T157** — Derived `tasks` index table  
  `P1` `M` · after: T153, T151
  - **Added by the ADR-0007 reversal.** A Room table carrying note id, item position, text, checked state and an **indexed `due` column**, so `:tasks` is a query rather than a scan.
  - **An index, not a store**, enforced structurally the way the outbox already is: only ever written by deriving from a note body, in the same transaction as the note write. Nothing may insert a task directly, mirroring how `NoteDao.upsertLocal` derives tags rather than accepting them.
  - **Never synced.** No outbox row, no document-model entry, no C# contract — each client derives its own index from bodies it already has. Note this is an explicit exception to the outbox rule in `android/CLAUDE.md`, on the same grounds as `note_tags`: derived data isn't mutated, it's recomputed.
  - **Rebuildable from scratch**, so a wrong index is fully repairable by dropping and re-deriving. Ship the rebuild alongside the table, not later.
  - **⚠️ Schema change, so it needs a migration — and `exportSchema` is still off (T151).** Land T151 first so the migration is checked rather than assumed; that guard exists precisely for this.
  - **Instrumented tests required**, not optional. A stale index is invisible to unit tests and to the compiler, exactly like the `INSERT OR REPLACE` cascade T018 shipped and only instrumented tests caught.

**Not in scope, and each would need its own token both clients agree on:** recurrence, a start date distinct from a due date, priorities, and time of day. Deliberately starting with one token and date-only values.


## 06 · SLM spike (GO/NO-GO)

**Timeboxed.** Deliberately early: answers the highest-uncertainty question while changing course is still cheap. Days, not weeks.

- [ ] **T028** — Define InferenceEngine interface  
  `P0` `S`
  - Decouples MediaPipe now, enables LiteRT-LM later.
- [ ] **T029** — Add MediaPipe dependency, init GPU backend  
  `P0` `M` · after: T028
  - LoRA requires `backend='gpu'`.
- [ ] **T030** — Acquire + convert base model to .task  
  `P0` `M` · after: T029
- [ ] **T031** — adb push model, load, first successful inference  
  `P0` `M` · after: T030
  - `/data/local/tmp/llm/` during development.
- [ ] **T032** — Prompt template with inline schema + few-shot  
  `P0` `M` · after: T031
  - Low temperature (~0.1). Extraction, not creative writing.
- [ ] **T033** — JSON extraction (first balanced brace span)  
  `P0` `S` · after: T032
- [ ] **T034** — JSON repair pass (fences, trailing commas, unquoted keys)  
  `P0` `M` · after: T033
  - No grammar-constrained decoding in MediaPipe — defend in layers.
- [ ] **T035** — Schema validation + confidence threshold, drop to T3  
  `P0` `M` · after: T034
- [ ] **T036** — Wire T2 into the command pipeline  
  `P0` `M` · after: T035
  - SLM never on the fast path.
- [ ] **T037** — GO/NO-GO: LoRA viability spike on target device  
  `P0` `L` · after: T031
  - **DECISION GATE, TIMEBOXED.** Does a LoRA adapter load on the GPU backend with the chosen model? If no: fall back to few-shot and reframe the portfolio narrative. Do not start dataset work until answered.
- [ ] **T038** — Baseline latency + peak memory measurement  
  `P0` `M` · after: T031
- [ ] **T039** — DECISION: Gemma-2 2B vs Gemma-3 1B — record rationale  
  `P0` `S` · after: T037
  - Gemma-3 1B LoRA support is not clearly documented; Gemma-2 2B is. Let measurement decide, then write it down.


## 07 · Backend (Functions + Cosmos)

**T042 is the only hard blocker for section 08 — build it first within this section.**

- [ ] **T042** — Notes.Shared: POCOs, DTOs, tag parsing, merge rules  
  `P0` `L`
  - Referenced by both Desktop and Functions — single source of truth, cannot drift. **Unblocks section 08.**
- [ ] **T040** — Entra ID app registration: desktop public client + Android  
  `P0` `M`
  - One registration, two platform configurations.
- [ ] **T041** — Create Cosmos DB account — PROVISIONED, free tier opted in  
  `P0` `S`
  - ⚠ **IRREVERSIBLE.** Free tier is opt-in *at creation only* and is NOT available on serverless-capacity accounts. Choosing serverless forfeits it permanently.
- [ ] **T043** — Notes.Functions scaffold: isolated worker, Program.cs, DI  
  `P0` `M` · after: T042
  - ⚠ **ISOLATED WORKER ONLY.** In-process support ends 2026-11-10. Older tutorials show in-process — treat as suspect.
- [ ] **T044** — JWT validation middleware  
  `P0` `M` · after: T040, T043
- [ ] **T045** — GET /api/notes (since rev, tag filter)  
  `P0` `M` · after: T043, T041
- [ ] **T046** — GET/PUT/DELETE /api/notes/{id} with If-Match ETag  
  `P0` `L` · after: T045
- [ ] **T047** — POST /api/sync/batch (batch push + pull)  
  `P0` `L` · after: T046
  - Protects the free-tier execution grant when a client returns from a long offline stretch.
- [ ] **T048** — Server-side monotonic rev assignment  
  `P0` `M` · after: T046
  - Clock-independent ordering. Clients never invent a rev.
- [ ] **T049** — Soft delete + tombstone purge (timer trigger, 90d)  
  `P1` `M` · after: T046
- [ ] **T050** — $0 budget alert + manual throughput pinned at 1000 RU/s  
  `P0` `S` · after: T041
  - **Manual**, not autoscale — autoscale bills above the free grant the moment it scales.
- [ ] **T051** — Blob Storage container + upload model/LoRA artifacts  
  `P1` `M`
  - Hosts the SLM artifacts for the Android first-run download.


## 08 · WPF desktop

**The .NET job-hunt artifact.** Independent of the SLM. If the schedule slips, protect this.

- [ ] **T052** — Notes.Desktop project: .NET 10 WPF + Generic Host + DI  
  `P0` `M` · after: T042
- [ ] **T053** — EF Core + SQLite context and migrations  
  `P0` `M` · after: T052
  - Offline source of truth; the most-quoted .NET data skill.
- [ ] **T054** — MVVM scaffolding (CommunityToolkit.Mvvm)  
  `P0` `M` · after: T052
  - Source-generated observables; no hand-rolled INotifyPropertyChanged.
- [ ] **T055** — Two-pane shell: note list + editor  
  `P0` `L` · after: T054
- [ ] **T056** — Note list VM: sort by updatedAt, category multi-select filter  
  `P0` `M` · after: T055
  - Filter computed locally from SQLite — instant, no round trip.
- [ ] **T057** — Markdown editor + Markdig live preview  
  `P0` `L` · after: T055
- [ ] **T058** — Checklist checkboxes rewrite underlying `- [ ]` line  
  `P1` `M` · after: T057
- [ ] **T059** — Light/dark themes bound to system preference + override  
  `P1` `M` · after: T055
  - Asymmetry with Android's dark-only is intentional.
- [ ] **T060** — MSAL.NET public client, PKCE, encrypted token cache  
  `P0` `L` · after: T040
  - No client secret — a desktop binary cannot keep one.
- [ ] **T061** — HttpClient via IHttpClientFactory + Polly retry/backoff  
  `P0` `M` · after: T060
- [ ] **T062** — Sync status affordance (last sync, pending count, conflicts)  
  `P1` `M` · after: T055
  - An offline-first app that hides its sync state feels broken rather than offline.
- [ ] **T063** — MILESTONE: desktop usable offline as standalone notes app  
  `P0` `S` · after: T057, T053
  - 🏁 Demonstrable on its own. Depends on neither the SLM nor the backend.


## 09 · Sync engine

Two offline replicas. Second-riskiest part after the SLM.

- [ ] **T064** — Outbox table + transactional write on every mutation  
  `P0` `L` · after: T053
  - Same transaction as the entity change — that is what makes interrupted sync safe to resume.
- [ ] **T065** — SyncService as IHostedService (timer, connectivity, on-demand)  
  `P0` `L` · after: T064, T061
- [ ] **T066** — Push/pull cycle against /api/sync/batch  
  `P0` `L` · after: T065, T047
- [ ] **T067** — Conflict detection: baseRev vs server rev  
  `P0` `L` · after: T066, T048
- [ ] **T068** — Conflict copy materialisation as separate note  
  `P0` `M` · after: T067
  - Never silently lose the losing edit. Title: `<title> (conflict, <device>, <timestamp>)`.
- [ ] **T069** — Android SyncWorker (WorkManager) equivalent  
  `P0` `L` · after: T018, T047
- [ ] **T070** — Shared JSON fixture corpus (base/local/remote/expected)  
  `P0` `M` · after: T042
  - The seam where Kotlin/C# drift will live. Commit once, execute from both test suites.
- [ ] **T071** — xUnit sync tests against fixture corpus  
  `P0` `M` · after: T070
- [ ] **T072** — Android unit tests against the SAME fixture corpus  
  `P0` `M` · after: T070
  - Disagreement fails a test instead of quietly corrupting your notes.
- [ ] **T073** — All sync scenarios in design doc §8 pass  
  `P0` `M` · after: T071, T072
  - 🏁 Pass/fail gate: offline edit one side, offline edit both, delete vs edit, interrupted push, clock skew, cross-implementation agreement.


## 10 · Pipelines & IaC (AZ-400 50–55%)

Deliberately **after** there is code to build. Work through it alongside AZ-400 study, not upfront.

- [ ] **T111** — Create Azure DevOps org + project, enable free tier billing link  
  `P0` `S`
  - ⚠ The free parallel-job grant for private projects is **not automatic** — link the org to an Azure subscription and configure billing. Then: 1 job, 1,800 min/month, 60 min max per job. Split: GitHub holds the repo, Azure Pipelines does CI/CD.
- [ ] **T112** — Service connection: Azure Pipelines → GitHub repo  
  `P0` `S` · after: T111, T108
- [ ] **T113** — Service connection to Azure using workload identity federation  
  `P0` `M` · after: T111
  - Federation, **not** a service principal secret. Nothing to rotate or leak. *AZ-400 D4.*
- [ ] **T114** — Bicep: all Azure resources as IaC  
  `P0` `L` · after: T113
  - Cosmos (provisioned + free-tier flag), Storage, Function App, App Insights, Key Vault, App Configuration. **Double win:** AZ-400 D3 *and* it de-risks T041 by making the irreversible setting declarative.
- [ ] **T115** — Bicep what-if validation stage in pipeline  
  `P1` `M` · after: T114
  - `az deployment what-if` on PR so infra changes are reviewable before they apply.
- [ ] **T116** — Multi-stage YAML pipeline: build + test .NET  
  `P0` `L` · after: T112
  - build → test → deploy-dev → (approval) → deploy-prod. *AZ-400 D3 centre of gravity.*
- [ ] **T117** — Publish code coverage + enforce a threshold  
  `P1` `M` · after: T116, T071
  - Gate on the sync engine specifically — that's where a silent regression costs real notes.
- [ ] **T118** — Pipeline stage: build Android APK  
  `P1` `M` · after: T116
  - Gradle build + unit tests incl. the fixture corpus. Signing keys from Key Vault, never the repo.
- [ ] **T119** — Azure Artifacts feed: publish Notes.Shared as a versioned NuGet package  
  `P1` `M` · after: T116, T042
  - *AZ-400 D3 package management.* Forces real thinking about contract versioning between client and server.
- [ ] **T120** — Consume Notes.Shared from the feed in Desktop + Functions  
  `P2` `M` · after: T119
  - Optional — project references are simpler for a solo monorepo. Do it for the exam practice, then note in an ADR that you'd keep project references at this size.
- [ ] **T121** — Environments (dev/prod) with approval gates  
  `P0` `M` · after: T116
  - Approving your own deploy feels silly solo — do it anyway, the mechanics are the exam content.
- [ ] **T122** — Deploy Functions via slot swap (blue/green)  
  `P1` `M` · after: T121
  - Cheapest real implementation of a progressive-exposure strategy.
- [ ] **T123** — PR validation pipeline (build + test on every PR)  
  `P0` `M` · after: T116, T108
  - Wired to T108's branch protection so main cannot take a red build.
- [ ] **T124** — Extract reusable YAML templates  
  `P2` `M` · after: T116, T118
  - *AZ-400 D3: maintain pipelines.*
- [ ] **T125** — Pipeline caching + watch the 1,800 min/month budget  
  `P1` `M` · after: T116
  - Cache NuGet and Gradle. If you blow the budget, move Android to GitHub Actions (2,000 free min/month) and keep .NET on Azure Pipelines.


## 11 · Security & observability (AZ-400)

Security/compliance (10–15%) + instrumentation (5–10%). Also fills a genuine gap: the design had no observability at all.

- [ ] **T126** — Key Vault + variable groups linked to Key Vault  
  `P0` `M` · after: T114
  - No secrets in YAML, none in the repo. *AZ-400 D4.*
- [ ] **T127** — Managed identity: Functions → Cosmos (drop connection strings)  
  `P0` `M` · after: T114, T045
  - System-assigned identity + Cosmos RBAC data-plane roles. Strictly better practice *and* exam surface.
- [ ] **T128** — Enable secret scanning + push protection  
  `P0` `S` · after: T108
  - Catches the mistake before it reaches history, where it's expensive.
- [ ] **T129** — Dependabot / dependency scanning  
  `P1` `S` · after: T108
  - NuGet + Gradle updates and vulnerability alerts.
- [ ] **T130** — CodeQL SAST on PR  
  `P1` `M` · after: T123
  - Static analysis for C# and Kotlin.
- [ ] **T131** — Application Insights instrumentation for Functions  
  `P0` `M` · after: T114, T043
  - Fills a real hole — the HLD had zero observability. Sampling on; keep note **content** out of telemetry.
- [ ] **T132** — Structured logging via ILogger with semantic properties  
  `P1` `M` · after: T131
  - Structured properties, not string interpolation — otherwise T135 has nothing to query.
- [ ] **T133** — Custom telemetry: sync conflict rate, sync duration, outbox depth  
  `P1` `M` · after: T131, T067
  - Conflict rate tells you empirically whether the conflict-copy policy was the right call.
- [ ] **T134** — Android SLM telemetry (local-only, privacy-preserving)  
  `P2` `M` · after: T036
  - Latency, tier-hit distribution, abstention rate. **Metrics only — never note or command content.** On-device or explicitly opt-in.
- [ ] **T135** — KQL queries + Azure dashboard  
  `P1` `M` · after: T132, T133
  - Writing KQL by hand is the part people skip and the exam tests.
- [ ] **T136** — Alerts: function failures, 5xx rate, RU throttling (429s)  
  `P1` `M` · after: T131
  - You're pinned at 1000 RU/s — a sync bug that hammers Cosmos should page you, not silently fail.
- [ ] **T137** — Feature flags via App Configuration — gate the T2 SLM tier  
  `P2` `M` · after: T114, T036
  - Flip the SLM off without a redeploy. Flag fetched at sync time and cached locally so the fast path never depends on a network call.


## 12 · Fine-tune

The long pole. Gated on the section 06 go/no-go.

- [ ] **T074** — Synthetic dataset: intent × phrasing × slot variation  
  `P0` `L` · after: T036
  - Target 800–1,500 examples total including harvested real ones.
- [ ] **T075** — Harvest + hand-label real T0/T1 logs  
  `P0` `M` · after: T014
  - Real usage beats pure synthesis. This is why T014 lives in section 03.
- [ ] **T076** — Adversarial / out-of-scope examples mapped to `unknown`  
  `P0` `M` · after: T074
  - Training the model to abstain is cheaper and safer than training it to guess.
- [ ] **T077** — Freeze held-out test split BEFORE any tuning  
  `P0` `S` · after: T074, T075, T076
  - Do not skip. Do not peek. Everything downstream is meaningless if this leaks.
- [ ] **T078** — PEFT LoRA training run (q/k/v/o_proj)  
  `P0` `L` · after: T077, T039
  - Trained off-device.
- [ ] **T079** — Convert adapter via MediaPipe ConversionConfig  
  `P0` `M` · after: T078
- [ ] **T080** — Deploy adapter, verify load on GPU backend  
  `P0` `M` · after: T079
  - LoRA weights apply to attention layers only; GPU backend required.


## 13 · Benchmark

**The comparison table is the portfolio artifact** — more than the app.

- [ ] **T081** — Benchmark harness: accuracy, per-intent F1, slot exact-match  
  `P0` `L` · after: T077
- [ ] **T082** — Benchmark: TTFT, tokens/sec, peak RSS, battery/thermal  
  `P0` `M` · after: T080
  - `adb shell dumpsys meminfo`; sustained 50-command loop for thermal.
- [ ] **T083** — Baseline comparison table (rules / few-shot / fine-tuned)  
  `P0` `M` · after: T081, T082
  - 🏁 Report results even where the fine-tune loses.
- [ ] **T084** — TIMEBOX CHECKPOINT — ship whatever the number is  
  `P0` `S` · after: T083
  - ⚠ Set the date and target metric **in advance**. 'Improve accuracy' has no natural stopping point — this task is what stops it.


## 14 · Ship

- [ ] **T085** — dotnet publish self-contained single-file  
  `P1` `M` · after: T063
- [ ] **T086** — MSIX packaging + signing  
  `P1` `L` · after: T085
  - The tedious part most side projects skip and most employers care about.
- [ ] **T087** — First-run model download (WorkManager, SHA-256, unmetered)  
  `P0` `L` · after: T051
  - Model too large to bundle in an APK. Launcher stays fully functional at T0/T1/T3 before the model exists.
- [ ] **T088** — README + architecture diagram  
  `P0` `M` · after: T083
  - Recruiters skim. A diagram plus the benchmark table does more work than the code.
- [x] **T152** — Employer-facing project site (MkDocs Material → GitHub Pages)  
  `P2` `S`
  - **New (2026-08-21, user request.)** A public site for the project, aimed at an interviewer or hiring engineer.
  - **Built from `website/`, not `docs/`.** First attempt rendered `docs/` directly; that was rejected on review because the working engineering documents and a portfolio surface are different documents for different readers. `docs/` stays long, detailed and in-progress; `website/` is short, organised by project area, and written for someone evaluating the work. The site links out to `docs/` on GitHub rather than rendering it, so nothing is duplicated — `docs/` is the source of any fact, `website/` the summary.
  - **Organised by project area** — Android, Desktop (.NET), Azure backend, ML pipeline. Four pages, one per area. An "Engineering practices" page covering CI, source control and documentation was written and then cut on review: it described process rather than the product, and process is what a CV and an interview already cover. The one genuinely load-bearing item on it, the cross-language fixture contract, moved into the Android page next to the parsing it constrains.
  - **Unbuilt areas are stubs that say so**, in a status admonition at the top of the page and in a status table on the overview. Three of the four areas are scaffolds. A portfolio site that describes designed-but-unwritten work in the present tense is worse than one that admits the gap: the gap is checkable from the repo in about thirty seconds.
  - **Status is never conveyed by colour alone** — each row carries a distinct glyph and the words "Working" / "Not started", so the table survives greyscale and colour-blind readers.
  - **Plain-language pass.** The first draft read as machine-written: em-dashes everywhere, "not X, it's Y" antithesis, and an aphorism closing nearly every section. All three are gone, verified by grep rather than by eye, since proofreading your own prose for your own habits doesn't work. `CLAUDE.md` carries the constraint so the next session doesn't reintroduce it.
  - **Anchor validation is on** (`validation.anchors`), so a cross-page link to a renamed heading fails the build under `--strict`. Confirmed by deliberately breaking a link and watching CI-equivalent strict mode abort, then restoring it — a check that has never been seen to fail is not yet a check.
  - ⚠️ **Diagrams are Mermaid, which Material fetches from unpkg at runtime.** They could not be visually confirmed locally: the preview pane doesn't composite, and Mermaid measures text via `getBBox()`, which needs real layout. The diagram source parses cleanly under `mermaid.parse`, and the prose around every diagram stands alone so a blocked CDN costs decoration rather than meaning. **Confirm in a real browser before relying on them.**
  - **CI watches `website/`, not `docs/`** — so editing the design doc no longer rebuilds the site. Strict-mode build on pull requests; deploy job gated on `main` so a fork can build but never publish.
  - **`CLAUDE.md` carries a standing rule to keep the site current** — which page to touch, the status table, the "Last updated" stamp, replace stubs wholesale rather than amend them, and keep the prose plain. Written there rather than on the site, because a maintenance TODO visible to an interviewer reads as neglect.
  - **🔎 Noted, not fixed: `README.md` is stale** — it says notes "don't exist yet", which stopped being true around T018–T024.
  - **🔎 Noted, not fixed: `docs/cli-launcher-hld.md` is byte-identical to `docs/design.md`** with no inbound links.
  - **Verified**: `mkdocs build --strict` clean with anchor validation on; all four pages plus the overview render; nav, search, theme toggle and the custom stylesheet load.

- [ ] **T089** — Publish benchmark table  
  `P0` `S` · after: T083
  - Commit as `docs/benchmarks/*.md` — don't let the numbers live only in a terminal scrollback.
- [ ] **T090** — Writeup / blog post  
  `P1` `M` · after: T088


## 15 · v2: LiteRT-LM migration

Optional. MediaPipe is in maintenance-only mode; this is a planned migration, not a fire drill.

- [ ] **T091** — Implement LiteRtLmEngine behind InferenceEngine  
  `P2` `L` · after: T028
- [ ] **T092** — Benchmark MediaPipe vs LiteRT-LM backends  
  `P2` `M` · after: T091
  - Strong portfolio artifact in its own right.


## ⚑ Open questions (standing)

**Not sequential.** Each gates specific work — resolve when its dependent task comes up, not all at once.

- [ ] **T093** — Verify Azure AI Language F0 monthly allowance  
  `P2` `S`
  - Before depending on it. Auto-categorisation is a stretch goal, not critical path.
- [ ] **T094** — Confirm Gemma licence permits hosting converted artifact  
  `P1` `S`
  - Otherwise require user-side download from Hugging Face.
- [ ] **T095** — Confirm Blob free-tier egress survives multi-GB downloads  
  `P1` `S` · after: T051
  - A single re-download could dominate the monthly allowance.
- [ ] **T096** — Confirm Entra free tier covers 1 app, 2 platform configs  
  `P1` `S` · after: T040
  - Expected yes; verify before building auth.
- [ ] **T097** — Decide whether aliases sync  
  `P2` `S`
  - Currently no. Desktop has no apps to launch, so probably still no.


---

**156 tasks — 51 done, 105 open** (recounted 2026-08-20). Originally generated from the Todoist backlog; keep both in step when either changes. The count drifts as tasks are split (T006 → T140–T145) or added from use (T146–T151) — recount rather than trusting this line.
