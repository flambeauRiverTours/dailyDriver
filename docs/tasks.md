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
  - Deleted `ExampleUnitTest` (`assertEquals(4, 2 + 2)` — Android Studio template cruft). Kept `ExampleInstrumentedTest`: it guards the `com.flambeauriver.dailydriver` namespace that `android/CLAUDE.md` flags for a deliberate future rename, and it's the only thing proving the instrumented harness runs at all.
  - **Review pass over the new tests afterwards**, which removed four as redundant or misleading — a sweep that only ever grows is how a suite gets slow and untrustworthy:
    - *"a token that is a prefix of another still resolves to itself"* — **name claimed something it didn't test.** No registered token is actually a prefix of another (`:note` and `:notif` diverge at the fourth character), so the assertions only re-checked what `every registered token actually dispatches` already covered. Deleted; the forward-looking reasoning moved into the existing whole-word test's comment, where it's accurate.
    - *"every minute of the day formats without throwing"* — named "every minute" but stepped by 7, and asserted only `isNotBlank()`, which a badly broken formatter would pass. Fully subsumed by the two hour-sweeping tests beside it that assert real content.
    - *"a whitespace-only query never auto-launches"* — used a four-app list, so `singleOrNull()` returns null regardless and the test would have passed even with the blank-query guard deleted. The pre-existing single-app version is the one that actually exercises that guard.
    - *"is symmetric"* and the two `:apps`-specific auto-space tests — strict subsets of the property tests added in this sweep. Their explanatory comments were folded into the property tests that replaced them.
  - Also converted five negated `assertTrue(!x)` / `assertTrue(x.not())` to `assertFalse(x)`, which gives a usable failure message instead of "expected true, was false".
- [x] **T017** — Install as daily driver; keep fallback launcher installed  
  `P0` `S` · after: T011
  - Never ship an untested build to your only home screen.
  - **Done 2026-08-22.** Installed on the S20 FE from the `android-v0.1.0` GitHub Release, signed with the real release key, and set as the home app with One UI Home kept available as the fallback this task asks for.
  - **This is the Phase 0 exit gate, and closing it empties sections 02 and 03.** The launcher is now the real home screen running T0/T1/T3 with zero ML, which is exactly the exit criterion §9 sets.
  - **T014 landed first, as it had to.** The training-data log was explicitly sequenced before this task, because every day of daily-driving without it throws away the real usage §8's dataset depends on and which cannot be reconstructed afterwards. That ordering held.
  - **Getting it installed took four CI-only failures and a five-deep diagnosis** — see T158/T159 for the pipeline ones and T160 for the install itself. None was reachable from a local build.


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
- [x] **T014** — Log (raw_input, resolved_intent) for every T0/T1 hit  
  `P0` `S` · after: T009
  - **THIS IS TRAINING DATA.** Do it now or section 12 has nothing real to learn from.
  - **🔎 Found while designing this: §4.3's label space is stale.** It predates `:task`, `:tasks`, `:tasksdone` and `:notif`, so four of the ten reserved commands have no intent label. That reframed the whole task.
  - **The log records the resolution, not a §4.3 label** (check-in). A label space that has already gone stale once will go stale again, and unlike code a log cannot be refactored — the rows are a record of the past. So it stores what actually happened (which token matched, which package launched) and §8's hand-labelling maps it when the labels stop moving. §4.3 now carries a warning rather than being quietly wrong.
  - **Misses are logged too** (check-in), marked `T3`. Every fallthrough is exactly the out-of-scope example §8 wants mapped to `unknown`, and abstention data is far more convincing observed than invented. §4.2 only asked for hits; this is deliberately more.
  - **Deduplicated with a count** (check-in). The second reason is the load-bearing one: an undeduplicated log would be dominated by whatever app you open most, and a fine-tune on it would mostly learn that one example. A count keeps frequency as a number a training pipeline can weight deliberately instead of inheriting.
  - **Logged at one place, before the branches run.** `ShellScreen`'s Enter handler is the only point that sees every submitted line together with what each tier found. Logging inside the branches would mean a newly added command silently collecting nothing — a failure that breaks nothing and is invisible until the dataset is short.
  - **Alias *definitions* are excluded.** `alias sp = spotify` is configuration, not something anyone will later phrase in natural language.
  - **Never syncs, and casing is preserved.** The input is everything typed at a home screen, so no outbox row and no §6.2 entry — a stronger version of the alias exclusion (T097). Casing stays because what was typed *is* the training input, and normalising would discard a variation the model must handle at inference. `clear()` exists because erasing it has to be possible.
  - **Failures are swallowed at the repository.** A launcher that refused to open an app because its training log couldn't be written would have its priorities backwards.
  - **🐛 Found by asking "how does this get off the device?" — the answer exposed two problems, and one was a real bug.**
    1. **T1 was only logged when Enter beat the auto-launch timer.** App launches fire from a `LaunchedEffect` after a one-second typing pause, calling `onAppClick` directly and never reaching the Enter handler where logging lived. The commit had claimed that handler "is the only place that sees every submitted line" — demonstrably wrong. This did not lose T1 data outright, which would have been obvious; it lost it **whenever the pause beat the user to Enter**, biasing the training set toward fast typing. Fixed by logging on that path too, and verified on-device by typing and *never* pressing Enter.
    2. **Copying `dailydriver.db` alone yields nothing.** Room runs SQLite in WAL mode, so on a fresh install the `.db` is ~4KB and empty while `-wal` holds 127KB of data. A naive pull fails with `no such table: command_log`, which reads like the feature never worked. Reproduced deliberately before writing the export.
  - **`scripts/export-command-log.sh` emits JSONL**, copying all three SQLite files so the WAL replays. Written and run rather than described, because a documented procedure nobody has executed is a procedure that does not work yet — and this one is only discovered to be broken after months of uncollectable data.
  - Schema v2→v3, migrated and validated. 593 unit tests (was 585), **57 instrumented (was 48)** — including that repeating an input *increments* rather than resetting, which is the entire premise of the table and is invisible from Kotlin, and that 1→2→3 migrations compose.
  - **✅ First export reviewed 2026-09-21** — 261 rows, 1,116 recorded uses, 22 Aug – 18 Sep (~41 commands a day). The table works: zero malformed rows, the `(input, target)` key held with no duplicates, no row with `firstSeen > lastSeen`, and dedupe behaved as designed — the ten most-used rows are 55% of all uses while 64% of rows are singletons, which is the frequency-as-a-number shape the count column exists to produce. **Three caveats found, none fatal, all of which change how the file must be read at T075:**
    1. **`tier` is last-write-wins, so `tier × count` is not a tier distribution.** The upsert does `SET tier = :tier`, so a row with count 56 records the tier of the 56th use only. Fine for labelling, which is what the column is for; wrong for any "X% resolved deterministically at T0" figure, and that number is tempting enough to be worth writing down as unavailable. Recovering it would need `tier` in the primary key — which would split rows that are the same training example — or separate per-tier counters. **Not worth either.** §8 measures the model, not the cascade.
    2. **The `target` format changed mid-collection**, at T182–T184 ([ADR-0010](adr/0010-unified-candidate-matching.md), 14–15 Sep). Before, an alias expansion carried its argument into the target (`g fiona steele` → `:google fiona steele`); after, the target is the candidate's own target and the argument is not in it (`g qlora process` → `:google`). 42 rows use the old shape, 10 the new. The new behaviour is the correct one — the target is what the usage index counts, and counting `:google fiona steele` as its own candidate is meaningless — so this is a fix showing through the data, not damage. **Nothing is lost, because `input` always carries the full line.** The consequence for T075 is narrow and easy to get wrong: **read slots out of `input`, never out of `target`**, or the newest rows silently yield empty slots. Three further rows from the first hour of 22 Aug carry a pre-T014 bare label (`Firefox`, `messages`) rather than a package or a command — day-one churn, 3 uses total, discard them.
    3. **Four rows label a command as an app.** `:setting` → `com.android.settings` and `:browse` → `com.sec.android.app.sbrowser` are near-miss command typos that fell past T0 and fuzzy-matched an app; `:apps Firefox` and `:apps Spotify` resolve to the package the browse view launched. Labelled from `target` these become `open_app` when the intent was `open_settings` or `list_apps`. Same rule as (2): **the input is the training example, the target is a hint.**
  - **🔎 What the first export says about §8's dataset, which is more useful than the integrity result.** 261 unique inputs is a real seed — a fifth to a third of §8's 800–1,500, and the portion that cannot be invented convincingly — but its shape shifts work onto T074 and T076 rather than off them:
    - **64% of rows are one intent** (`open_app`, 168 rows across 78 packages). Weighting by `count` would make that 78%. The log is a good source for `open_app` phrasings and a poor source for everything else.
    - **Seven of the sixteen labels have zero examples**: `open_settings`, `search_notes`, `list_completed_tasks`, `define_alias`, `list_aliases`, `open_note`, `create_checklist`, `check_item`. The last two **can never appear** — they are UI taps, not prompt input, so no amount of further driving produces one. T074 owns those two outright.
    - **Only three `unknown` examples, and all three are the same failure mode** (`:nptif`, `:notfis`, `: Note Journal 8/23/26` — typos of a reserved command). §8 wants abstention FPR measured against out-of-scope input, and near-miss command typos are the easiest possible case. **T076 cannot lean on the log for adversarial examples.** Worth stating plainly because T014's design note promised the opposite — that observed misses beat invented ones — and on this evidence that holds for *realism* and not at all for *coverage*.
    - **🔎 Four of the twelve reserved commands were never used once in 27 days of real daily driving**: `:notes`, `:tasksdone`, `:alias`, `:settings`. That is product information, not a dataset problem, and no fine-tune addresses it — either they are undiscoverable or they are unneeded. Worth an open question rather than a silent gap in the training set.
    - **A time series cannot be reconstructed from this schema.** `lastSeen` is overwritten on every use, so a day with no `lastSeen` is not a day with no usage. Irrelevant to a bag-of-examples harvest, fatal to a temporal train/test split — so **T077 splits randomly or by intent, never by date.**
    - **↻ §4.3 was two labels short, found here** — `:google` and `:export` were registered by T140 after the label space settled. `web_search` and `export_log` added 2026-09-21 in §4.3 **and in `Intent`**, so the schema and the doc agree rather than the doc running ahead. `web_search` is the log's second most-used command and carries nearly all of its free-form natural language, so labelling it late nearly cost the model its strongest real-usage category. Verified: **792 unit tests (was 790, +2), 0 failures; lint 0 errors**, no new warnings.
    - **🐛 The test that exists to catch this had the same gap.** `the label space covers every reserved command` (T035) checks the label set against a **hand-written list of command wires**, and that list was never updated when T140 added two commands — so it passed for the entire period it was wrong. A guard that only fires when someone remembers to update it is not a guard, and this is the second time §4.3 has gone stale unnoticed. The list is corrected and the weakness is written into the test itself; **the durable fix is an exhaustive `when` over `ReservedCommand` mapping each variant to an `Intent`**, which the compiler refuses to let a new command skip. Not done here because it puts a new dependency between the `command` and `slm` packages, and that direction is worth choosing deliberately rather than in passing — worth doing as part of T036, which has to bridge those two packages anyway.
  - **⚠️ Privacy: the export is not portfolio-safe, and it is already committed.** 51 rows preserve full search queries in `input` — health (IVF, restless leg syndrome, gray teeth), finance (EMBA cost, IRA advice), and named individuals, including three `:task` and `:note` lines naming real people. The on-device rules did their job: never synced, no outbox row, `clear()` exists. **The leak is on the desktop side** — `dailydriver-command-log-2026-09-18-144837.jsonl` was committed to the repo root in `2553b0c` (2026-09-21) and pushed to `origin/main`. The repo is private, so nothing is public, and the fix is cheap **only while that stays true**:
    - Untracked and `.gitignore`d 2026-09-21 (`dailydriver-command-log-*.jsonl`), so no further export lands by accident.
    - **The blob is still in history.** Removing it means a rewrite and a force-push to `main`, which this repo's own protection rules block — an owner can lift them, and the window to do it cheaply closes the moment the repo goes public or anyone clones it. **Decide before the portfolio flip, not at it.**
    - **T075 needs a redaction pass in its definition, not as an afterthought.** The harvest moves this text into a dataset that is meant to be published, and a fine-tune trained on it can emit those strings. `web_search` queries are the whole problem: they are simultaneously the richest natural-language examples in the log (§4.3) and the only rows carrying anything private. Replacing each query with a synthetic one of matching shape keeps the phrasing signal and drops the content.



## 02a · Daily-driving fixes

**Found by using it (2026-08-22), the day after T017 made the launcher the real home screen.** Kept out of section 02 so Phase 0's record stays as it closed: none of these were visible from a test bench, and finding them is what daily-driving was for.

- [x] **T161** — Battery level in the status row  
  `P1` `S` · after: T146
  - Sits **right of the date**, so the row reads `[unread] time … date battery`.
  - **Fixed width, padded.** `9%`, `87%` and `100%` are three different widths, and a row that shifts as the battery drains is the same jitter the blinking colon already avoids by swapping the colon for a space rather than dropping it (T146). Pad to a constant cell count.
  - **Charging gets a marker, not a colour.** Colour alone is no signal in a dark monospace UI, and an ASCII marker sidesteps the glyph-availability problem that made `⠿` unusable as a drag handle at T023.
  - Read from the sticky `ACTION_BATTERY_CHANGED` broadcast rather than polling; battery moves slowly. Register only while RESUMED, matching how the clock already ticks.
  - Formatting goes in `StatusLineFormat` (pure, JVM-testable) with only the subscription in Compose — the split T146 established.
  - **`EXTRA_SCALE` is the denominator, not always 100.** Reading `EXTRA_LEVEL` as a percentage directly is the common way to be quietly wrong on a device that reports out of a different maximum.
  - **🐛 Found by looking at it, not by a test: `Sat 22 Aug+100%`.** The cell's leading column *is* the charging marker, so when charging there was nothing left to separate battery from date. The width tests all passed — they assert the cell is five columns, which it was. Separated by layout instead, leaving `batteryText` a pure value formatter.
  - Verified on an API 33 emulator across 5%, 9%, 87% and 100%, charging and not: the `%` sits at the same x in every case.
- [x] **T162** — Validate an alias target when it is defined  
  `P1` `M` · after: T013
  - Today any string can be aliased, so a typo becomes an alias that silently resolves to nothing.
  - **Resolve at definition time**: a reserved command token is valid; otherwise fuzzy-match the app index. No match at all is a rejection with the reason, not a stored alias.
  - **Ambiguity asks rather than guesses.** Two apps sharing a label, or a prefix matching several, lists the candidates and declines until one is named. This is the *opposite* of T1's best-match-wins rule, deliberately: T1 is a guess you correct with one keystroke, an alias is a decision you are storing, and a wrong stored guess is wrong every time afterwards.
  - Extends `DefineAliasResult` rather than adding a second reporting path. Cycle checks already exist and stay.
  - **🔎 An app target is stored as the app's full label, not what was typed**, which was not in the original scope and turned out to matter most. Aliases re-resolve through T1 on *every* use, so storing `alias c = calc` leaves the alias meaning whatever `calc` matches later — installing a better-scoring app would silently repoint an alias the user never touched. Resolving once, at definition, pins it to what they actually chose. Verified on device: `alias ch = chrom` stores `ch = Chrome`.
  - **Structural rejections are checked before target validation.** `alias a = a` is self-referential whether or not anything called `a` is installed, and reporting it as an unknown target would send you hunting for a missing app instead of a typo.
  - **"Only a prefix was typed" is a cause of ambiguity, not ambiguity itself.** A prefix narrowing to exactly one app is accepted; the alias just stores the full name.
  - **Ambiguous candidates are named using T164's disambiguation**, so two apps called `Messages` list as `Messages (Google), Messages (Samsung)` rather than the useless `Messages, Messages`. The two tasks compose without either knowing about the other.
  - `target` defaults to `AliasTarget.Command`, so every existing caller and test keeps working and an unvalidated definition behaves exactly as before.
  - 17 tests, plus device verification of all three outcomes: nothing matched, unique prefix accepted, and `'c' matches Clock, Camera, Chrome, Calendar, Contacts — be more specific`.
- [x] **T163** — Aliases expand as you type, not only on Enter  
  `P1` `S` · after: T162
  - `a = :apps` should become `:apps ` with the browse list open, without pressing Enter.
  - **On the existing pause timer, not instantly** (check-in). Instant expansion on exact match makes every longer input starting with that alias unreachable — type `a` and it becomes `:apps ` before you can reach `alarm`. That is exactly the trap that made `:notes` untypeable at T140, recorded in `android/CLAUDE.md`. Reusing `AUTO_LAUNCH_DELAY_MS` means the behaviour matches what the prompt already does for T1 auto-launch.
  - **Enter still expands immediately**, which it already does. Enter is an explicit commit and carries no ambiguity to protect against.
  - ⚠️ One alias being a prefix of another (`a` and `ab`) inherits the same problem in miniature: pausing after `a` commits to `a`. Acceptable, and worth stating in the task rather than discovering.
  - **The alias timer is checked before the app timer**, and that ordering is the whole correctness argument: an exact alias is T0 and a fuzzy app match is T1, so an alias that also happens to fuzzy-match an app must expand rather than launch. The two effects are separate, with the app one yielding when an alias is pending, so they cannot race.
  - **The space came for free.** Expansion goes through `onInputChange`, which already auto-spaces a completed token — `:apps` is auto-spaceable, so `a` becomes `:apps ` and the browse view opens with no new code.
  - Logged as a `T0` resolution (T014), matching the auto-launch path, so pause-expanded aliases are not missing from the training data the way T1 hits once were.
  - Verified on an API 33 emulator: with `a = :apps` defined, typing `a` and waiting opened the app list with Enter never pressed.
- [x] **T164** — Disambiguate duplicate app names with the publisher  
  `P1` `S` · after: T003
  - Two apps labelled `Messages` are indistinguishable in `:apps`, which also makes T1's preview line ambiguous.
  - **Only duplicates get a suffix.** Adding a publisher to every row would be noise on the 95% of names that are already unique.
  - **Publisher derived from the package name** (check-in): the vendor segment, so `com.google.android.apps.messaging` → `Messages (Google)` and `com.samsung.android.messaging` → `Messages (Samsung)`. No new `PackageManager` calls, and it matches how a person would name them.
  - Falls back to the full package when the heuristic yields nothing useful — a two-segment package like `com.whatsapp` has no vendor segment to read.
  - **Not the installer package**, which sounds authoritative and is not: nearly everything reports the Play Store, so both `Messages` apps would still look identical.
  - Pure function over the app index, so it is unit-testable and applies to both the list and the match preview — they must agree, or the preview names something the list does not.
  - **🔎 Display names are a separate map, not folded into `AppEntry`, and that is load-bearing.** Matching must keep seeing the real label: a fuzzy match against `Messages (Google)` scores *worse* for someone typing `mess` than one against `Messages`, so writing the suffix into the entry would make disambiguation actively harm the thing it exists to clarify. Pinned by a test.
  - **Computed over the whole index, not the filtered list.** Whether `Messages` is ambiguous is a property of what is installed, not of what the query narrowed to. Deriving it from the filtered list would drop the suffix the moment the filter reached one match — which is exactly when you are about to commit to it.
  - 11 tests, including case- and whitespace-insensitive ambiguity detection and the two-segment package fallback.
- [x] **T166** — In-app export of the command log  
  `P0` `M` · after: T014
  - **🐛 Found 2026-08-22: the training data on the daily-driven phone cannot currently be retrieved.** `scripts/export-command-log.sh` reads the database through `run-as`, which only works on a **debuggable** build. The phone runs a release build from T159, so `run-as` refuses with `package not debuggable`. T014 collects, T159 ships, and the two do not meet.
  - **Nothing is lost yet** — the rows are in app-private storage — but they are unreachable, and an uninstall or reinstall would destroy them. That nearly happened while trying to sideload a debug build for T165.
  - **This would have surfaced on 29 August**, when the scheduled test-pull ran and failed, with a week of unrecoverable usage behind it. The reminder existed precisely to catch a wrong assumption early; it caught this one a week early instead, by accident.
  - **Fix: export from inside the app**, not from adb. A command writing JSONL to shared storage works on any build type, needs no cable, and does not depend on the debug flag. `scripts/export-command-log.sh` stays useful for emulator and debug-build work.
  - ⚠️ **Do not "fix" this by making release builds debuggable.** That would expose app-private data on the daily driver to any app with adb access, to make a maintenance task easier.
  - Update the 29 August reminder and `docs/releasing.md` once this lands, since both currently describe a route that does not work on the build being used.
  - **Built as `:export`**, a normal T0 reserved command through T140's registry — so it documents itself in `:help` and needs no settings screen. Writes to Downloads via MediaStore, which needs no permission for a file the app creates and lands somewhere a file manager, a USB cable and `adb pull` can all reach.
  - **Split pure from impure**: `command/CommandLogExport.kt` is the serialisation and the messages (14 unit tests), `command/CommandLogExporter.kt` is the MediaStore write. What the bytes *say* is the part that has to be right, and that half needs no device.
  - **🔎 MediaStore renames what you give it, twice over, and both were found by running it rather than reading about it.** It reconciles the display name's extension against the MIME type, so the "correct" `application/json` produced `dailydriver-command-log-2026-08-22.jsonl.json`; an *unregistered* type (`application/x-ndjson`) is what leaves the name alone. Separately, it de-duplicates a repeated name by appending ` (1)` **after** the extension — `….jsonl (1)` — which a `*.jsonl` glob then skips, so the second export of a day would silently go missing. The file name now carries a seconds-precision stamp and is unique before MediaStore ever sees it.
  - **The command reports the path queried back from MediaStore, not the one it asked for.** T166 is entirely a story about trusting a route that quietly did something other than what it looked like, and this is that lesson applied to its own fix. It is also what surfaced the `.jsonl.json` rename in the first place, on the very first run.
  - **An empty log still writes a file and still reports `0 inputs`.** A silent no-op on nothing-to-export would reproduce the exact failure mode this task exists to fix.
  - **A failed export is reported in the transcript**, deliberately inverting `CommandLogRepository.record`'s swallow-everything rule. Recording the log must never cost an app launch; exporting *is* the command, so a silent failure is the bug.
  - The JSONL keys are a contract with `scripts/export-command-log.sh` — pinned by a test, and cross-referenced in both files' headers.
  - Verified on an API 33 emulator: seeded the log, exported twice five seconds apart, both files clean `.jsonl` in `Download/` with the transcript naming each one.

- [x] **T165** — Keyboard should appear whenever the launcher resumes  
  `P1` `M` · after: T015
  - Unlocking the phone lands on the prompt with no keyboard, so every command starts with a tap. On a keyboard-first launcher that is the whole interaction being broken.
  - **Every resume, not just unlock** (check-in): returning from an app or dismissing the shade should also be ready to type. The input line drives the live filter in `:apps` and `:notes` too, so the keyboard is wanted in browse mode as well.
  - Focus request plus an explicit IME show on RESUMED, alongside `windowSoftInputMode`. The manifest flag alone governs first window focus and does not cover a resume.
  - **⚠️ Interacts with T149's immersive mode**, which re-applies on every focus change; the IME moves the insets that immersive mode is manipulating. Expect to verify on device rather than trusting it compiles.
  - **Device verification is required, not optional.** Android restricts showing the IME without user interaction on some versions, and this is unobservable from a unit test.
  - **🔎 The cause was not missing focus — focus was already being requested.** T015's `focusRegainedSignal` fires on window focus gain and calls `requestFocus()`, but **`requestFocus()` is a no-op when the field never lost focus**, which is exactly what unlocking looks like: the window comes back with the input still focused, nothing changes, and nothing ever asks for the keyboard. Showing the IME is now explicit and unconditional, so it cannot depend on focus having moved.
  - `stateVisible` added to `windowSoftInputMode` as well, covering the window's first appearance; the two mechanisms handle different moments.
  - Verified on an API 33 emulator through a real sleep → wake → dismiss-keyguard cycle: the prompt comes back focused with the keyboard up. **No conflict with T149** — the system bars stayed hidden and the status row was intact, which was the interaction worth worrying about.
  - ⚠️ Still worth a confirmation on the S20 FE, since IME policy varies by OEM and the emulator is AOSP. Blocked behind T166: installing a debug build there would require uninstalling the release build and destroying the command log.

- [x] **T167** — `:apps` cannot find a disambiguated app name  
  `P1` `S` · after: T164
  - **🐛 Typing `:apps Messages (Google)` matches nothing**, even though that is the name the list itself just showed you. The filter is unusable for exactly the apps T164 added the suffix for.
  - `AppFiltering.filteredBy` substring-matches `AppEntry.label` only. T164 deliberately kept display names out of `AppEntry` and computed them in `ShellScreen`, but only ever passed them to the *view*. So the suffix is rendered and not searchable — the name on screen is not a name you can type.
  - Fix: pass the display-name map into `filteredBy` and match label **or** display name.
  - ⚠️ This does **not** contradict T164's "matching still sees the real label" test, and check that test still passes rather than assuming. That rule is about `rankedMatches`, where fuzzy *scoring* against `Messages (Google)` is worse for someone typing `mess`. A substring filter has no score to degrade: `mess` matches both strings equally.
  - Built: `filteredBy(query, names)` matches label **or** display name, with `names` defaulting to empty so callers without a display map behave exactly as before. 10 tests. T164's `rankedMatches` rule is untouched and still passes.

- [x] **T168** — `:notes` snippets show raw Markdown  
  `P1` `S` · after: T025, T153
  - **🐛 A task note previews as `- [ ] buy milk @2026-08-23`** — checkbox marker and date token and all. Reproduced on an emulator; the checklist row itself is already correct (`[ ] buy milk` with a `tomorrow` label), so this is the list snippet alone.
  - `NoteListView` renders `note.body` directly. The body is Markdown and always was; nothing ever taught the snippet to read it.
  - Fix: build the snippet from parsed content — strip the `- [ ]`/`- [x]` markers and lift out the `@YYYY-MM-DD` tokens, the same way the editor row does.
  - **Strip the marker too, not only the date.** `- [ ] buy milk` is exactly as raw as the token beside it, and fixing one while leaving the other says the leak was a formatting slip rather than the snippet never having been decoded.
  - Built: `noteSnippet` runs the first meaningful line through `parseChecklist`, which strips the marker and lifts the date, and drops tag lines first so a note opening with `#work` previews its content. Verified on an emulator: the row now reads `buy milk`.

- [x] **T169** — Tags in a checklist become checkbox rows  
  `P1` `M` · after: T139, T023
  - **🐛 Adding a tag chip to a checklist creates a checkbox that says `#shopping`.** `withTag` appends the tag on its own line at the end of the body (T139), and `parseChecklist` rule 2 makes *any* non-blank line an unchecked item. A tag is note metadata and has no business being something you can tick off.
  - **Do not fix this by changing `parseChecklist`.** It is pinned by `fixtures/checklist-parsing.json` and owes a matching C# implementation (§7.3); rule 2 is also what makes note→checklist conversion a parse rather than a special case.
  - **And do not fix it by having the parser drop tag lines either** — `editItems` is `render(transform(parse(body)))`, so a line the parser drops is a line the render deletes. The first tag you added would vanish on the next keystroke.
  - Fix: a pure `splitTagLines(body)` in `notes/`, used by the editor and by both conversion functions, so tag lines are lifted off before parsing and reattached after rendering. The contract file and the C# obligation stay untouched.
  - Built as `notes/TagLines.kt` — `splitTagLines`/`rejoin`, applied at all four places the editor reads the body. `fixtures/checklist-parsing.json` and the C# obligation are untouched.
  - **🔎 The split alone did not fix the reported bug, and finding that out needed the device.** T139's `+ tag` appends a *bare* `#` and hands the cursor back to the body so you type the name — which works for prose and cannot work for a checklist, because a checklist has no body field. The `#` landed as a checkbox, and typing into it produced `- [ ] #shopping`: an item shaped like a tag that can never become one. A bare `#` is correctly not a tag line, so no amount of splitting reaches it.
  - **So `+ tag` now opens a one-line input in checklist mode** (UI check-in, 2026-08-22), leaving prose notes exactly as they were. **This is still not a second writer of the tag set** — T139's actual objection: the input is transient and commits through the same `withTag` the chips use, so the body remains the only place tags live. `#` is rendered rather than typed, since it is the one character every tag must have.
  - Commits on Done or on focus loss; blank commits nothing, so opening it by accident costs nothing.
  - 16 tests, including the round-trip that would have deleted the first tag had the parser simply dropped tag lines. Verified on an emulator: `+ tag` produces a chip, and the checklist row count does not change.

- [x] **T170** — `:apps` opens scrolled to Z  
  `P1` `S` · after: T012
  - **🐛 The list opens showing `z, y, x, w`** and you scroll the whole alphabet to reach A. The intent (documented on `AppListView` since T012) is the opposite: bottom-anchored, A nearest your thumb.
  - The view reverses the list by hand and relies on `Arrangement.Bottom`, which only arranges *slack* — so it does nothing once the content is taller than the screen, which for a real app list is always. A short filtered result sits correctly at the bottom; the unfiltered list starts at index 0, which the manual reverse made Z.
  - Fix: drop `apps.reversed()` and set `reverseLayout = true`, feeding the LazyColumn A→Z. Item 0 is then A, drawn at the bottom, and a reversed list starts at item 0 — so the correct position falls out instead of being arranged into place.
  - The fast-scroll rail still reads Z→A top to bottom, but its indices are now into the A→Z list. Getting that backwards sends every letter to the wrong end.
  - Built: `reverseLayout = true` over the A→Z list, replacing the manual `.reversed()`. The fast-scroll rail reverses its *letters* while its indices stay A→Z. Verified on an emulator: the list opens on Calendar, Camera, Chrome reading upward.

- [x] **T171** — Date belongs beside the time  
  `P1` `S` · after: T146, T161
  - Time is at the left edge, date and battery at the right. Time and date are one piece of information read together; battery is the thing that should be off on its own.
  - Fix: regroup the status row as `[dot] [time] [date]` left, `[battery]` right.
  - Built, plus a `·` separator (UI check-in, 2026-08-22). Adjacency alone was not enough: the blinking colon means the time alternates between `4:28` and `4 28`, so on every other tick the only thing dividing the two fields was a gap the same width as the one inside the time. Reads `8:18 AM · Sun 23 Aug`.

- [x] **T172** — `:google <query>`  
  `P2` `S` · after: T140
  - Searches Google for the rest of the line and hands off to the default browser. Registered through T140's dispatcher like every other command, so it documents itself in `:help`.
  - Auto-space comes free: `autoSpaceableTokens` includes any token no other token extends, and nothing extends `:google`.
  - `ACTION_VIEW` on `https://www.google.com/search?q=…` rather than `ACTION_WEB_SEARCH`. The latter respects the device's chosen search engine, which sounds better and is not what was asked for — it also frequently resolves to a search *app* rather than the browser.
  - **The query must be URL-encoded**, and bare `:google` with nothing after it should say so rather than opening a blank search, the same way `:task` does.
  - Worth noting plainly: this is the first command that sends typed text off the device. Nothing is logged differently, but `:google` lines land in the T014 command log like any other input.
  - Built as `search/WebSearch.kt` (pure, 6 tests) plus `WebSearchLauncher`. Verified on an emulator: Chrome opened on the search URL, and bare `:google` reported `nothing to search`.

- [x] **T173** — Aliases should take arguments  
  `P2` `M` · after: T144, T162, T163
  - `alias a = :apps` then `a firefox` should run `:apps firefox`. Today `aliases[input.trim()]` is a whole-string lookup, so `a firefox` matches nothing and falls through to T3.
  - Fix: look up the first token and append the remainder to the expansion.
  - **Always append, including when the alias points at an app** (decided 2026-08-22). The alternative considered was expanding-with-arguments only for reserved commands, on the grounds that `alias s = Spotify` makes `s foo` expand to `Spotify foo` and dead-end at T3. Rejected: creating an alias that takes no argument and then passing it one is the user's own doing, and a rule that silently declines to expand in some cases is harder to hold in your head than one that always expands. **Keep the rule uniform — do not add a target-type special case here.**
  - Falls out of that: an app alias given an argument produces a T3 miss on a string the user did not type, which the T014 log will record. That is acceptable and worth knowing when reading the log, not a reason to complicate the rule.
  - Check the interaction with T163's pause-expansion rather than assuming: both paths converge on the same string, but the timer is keyed on `input`, and a bare alias still has to expand on its own.
  - Built as `alias/AliasExpansion.kt` — whole-string lookup first, then first-token plus remainder. 13 tests. Verified on an emulator: with `alias a = :apps`, typing `a chrome` launched Chrome.

- [ ] **T174** — Launch app shortcuts as a second matching level  
  `P2` `L` · after: T167, T017
  - **⚠ BLOCKED ON THE PHONE, AND PARKED (2026-09-24).** Android is otherwise complete, so this is the only remaining item that would pull a session back to device work. The AOSP emulator publishes zero shortcuts, so it can **only** be developed against the S20 FE. Pick it up when there is a reason to be on the phone anyway.
  - **`:apps Messages Laura` opens the conversation with Laura**, rather than just Messages. Raised 2026-08-22.
  - **There is no generic launch-with-arguments in Android.** `getLaunchIntentForPackage` builds a `CATEGORY_LAUNCHER` intent with no argument slot, and nothing resembling `argv` exists. Do not go looking for one.
  - **The mechanism is `LauncherApps.getShortcuts()`** — the shortcuts an app publishes, the ones you get long-pressing its icon. Google Messages publishes one per recent conversation. Verified against `android.jar`: `getShortcuts(ShortcutQuery, UserHandle)`, `startShortcut(...)`, `hasShortcutHostPermission()`, and `ShortcutInfo.getShortLabel()/getLongLabel()`.
  - **⚠️ It requires being the default launcher, which is exactly why it fits.** T017 already made dailyDriver the home app; almost nothing else can call this API. Gate on `hasShortcutHostPermission()` rather than assuming, since the launcher can be swapped back to One UI Home at any time. Query with `FLAG_MATCH_DYNAMIC or FLAG_MATCH_MANIFEST or FLAG_MATCH_PINNED`.
  - **Shortcuts carry string labels, so this is the existing fuzzy matcher run one level deeper** — match the first word against apps, the rest against that app's shortcut labels. No new matching machinery and no per-app knowledge. It is also the natural slot-filling target for T2 later: "message laura" is an intent with an app slot and a shortcut slot.
  - **⚠️ The AOSP emulator publishes zero shortcuts** (`dumpsys shortcut` counts none), so this can only be developed against the S20 FE. Budget for that before starting.
  - Not every app publishes anything useful, so the feature degrades to nothing on those. The UI has to say so rather than look broken.
  - **Rejected alternatives.** Standard intent actions (`ACTION_SENDTO` + `smsto:`, `ACTION_DIAL` + `tel:`) are generic but capability-shaped rather than app-shaped, and resolving a *name* to a number needs Contacts — a real privacy step for a launcher that currently reads none. Per-app deep links (`spotify:search:`, `wa.me/`) work today and are an unbounded table of undocumented URIs that break without warning.

- [x] **T175** — `:notes` and `:tasks` open at the wrong end  
  `P1` `S` · after: T170
  - **🐛 The same defect T170 fixed in `AppListView`, still present in two more copies.** `NoteListView` and `TaskListView` both do `.reversed()` then lean on `Arrangement.Bottom`, which distributes *slack* and therefore does nothing once the content is taller than the viewport.
  - Their own comments state the intent that is not being met: notes should put "the newest note closest to the prompt", tasks "the most urgent task closest to hand". On overflow both open at index 0 instead — the **oldest** notes, the **least urgent** tasks.
  - `NotifListView` uses `Arrangement.Bottom` with no reverse at all; check what it is actually trying to do before changing it, rather than assuming it has the same bug.
  - **Invisible until the list is long**, which is exactly why T170 survived from T012. Reproduce with enough rows to overflow before fixing, so the fix is verified rather than reasoned.
  - Fix as T170 did: `reverseLayout = true` over the unreversed list. Do not paper over it with an initial `scrollToItem`, which corrects the position a frame late.
  - **Reproduced first, as the task demanded.** 16 seeded tasks: `:tasks` opened on task16 → task10, the *least* urgent, with the soonest nine below the fold. Fixed and re-shot: task01 now sits directly above the prompt.
  - `:notes` confirmed the same way with 10 seeded notes — the newest is now nearest the prompt.
  - **`NotifListView` was a third variant, not a fourth copy.** It had no hand-reversed list, so only its *resting position* was wrong; its content order was already right. Adopting the fix therefore **flips the order of a short list**, which is the common case for notifications. Done deliberately: every other view in the shell puts item 0 nearest the prompt, and this one's own docs already claimed "the same bottom-anchored shape as `AppListView`" while doing something else. ⚠️ Not verified on-device — overflowing it needs granted notification access and more notifications than an idle emulator produces.

- [x] **T176** — One bottom-anchored list, not four  
  `P2` `M` · after: T175
  - Four views (`AppListView`, `NoteListView`, `NotifListView`, `TaskListView`, ~390 lines) each hand-roll the same bottom-anchored `LazyColumn`. **Three of the four got it wrong**, which is the case for extracting it: T170 and T175 are one bug found twice because there was no single place to be right.
  - Extract a `BottomAnchoredList` taking rows, a key, a test tag and row content. The differences that must survive: `AppListView`'s fast-scroll rail, `NoteListView`'s two-line rows, `TaskListView`'s due labels and toggle.
  - ⚠️ Do this **after** T175, not instead of it. Fixing the bug inside a refactor makes it impossible to tell which change fixed what.
  - Built as `shell/BottomAnchoredList.kt`; all four views converted. `AppListView` passes its own `LazyListState` so the fast-scroll rail can still scroll programmatically.
  - **The component's doc carries the warning that matters**: callers pass rows in *natural* order and must not reverse first. Reversing the data *and* the layout cancels out, which is precisely how three of the four views got it wrong.
  - Suite unchanged at 725 — the right signal for a refactor. A test needing an edit would have meant this was not one.

- [x] **T177** — Collapse the three launchers, make `splitTagLines` structural  
  `P2` `S` · after: T172, T169
  - `AppLauncher`, `SettingsLauncher` and `WebSearchLauncher` are 93 lines of one shape: build an Intent, add `FLAG_ACTIVITY_NEW_TASK`, `try`/`catch ActivityNotFoundException`, return Boolean. One `IntentLauncher` with three factory functions is about a third of that.
  - `NoteEditorViewModel` applies `splitTagLines` at **four** call sites by hand. Replace with one `editBody { content -> … }` helper so the split is structural rather than remembered — "remember to apply it everywhere" is precisely how T169 happened.
  - Both are cleanups with no behaviour change, so the existing suite is the check: if a test changes, something was not a cleanup.
  - `shell/IntentLauncher.kt` replaces `AppLauncher`, `SettingsLauncher` and `WebSearchLauncher` (93 lines → 60 including more documentation than all three carried). All three paths verified on an emulator: `openSettings` → Settings, `openUrl` → Chrome, `launchApp` → Clock via a T1 fuzzy match.
  - `settings/` is now an empty package and was removed.
  - `editBody { content -> … }` and `checklistOf(body)` replace the four hand-applied `splitTagLines` calls in `NoteEditorViewModel`. The split is now structural: there is no way to read or rewrite a checklist body that skips it.

- [x] **T178** — Command dispatch lives in two places  
  `P1` `L` · after: T140
  - **`ShellViewModel.onSubmit()` has 12 branches and 11 are a bare `return`.** Only `:help` does anything. Each no-op carries a comment saying ShellScreen intercepts it first — so the ViewModel that owns the state does almost none of the deciding, and the composable that should only render does most of it.
  - **Not sloppiness — forced.** A command needs a `Context` (launchers) or a suspend Room write, and the ViewModel has neither. Every command since has been answered with another callback.
  - The costs are measurable: **30 parameters** on `ShellScreen`; **15** ViewModel methods that are only `transcript.record(…)` then `input = ""`; four `reservedCommand as?` downcasts plus a four-branch `if/else-if` re-deriving what the parser already knew.
  - **Fix: the registry should map a token to *behaviour*, not just to a parsed variant.** T140 already owns token → `ReservedCommand`; extend it to a handler returning **effects as data** (`LaunchApp(pkg)`, `OpenUrl(url)`, `Record(text)`) that `MainActivity` performs. `onSubmit` becomes one dispatch, `ShellScreen` goes back to rendering, and adding a command is one registry entry instead of edits across four files. Most of the 30 parameters and all 15 pass-through methods fall out.
  - ⚠️ **Highest-risk item on this list — plan it before starting.** It touches every command, and the registry property tests (T150) are the safety net that makes it feasible at all. Do T175–T177 first; they are independent and shrink the surface this has to move.
  - **Don't take this as licence to add DI.** `MainActivity`'s nine `lateinit` fields look like the smell, but with one Activity and no test seams, Hilt would cost more than it returns. The problem is where decisions are made, not how objects are built.
  - Built as `command/CommandEnvironment.kt` (the seam) + `command/CommandDispatch.kt` (the single dispatch) + a `ShellViewModel.factory`. **Results: `ShellScreen` 30 parameters → 22 and 295 → 222 code lines; `ShellViewModel` 278 → 148 lines with the 15 record-and-clear methods gone; `MainActivity.onCreate` 114 → 60 code lines.** No DI framework, as the note above insisted.
  - **🔎 The refactor found a UX regression in itself, and the tests caught it.** Clearing the prompt unconditionally on submit closed the browse list you were still filtering — `:apps ca` + Enter threw away the filter that produced the list. The old eleven no-ops had been quietly encoding that. Fixed with a third outcome, `CommandOutcome.Stay`: a browse view with nothing to commit to keeps what was typed, and clearing is the *outcome's* decision rather than the caller's.
  - **🐛 And it found a second regression, only visible in the exported log.** Moving the logging dropped `ShellScreen`'s deliberate exclusion of alias *definitions*, so `alias a = :apps` started being filed as a **T3 miss** — teaching the training set that a line which worked perfectly was unresolvable. Restored as `isTrainingExample`, with a regression test naming the incident. Nothing but reading a real export would have shown this.
  - **The folded-in note-pause bug is fixed and verified the only way it can be**: the export now contains a `:notes` row that came from the pause path, which logged nothing before.
  - Clearing is deferred until dispatch returns and guarded on the input still being what was submitted, so a slow command (`:export` reads the database and writes a file) cannot wipe something typed while it ran.
  - **31 new tests** against a fake environment (`CommandDispatchTest`), covering every command and the T0→T1→T3 cascade — a surface that had **zero** coverage because it lived inside a composable. `runBlocking` rather than a coroutines-test dispatcher: dispatch suspends but never delays, so no new dependency was needed.
  - Several `ShellViewModelTest` cases changed, as expected — they asserted the architecture by name ("ShellScreen handles it, not this fallback"). Where the premise died the property underneath was kept: the no-op tests became "browse commands keep the prompt".
  - Verified on an emulator: every command through Enter, all three pause paths, and an export read back to confirm each tier appears.

- [x] **T179** — Three long functions carrying several jobs each  
  `P2` `M` · after: T146, T023
  - **Measure code lines, not total lines, before believing any of this.** This module comments heavily on purpose, so raw length badly overstates complexity: `toNoteType` reads as 55 lines and is 19 of code, `onSubmit` as 67 and is 24. Counting code only, **64 functions are 10+ lines**, which is unremarkable for 7,400 — the problem is the distribution, not the count. Four functions hold most of the weight: `ShellScreen` (295), `ChecklistRow` (117), `MainActivity.onCreate` (114), `StatusLine` (72). Everything else is 53 or under.
  - **Three extractions, independent of each other and of T175–T178.** None needs the dispatch rework, so this can run alongside T177 as low-risk cleanup.

  1. **`ChecklistRow` (117 → ~40), the best return.** Four separable concerns sharing a function only because they share a row: focus tracking (`hadFocus`, `LaunchedEffect(autoFocus)`), the drag handle, the text field, and the due/delete affordances. Pull the handle and the focus block out first — the handle is self-contained and is the piece with the subtlest history, since the gesture originally never fired at all (`BasicTextField` claims long-press for selection, which is why it has a dedicated `≡` and plain `detectDragGestures`). **Preserve that arrangement exactly**; it is not incidental.
  2. **`StatusLine` (72 → ~30).** A 36-line lifecycle-scoped `ACTION_BATTERY_CHANGED` receiver sits inside a composable whose job is to draw three pieces of text. Extract `rememberBatteryText()`, and the `repeatOnLifecycle` clock loop as `rememberStatusText()` — the same shape twice. **Keep both scoped to RESUMED**: this is the home screen, paused constantly and rarely destroyed, so an unscoped receiver or ticker outlives every use of it.
  3. ✅ **Done 2026-08-23, ahead of the other two — it is T178's prerequisite.** `shell/ShellUiState.kt`, pure, with 17 tests covering exactly the logic that had none. Enter's behaviour for `:apps`/`:notes` depends on the filtered list, so dispatch could not move to the ViewModel until this did.
  3. **`ShellScreen`'s derived state (~60 lines out), and this is the one worth arguing for.** Lines ~172–290 are pure derivation — five `as?` downcasts, then `filteredApps`, `autoLaunchTarget`, `isPlainQuery`, `plainMatchTarget`, `previewText`, `pendingAliasExpansion`. Nothing there touches Compose beyond `remember`. Lifting it into a `ShellUiState` built by a pure function makes it **unit-testable, which none of it is today** — and `previewText` and `plainMatchTarget` are what decide what T1 shows you, so they are among the least appropriate things in the app to have no tests.
  - **⚠️ Leave the four-branch view switch alone** (`if (appsCommand != null) … else if (notesCommand != null) …`). It re-derives the command type the parser already knew, and it collapses on its own under T178 — fixing it here means fixing it twice.
  - **Do not touch these, despite the line counts:**
    - `MainActivity.onCreate` (114) is a flat wiring list with no branching. Splitting it into `wireRepositories()`/`wireLaunchers()` moves lines without reducing anything, and it shrinks as a *consequence* of T178.
    - `damerauLevenshtein` (26) is an algorithm; `firstJsonObject` (25) is a brace-balancing scanner with string and escape tracking. Both are dense because the problem is, and both are well covered.
    - `export` (33), `evaluateAliasDefinition` (24) and `parseChecklist` (20) are single-purpose with their decisions already named and extracted. Long is not the same as complex.
    - `TagChipsRow` (53) is a fair candidate and is **too new** — it landed with T169 and has had one emulator run. Let it settle before reworking it.
  - No behaviour change anywhere in this task, so the existing suite is the check: if a test needs editing, something here was not an extraction.
  - **Done. No test needed editing, which is the signal that these were extractions.** `ChecklistRow` 117 → **63** code lines (`rememberAutoFocus`, `DragHandle`, `ItemTextField`, `DueChip`); `StatusLine` 72 → **23** (`rememberClockText`, `rememberBatteryText`). Item 3 landed earlier as T178's prerequisite.
  - Verified on an emulator, because both halves are lifecycle behaviour a unit test cannot see: the colon still blinks (a 5×25px region alternating between frames — the two dots), the battery still reads, and **drag-reorder still works**, which was the piece most at risk since the gesture only fires from its own handle.
  - 🔎 Worth knowing for the next person who checks the blink: a lit-pixel *count* over the time cell reports it as static. Compare frames with `ImageChops.difference` and look at the bounding box instead.

- [x] **T182** — One ranked candidate list, replacing three matched collections  
  `P1` `L` · ADR-0010
  - **Usability testing: the command line is "all over the place."** Three collections matched three different ways — reserved commands by an exact registry walk, aliases by an exact map lookup, apps by the fuzzy ladder — related only by a hard-coded cascade. A typo in an app name was forgiven; the same typo in a command token was not.
  - Built as `command/CommandCandidate.kt`: a `Candidate` is an app, alias or command, carrying the `target` string `CommandResolution` already records — one identity, not a third one.
  - **🔎 The ladder was extracted, not rewritten.** `scoreLabel(label, query)` lifts the seven rungs out of `AppEntry.matchAgainst` so aliases and command tokens score through the same function. All 43 `AppMatchingTest` cases pass untouched, which was the point of extracting rather than generalising the signature.
  - **⚠️ The literal spec would have broken `chr` → Chrome.** "Exact, then edit distance, then AI" reads naturally but `AppMatching.kt` already explains why edit distance alone is the wrong instrument: `chr` against Chrome is 3 edits out of 6, scoring 0.5 — under the 0.70 typo floor. The ladder lives *inside* the fuzzy stage instead.
  - **T0-before-T1 is now structural.** The exact pass completes before any fuzzy scoring, so an alias named `cam` beats a fuzzy Camera match by construction rather than by an ordering rule imposed on top.
  - Ranking is band → usage → coverage → label, the last purely so the list never reshuffles under equal candidates while you read it.
  - 20 tests, including the two guards that matter: a heavily-used weak match never outranks an unused strong one, and an exact match anywhere beats a fuzzy match anywhere.

- [x] **T183** — Usage index for frequency ranking  
  `P1` `M` · after: T182 · ADR-0010
  - `command_usage(target, count, lastUsed)`, a derived index in the ADR-0007 mould — rebuildable, never synced, holding nothing `command_log` does not already imply.
  - **🔎 Why not just query `command_log`.** It is keyed `(input, target)` because it records *phrasings* — `chr`, `chrome` and `chro` are three training examples sharing an outcome. Ranking wants one number per target summed across every way of asking, which is the opposite aggregation. Giving the training log a second, hot-path job would also turn its failure modes into interaction failures.
  - **⚠️ One call, two writes, two different gates.** `recordResolution` still logs a T3 miss — those are §8's `unknown` examples — but must not count it: `UNRESOLVED` is the empty string, so counting it builds one meaningless row every unmatched line increments. The `isTrainingExample` gate (alias definitions) continues to cover both.
  - Migration 3 → 4, created empty. It *could* have been seeded from the log with one `INSERT ... SELECT`, and deliberately is not: a migration is the one place that must never fail, and ranking with no data simply falls back to the pre-T182 coverage order.
  - Instrumented: 59 tests pass (was 57), including a new case proving the migration leaves `command_log` intact — the obvious mistake being to "move" data that must exist in both shapes.

- [x] **T184** — Wire the shell to the unified matcher
  `P1` `L` · after: T182, T183 · ADR-0010
  - `dispatchCommand` picks the top ranked candidate instead of walking the cascade; `ShellUiState` derives the same ranking, so `plainMatchTarget` and `pendingAliasExpansion` are gone and the preview reads what Enter will act on.
  - **Three pause timers collapsed into one.** `ShellScreen` had separate `LaunchedEffect`s for alias expansion, app auto-launch and note auto-open, each with its own `delay` and a guard yielding to the one above it — a hand-written cascade. The ranking decides *what*; the timer decides only *when*. The `alarm`/`a` trap survives, since the clock still restarts on every keystroke.
  - **⚠️ The log had quietly stopped agreeing with the launcher.** `resolutionOf` still re-derived its answer from three exact matchers mirroring the old cascade, so once tokens became fuzzy-matchable at T182, `:aps` ran `:apps` and was logged as a T3 *miss* — training data asserting that a line which worked was unresolvable. Exactly what `classifyResolution`'s own comment warned about ("a second opinion that could disagree with the first"), arriving by a route it did not anticipate. It now reads the same ranking, and `classifyResolution` is deleted rather than left as a second path.
  - **⚠️ Tier now means the *stage* that answered**, not which lookup ran: exact is T0, fuzzy is T1, whatever kind of candidate it was. A fully typed app name logs T0 where it used to log T1. Existing rows keep their old meaning, which is survivable precisely because the log records what happened rather than a label (§8 reconciles at hand-labelling).
  - **🔎 Fuzzy matching gave the alias recursion a loop it never had.** `evaluateAliasDefinition` stops an alias *pointing at* another, but nothing stops an expansion from fuzzy-matching some unrelated alias's *name* — and that pair ping-pongs forever. `includeAliases = false` on the second pass makes one substitution structural rather than a property of the data. Pinned by a test that hangs without it.
  - **🔎 `:tasksDone` and `:tasksdone` were two candidates for one command.** The camelCase spelling is a second registry entry so that it also enters browse mode; under case-insensitive matching both matched equally, which would have split that command's usage count across two rows and shown a phantom "+1 more". Found by the registry-wide property test, not by reading the code. `buildCandidates` dedupes by case.
  - **Departure from the plan: `:apps` keeps its substring filter.** The plan said scope the unified list; doing that would break the view. Its auto-launch fires when the filter narrows to exactly one row, and SUBSEQUENCE alone matches dozens of labels on a real device — so a fuzzy `:apps` would rarely narrow to one and the affordance would silently vanish. A filter and a matcher answer different questions.
  - **A pause commits an app or an alias, never a command.** Before this, that fell out of `parseReservedCommand` being exact, so no timer ever saw a half-typed token; now that they are fuzzy-matchable it has to be said out loud, or pausing mid-`:notes` opens the editor on `:note`.
  - Usage counts are held as `ShellViewModel` state and refreshed after each command, not read per keystroke — ranking is pure and must not await Room on the fast path. Threading them to the shell is what keeps the preview naming what Enter opens.
  - Dead code removed with the cascade it belonged to: `topMatch`, `matchPreview`, `classifyResolution`, `reservedCommandToken`.
  - **790 unit tests, 0 failures; lint 0 errors.** `CommandDispatchTest`'s cascade cases pass unedited, including `an alias beats a fuzzy app match` — the T0-before-T1 property, now structural.
  - ⏳ **Owed: an on-device pass.** Type `ca` before and after using Calculator several times and confirm the top candidate changes; confirm `alarm` is still reachable with `a` aliased; confirm one timer means one commit; `:export` to confirm the log still reads correctly.

- [ ] **T185** — SLM stage in the unified pipeline  
  `P2` `M` · after: T184, T036 · ADR-0010
  - **⚠ BLOCKED ON T087 (2026-09-24).** T036 wired T2 into dispatch, but `NoInferenceEngine` is what ships, so there is nothing to run inside the pause window until the first-run download puts a model on the device. Starting before T087 would be building against a tier that cannot fire.
  - The third stage, behind exact and fuzzy. Runs *inside* the one-second window rather than after it, so the deterministic answer paints immediately and the model either beats the budget or does not.
  - Deferred behind T036 — there is no engine to call yet, and `NoInferenceEngine` already exercises the absent-model path.

- [x] **T187** — Launcher listed only system apps after a reboot  
  `P0` `M` · after: T003
  - **🐛 Found by daily-driving, 2026-09-22.** The morning after a reboot the launcher showed 17 apps where there are 400: Gaming Hub, Gallery, Clock, Play Store, Messages and the like, with Gmail, Spotify, Firefox and Claude simply absent. Typing `gm` — the single most-used input in the whole T014 log, 252 uses, always Gmail — fuzzy-matched **Gaming Hub** and launched it.
  - **The reported second symptom, "poor performance, it may be crashing and restarting", was neither.** The crash buffer was empty, the process had been up 30 minutes unbroken, and the `ActivityRecord` token was unchanged across dozens of resume/pause cycles, so the Activity was never destroyed. What it was: the launcher auto-launching the *wrong* heavy Samsung app on nearly every attempt, and being dismissed straight back out. Worth writing down because the bug report and the fault were in different subsystems, and chasing the words rather than the evidence would have gone to Compose.
  - **The first diagnosis was wrong, and the test that killed it is the one worth keeping.** Direct Boot was a good fit for the story: the launcher is the HOME app, so it is created behind the lock screen, `queryIntentActivities(intent, 0)` defaults to `MATCH_DIRECT_BOOT_AUTO` and returns only direct-boot-aware components while the user is locked, and the index was built once in `onCreate` and never re-read. Every part of that is true. It was still not what happened: **force-stopping the app and letting it rebuild its index while unlocked produced the same 17 apps.** The bad data was not a stale cache.
  - **Root cause: device-side package-visibility state that the reboot lost.** The app could see exactly the platform's `forceQueryable` set and nothing else — checked package by package, every visible app was on that list and every missing one was not. The `<queries>` declaration was granting nothing, while being present and correct: `aapt2` confirmed the element in the installed APK, and a diff against the v0.4.0 APK that had worked for a month showed **identical manifests** — same `<queries>`, same `targetSdk 33`, same permissions. Reinstalling the same APK restored all 400 apps immediately. So the declaration was right, the app had not changed, and the grant had evaporated across a reboot.
  - **Fix, in two parts, because one of them is not a fix.**
    1. **`QUERY_ALL_PACKAGES`**, kept alongside `<queries>` rather than replacing it. A normal permission granted at install and persisted like any other, rather than a filter grant derived at runtime, so it does not rest on the mechanism that failed. A launcher is an explicitly permitted use of it, and this app is sideloaded from a GitHub Release and never goes near Play, so the policy question does not arise. Lint's `QueryAllPackagesPermission` is an error-severity compliance check and is suppressed **on that one element** with the reasoning written beside it, not switched off in `lint.xml`.
    2. **The index now re-reads itself** — on `ACTION_USER_UNLOCKED`, and on window-focus regain behind a one-minute [`RefreshGate`](../android/app/src/main/java/com/flambeauriver/dailydriver/apps/RefreshGate.kt). **This would not have fixed the incident**, since the system was filtering and a re-read returns the same short answer; it is here because the app had no way to recover from *any* bad read, and the only cure was a force-stop nobody would think to try.
  - ⚠️ **`ACTION_USER_UNLOCKED` needs its own `IntentFilter`.** The package-change filter sets `addDataScheme("package")`, which applies to the whole filter rather than per action, so adding the unlock action there would compile, register, and never fire. The same silent shape as the dead `[ObservableProperty]` bindings on the desktop side: no error, no warning, just nothing happening.
  - **`AppIndex.entries` is now `@Volatile`** — `refreshIfStale` writes it from `Dispatchers.Default` while the matcher and the UI read it from the main thread. The list itself is immutable, so safe publication of the reference is the whole requirement.
  - **🔎 A test caught the fix's own bug before the device did.** `RefreshGate` was written with the comment that a monotonic clock made a backwards jump unreachable, and a test asserting exactly that failed: `now - last < interval` is *true* for a negative elapsed, so a clock that went backwards would have locked refreshes out for the size of the jump. Hardened to `in 0 until interval` rather than weakening the test to match the code.
  - **Verified:** 799 unit tests (was 792, +7), 0 failures; lint 0 errors, 14 warnings, unchanged. **Verified on-device**: the reinstall that proved the diagnosis also restored the full list, confirmed by screenshot with the third-party apps back.
  - **✅ The reboot half is now verified too (2026-09-22, by the user).** `android-v0.4.2` installed, phone restarted, full app list present. That is the half that actually mattered: v0.4.1 also looked correct immediately after its install, and only failed after a restart, so a release verified without a reboot would have proved nothing. `QUERY_ALL_PACKAGES` survives where the `<queries>` grant did not.
  - **What the fix is really resting on, stated plainly:** the grant now comes from a permission recorded at install and restored with the package database, rather than a visibility relationship re-derived at boot. `<queries>` is deliberately kept anyway — it is the honest declaration of what the app needs, it is what a reader should see first, and if the underlying platform bug is ever fixed it is the one that should be doing the work.

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
- [x] **T027** — Keyboard shortcuts so the editor stays keyboard-finishable  
  `P0` `S` · after: T022
  - **Re-specified for the actual device** (check-in, 2026-08-21). `Esc`, `Ctrl+Enter` and `Tab` do not exist on the S20 FE; its only spare key is the IME action. Implemented as **Enter on a trailing empty checklist row finishes the note**, the idiom every checklist app uses, plus the title's action moving to the body. §4.6 updated to match rather than left describing keys the target device has never had.
  - **A prose body still cannot be finished from the soft keyboard, and that is accepted rather than hidden.** Making the IME action finish the note would take Enter away from newlines, which is a worse trade for the long-form journalling this surface also serves. A prose note ends with a tap on ✓. If a hardware keyboard is ever in scope, `Esc`/`Ctrl+Enter` remain the right shapes and nothing here forecloses them.
  - The condition is extracted as `enterFinishesNote` and tested, rather than left inline in a composable no test can reach — on a soft keyboard it is the only spare key there is, so it is load-bearing.
- [x] **T138** — Undo snackbar for destructive actions  
  `P1` `M` · after: T023, T024
  - Autosave removed the `:w` safety net — a mis-tap is now instantly durable and syncs. Undo replaces it.
  - **An inline row, not a Material `Snackbar`** (check-in). There is no `Scaffold`, `Snackbar` or dialog anywhere in this app; a floating Material surface over a monospace terminal would be the first, and would look like it came from a different program. `DiscardConfirmation` set the inline precedent, and this takes its slot.
  - **The ✗ confirmation is retired**, which `android/CLAUDE.md` had said should happen the moment undo existed. Asking first is what you do when you *cannot* undo; doing both charges the same friction twice.
  - **One action deep, not a stack.** `UndoableAction` is a label plus a closure over whatever the action replaced, so undo needs no history: there is only ever one offer and it carries everything it needs. A stack would mean reasoning about undoing a delete after three unrelated edits, which is a much larger promise than "that wasn't what I meant".
  - **The offer outlives the editor**, because discarding a note closes it — an undo living inside the editor would be one you could never reach. `ShellScreen` renders it whether or not a draft is open, auto-dismissing after 8s (longer than a snackbar's 4s, since it replaces a confirmation and nothing competes for the space).
  - **🐛 The riskiest claim was that re-saving a discarded draft un-tombstones it.** `toNote()` yields `deleted = false` and `@Upsert` updates every column, so it should — but that is the same "reasonable reading of Room" that was wrong at T018, and if it were wrong here undo would *look* like it worked while the note stayed deleted. Pinned by instrumented tests covering restore, tag re-indexing, and the outbox row that propagates the resurrection.
- [x] **T139** — Tag chips row + inline #hashtag parity  
  `P1` `M` · after: T021, T019
  - **Chips edit the body's hashtags** (check-in). Tapping a chip strips that tag from the body; `+ tag` appends a `#` and hands the cursor back to the body, so the name is typed in the field that already exists.
  - **The "union them" suggestion this task originally carried is the wrong answer, and was rejected.** `NoteDao.upsertLocal` derives tags from the body and deliberately ignores any set passed in; a union means a tag can exist in `note_tags` with nothing in the body backing it, which requires removing that guarantee and creates two writers of one fact — the drift §6.2 and ADR-0004 exist to prevent.
  - **Removing a chip removes every occurrence, not the first.** A tag is a property of the note rather than of a position in it, so leaving a second `#work` further down would put the chip straight back on the next parse and read as the tap having failed.
  - **No second text input.** One would need its own validation and its own idea of what a tag may contain, and would be the second writer again. Typing after a `#` is the gesture that has always created a tag here.
  - 18 unit tests, including that a tag added by chip and one typed inline produce identical `parseTags` output, which is what the task actually asked for.


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
  - **Converting a note to a checklist leaves you typing** (UI tweak, 2026-08-21). A blank note converts to an empty list, so it gets its first row created and focused with the keyboard up; a note with prose already has rows, so the cursor goes to the end of the last one rather than appending a spurious blank to real content.
  - **Focus intent moved into the ViewModel** while doing it. `ChecklistView` used to guess a new row's index as `items.size`, which was right only because `addItem` happens to append — anything that reordered on insert would have focused the wrong row silently. The ViewModel is the only thing that knows where an item landed, so it now says so, and the view consumes a one-shot `pendingItemFocus`.
  - Verified: 552 unit tests (was 533), 0 failures; lint unchanged at 0 errors / 10 warnings. **Not yet verified on-device** — focus-loss commit and the tap-to-clear target are exactly the kind of thing that has broken on hardware three times in this editor already (T023).
- [x] **T155** — `:task` command  
  `P1` `S` · after: T153
  - `:task buy milk @fri` appends one dated line to a note titled `tasks`, opening or creating it by name. That is T025's existing `byTitle` path, so there is no new storage code.
  - Registers in T140's command table, which carries its own help text, so `:help` cannot drift out of step.
  - **An undated `:task` is due today** (check-in). `:task` means you intend to do the thing, and a task `:tasks` never shows would read as the command silently failing.
  - **The transcript echoes the resolved date**, not what was typed, so `@fri` is confirmed back as a real day. That is the only feedback that a shortcut was understood.
- [x] **T156** — `:tasks` list view  
  `P1` `M` · after: T153, T155
  - Queries the T157 index, grouping results overdue / today / upcoming, soonest first. An indexed lookup on a date column, not a scan.
  - **Ticking from this list writes back to the source note body**, because that is the only place the item is authored. The index follows from that write rather than being updated beside it.
  - Reuses the `:notes` and `:apps` list surface rather than inventing a third one.
  - **Two tap targets per row** (check-in): the checkbox ticks, the text opens the note the task lives in. Mirrors the checklist editor's own row, and keeps the common action at one tap while leaving the context reachable. The row also carries its note's title, since a task list that can't say where a task lives is hard to act on.
  - **`:tasksdone` is a separate command** for completed tasks (check-in), rather than a toggle inside `:tasks`. The outstanding list answers "what do I still have to do", and mixing finished work into it dilutes the one question it exists to answer.
  - **Lowercased from the requested `:tasksDone`.** Every other token is lowercase and matching is case-sensitive, so a capital means a shift-tap mid-command on a phone. The camelCase spelling is registered as a hidden alias, the same arrangement `?` has with `:help`, so both work.
  - **⚠️ `:task` → `:tasks` → `:tasksdone` is a three-way prefix chain**, the trap `android/CLAUDE.md` records from `:note`/`:notes`. No new code was needed: `autoSpaceableTokens` already excludes any token another extends, so `:task` and `:tasks` lose auto-space automatically. A test now pins that the mechanism holds three deep rather than only for a pair.

- [x] **T157** — Derived `tasks` index table  
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

**Prep done ahead of the spike (2026-08-22).** Most of this section never needed a model, and since the spike is timeboxed, everything built beforehand shrinks the part that is actually constrained. T028 and T033–T035 are pure logic with no MediaPipe dependency and no weights, and they are needed **whichever way the gate goes** — even the few-shot fallback needs JSON extracted, repaired and validated. What remains inside the timebox is exactly the part that requires hardware: T029–T031, T037, T038, T039.

- [x] **T028** — Define InferenceEngine interface  
  `P0` `S`
  - Decouples MediaPipe now, enables LiteRT-LM later.
  - **`infer` returns the raw string, deliberately.** An engine returning a parsed object would have to decide what to do with malformed output, and that is a policy question the interface has no business answering — it belongs to T033–T035.
  - **Null is an ordinary outcome, not an error.** Not loaded, or inference failed, both route to T3. §4.2 is explicit that the SLM may fail and the launcher may not.
  - **`NoInferenceEngine` is shipped, not a test double.** It is what runs until T036 wires a real engine in, and on any device where the download has not happened or T037 came back negative. A real implementation of "no model" means that state is exercised in production rather than being a null check nobody tests.
- [x] **T029** — Add MediaPipe dependency, init GPU backend  
  `P0` `M` · after: T028
  - LoRA requires `backend='gpu'`.
  - **Pinned at `com.google.mediapipe:tasks-genai:0.10.35`**, the latest published release (2026-04-27). The guide still shows 0.10.27, so the API was read off the AAR instead — which is how the §4.4 correction below got found.
  - **`abiFilters` is required by this dependency, not chosen for tidiness.** `tasks-genai` ships `libllm_inference_engine_jni.so` for four ABIs and native libraries are packaged *uncompressed*, so each costs its full size: arm64-v8a 26.6 MB, armeabi-v7a 19.2 MB, x86 32.7 MB, x86_64 30.2 MB. Unfiltered, a 22.0 MB release APK would pass 130 MB to carry three copies the target device can never execute. **Measured: release is now 51.2 MB, arm64-v8a only.**
  - **⚠ Debug keeps x86_64, and leaving it out would have broken CI in a way that reads as a broken emulator.** An APK containing native libraries with none matching the device ABI is refused at install with `INSTALL_FAILED_NO_MATCHING_ABIS`, and the instrumented suite runs on an API 33 **x86_64** emulator. That suite is the only thing that has ever caught a bug in the Room layer, so it has to keep installing even though it can never run inference — it is `-gpu swiftshader_indirect`, and Google's guide says the API "does not reliably support device emulators".
  - **The three `<uses-native-library>` OpenCL declarations came free.** The AAR's own manifest declares `libOpenCL.so`, `libOpenCL-car.so` and `libOpenCL-pixel.so` as `required="false"`, and manifest merger pulls them in; verified present in the packaged release manifest rather than assumed. Nothing was hand-written, which is the opposite of what T187's `<queries>` work would lead you to expect.
  - **`ModelLocation` is the whole testable half, and it points at `getExternalFilesDir`, not `filesDir`** (user's call). Both are app-scoped and both clear on uninstall, but only the external one takes an `adb push` without `run-as` — and `run-as` does not work on this project's daily driver, because the phone runs a signed release build. `filesDir` would mean T031's development path and T087's shipping path could never be the same path, and the one we could exercise on the target device would be the one we don't ship. Supersedes §4.5's "app-private storage" for the model file specifically.
  - **Presence is three conditions, not `exists()`.** An interrupted `adb push` leaves a zero-byte file; a push with a trailing slash leaves a *directory* with the model's name. Both would reach MediaPipe and fail inside native code as an opaque load error instead of "the model is not here yet". **Explicitly not an integrity check** — no size threshold distinguishes a complete model from half of one, so that stays §4.5's SHA-256 verification at T087, where the expected hash is actually known.
  - **`MediaPipeEngine` ships unwired, deliberately** (user's call). `NoInferenceEngine` is still what the pipeline uses; T036 wires T2 in, after T037's gate. An engine reachable only from a test cannot put the SLM on the launcher's fast path by accident, and that is a root hard constraint rather than a preference.
  - **Load failure latches to `Failed`; an inference failure returns null and stays `Loaded`.** A departure from T028's "load or inference failed" wording, made on purpose: one generation failing is the model declining one input, and condemning the engine would disable T2 for the rest of the process over a single awkward prompt. Null already routes to T3, so nothing is lost by staying loaded.
  - **Catches `Throwable`, not `Exception`**, because the failure most worth surviving is `UnsatisfiedLinkError` — an `Error` — which is what a missing or wrong-ABI native library throws. §4.2 does not permit the home screen dying for an optional feature.
  - **What this task proves, and what it does not.** There is no model yet, so "it works" is not available. Five instrumented tests pin what is: the native library **loads** (`System.loadLibrary`), a missing model reads as `Absent` rather than `Failed`, a junk non-empty file reaches MediaPipe and comes back `Failed` without an exception escaping, `close` is safe twice on an engine that never loaded, and `getExternalFilesDir` actually resolves on the device. That last one is worth knowing before spending a 2.7 GB push on the path. **The failure changed shape from "no MediaPipe at all" to "no weights", which is the whole of T029.**
  - **↻ §4.4's config list was wrong and is now corrected.** The options split across `LlmInferenceOptions` (model) and `LlmInferenceSessionOptions` (per generation), and **`loraPath` is on the session while `supportedLoraRanks` is on the model** — so applying an adapter is two steps in two places, and T037 has to exercise both or it proves nothing. `setSupportedLoraRanks` is deliberately *not* set yet: the rank must match a trained adapter, none exists, and guessing one would look like configuration and behave like a bug.
  - **⚠ `setConstraintHandle(long)` exists on the session options**, which is a constrained-decoding hook — the single cost ADR-0003 accepts giving up, and the justification for the whole five-layer defence. No class in the AAR builds one and `com.google.mediapipe` publishes no companion artifact, so this is a question rather than a reversal. **Answer it before T032**, not after.
  - Verified: **806 unit tests** (was 799) and **64 instrumented** (was 59), 0 failures, run on an API 33 x86_64 AVD matching the CI configuration. Lint 0 errors / 14 warnings — the baseline was 10, and only **one** of the four new ones came from this change (`ChromeOsAbiSupport`, suppressed inline with reasoning, since a home launcher pinned to one phone does not owe ChromeOS 30 MB). The other three are `GradleDependency` hits that appeared because AndroidX published new versions; `tasks-genai` is **not** among them, which independently confirms 0.10.35 is current.
  - **⚠ Not verified on the S20 FE.** The phone was not reachable over adb during this task, so the arm64-v8a half of the native-library check has been reasoned about rather than run. T031 installs on the phone anyway and will answer it as a side effect — but if the model fails to load there, **check this first** rather than suspecting the weights.
- [x] **T030** — Acquire + convert base model to .task  
  `P0` `M` · after: T029
  - **✅ Acquired 2026-09-23; no conversion was needed.** `litert-community` publishes pre-converted artifacts, so the acquire half is a gated download and the convert half does not arise until section 12 has a fine-tuned checkpoint of its own. Do not read this tick as "the conversion pipeline works" — that is still unexercised.
  - **Everything official is gated.** Hugging Face returns 401 without a token for every `.task` under `litert-community`, and Kaggle's `google/gemma` needs an access request. Acceptance is a licence action the user has to take; §4.5 already anticipated this and it lands on T087.
  - **⚠ The `-web` variants are not the Android ones.** `gemma3-270m-it-q4_0-web.task` is 249 MB and works, but only once renamed off `.task` — see T031. `litert-community` also publishes non-`-web` `.task` files (`gemma3-270m-it-q8.task`, 304 MB) which are presumably genuine zip bundles; untested.
- [x] **T031** — adb push model, load, first successful inference  
  `P0` `M` · after: T030
  - `/data/local/tmp/llm/` during development.
  - **✅ Done 2026-09-23. First model load and first inference on the S20 FE.** Gemma-3 270M q4, 249,233,408 bytes, `state=Loaded`, load **6.9 s**, and a real answer back through `MediaPipeEngine` — the production class, not the raw API.
  - **⚠ The file extension is load-bearing, and it cost an hour to find.** MediaPipe dispatches the loader on it: `.task` takes the zip-bundle path, anything else takes the flat-flatbuffer path. The **same bytes** were rejected as `model.task` with `UNKNOWN: Unable to open zip archive` and loaded on **both GPU and CPU** as `model.bin`. Every artifact this project can obtain is a flat flatbuffer beginning `1c 00 00 00 TFL3` — Kaggle's `gemma-2b-it-gpu-int4.bin` and `litert-community`'s `-web` variants alike — so **`ModelLocation.DEFAULT_FILE_NAME` was wrong and is now `model.bin`**, pinned by a unit test.
  - **🐛 That default was a real bug in our own code**, shipped at T029 and invisible to 806 green tests, lint and the compiler, because nothing could exercise it without a device and a model. Same shape as the T018 outbox cascade: only the instrumented path could see it.
  - **⚠ `connectedDebugAndroidTest` wipes the app's external files dir**, because it reinstalls. Pushing the model then running Gradle loses the model. Push *after* `installDebug`/`installDebugAndroidTest` and drive the test with `am instrument` directly.
  - **Latency, measured three times warm: 7150 / 7078 / 7049 ms.** That is ~4.5x §2's 1.5 s p50 target and it would be the wrong conclusion. `rawChars=1188`: the model generated roughly 300 tokens when the JSON wanted is about 20, running on until `maxTokens`. **~43 tokens/s is a healthy decode rate for 270M q4 on an Adreno 650**, and the ~20 tokens actually needed project to well under 0.5 s. The problem is that nothing stops generation, which is T032's job and T190's.
  - **The 270M echoed the prompt's placeholder**: `{"intent": "...", "slots": {}, "confidence": 0.0}`. Schema-shaped and semantically empty. Two things follow. T035 would reject `"..."` as not a member of the label space and abstain to T3, which is the layered defence working exactly as designed. And **T190 would make it unemittable** — `Schema.enum` forces a choice among the eighteen — which is the most concrete argument for [ADR-0011](adr/0011-constrained-decoding-and-fine-tuning-as-experiment.md) yet produced.
- [x] **T032** — Prompt template with inline schema + few-shot  
  `P0` `M` · after: T031
  - Low temperature (~0.1). Extraction, not creative writing.
  - **✅ Done 2026-09-23, measured on the S20 FE.** `slm/PromptTemplate.kt` plus Gemma turn markers on the session. The 270M went from echoing the prompt's placeholder to answering correctly: `remind me to call the dentist tomorrow` → `{"intent": "add_task", "slots": {"text": "call the dentist", "due": "tomorrow"}, "confidence": 0.9}`. Right intent, right slots, right date token.
  - **✨ The turn markers were the whole fix, and they are not part of the prompt.** An instruction-tuned Gemma is trained on `<start_of_turn>user … <end_of_turn>` and emits that token to finish; handed a bare string it has no turn structure, never produces it, and generates until `maxTokens`. That is what T031 actually measured. They are configured on the session through MediaPipe's `PromptTemplates` in `MediaPipeEngine`, **not** in `PromptTemplate`, because they describe the *model* rather than the task — so the prompt survives a model swap (T039 is open) and the LiteRT-LM move at T091. **A non-Gemma model needs different markers**; Phi-2 is on the LoRA list and does not use these.
  - **Measured, warm, three runs each:** infer **7150/7078/7049 ms → 3541/2886/3258 ms**, and `rawChars` **1188 → 554**. Roughly half the latency for a correct answer instead of a wrong one.
  - **⚠ Still ~2.4x over §2's 1.5 s p50, and the cause is now exactly known.** After answering in 101 characters the model **fabricates further exchanges**, replaying the few-shot examples back as new question-and-answer pairs until it hits the token limit. About **80% of generated tokens are waste**. Strip them and this lands near 0.7 s, inside budget.
  - **Correctness is unaffected by that, which is T033 doing its job.** `firstJsonObject` takes the first balanced span, so the invented continuations are discarded before `parseIntent` ever sees them. The layered defence absorbed a real failure mode on real hardware rather than a hypothetical one.
  - **This is T190's remaining half.** A constrained decoder terminates at the end of the object, so `ToolCallOnly` makes the continuation unemittable rather than merely discarded. T032 bought the correctness; T190 buys the latency.
  - **The label list is derived from `Intent.entries`, never written out.** `StructuredOutputTest` still carries a hand-maintained copy of these wire names that was caught being wrong on 2026-09-21, and the prompt would have been the third place to keep in step. A property test asserts every enum member reaches the prompt, so adding a label cannot forget it.
  - **The few-shot examples are run through `parseIntent` by the tests**, so an example we teach with that our own validator would reject is a failing test. **The `unknown` example is asserted to parse to null**, which pins both halves of that contract: abstention and failure are deliberately the same outcome, because the caller drops to T3 in both.
  - **Examples chosen from the T014 logs, not from convenience**: `open_app` and `web_search` dominate real usage, `add_task` is the most structurally awkward slot shape, and `unknown` is there because a block of confident hits teaches that guessing always pays.
  - **The command is collapsed to a single line before it goes in.** Input comes straight off the prompt line, so a newline plus a forged `Command:` would otherwise let typed text append its own exchange and steer the answer. The invariant the test pins is *line-initial* markers, which costs nothing and avoids mangling text a user legitimately typed.
  - `MAX_CHARS` guards the prompt's size, because prefill is paid on every single command and a few-shot block grows one well-meaning example at a time. Raise it with a measurement, not to make a test pass.
  - Verified: **816 unit tests** (was 807), 0 failures; 9 of them new and mostly property tests over the label space.
- [x] **T033** — JSON extraction (first balanced brace span)  
  `P0` `S` · after: T032
  - **Balanced counting, not regex, and `slots` is why.** It is always a nested object (§4.3), so a non-greedy `\{.*?\}` truncates every real payload and a greedy one swallows two objects. Depth counting is the only thing that gets both right.
  - **Braces inside strings are tracked, with escapes.** A note titled `{weird}` is legitimate content, and a naive count closes the object early — turning valid JSON into invalid JSON that the repair layer then cannot fix.
  - Truncated output returns null rather than a partial object. `maxTokens` makes that a real outcome, and T3 is the right answer to a reply that was cut off.
- [x] **T034** — JSON repair pass (fences, trailing commas, unquoted keys)  
  `P0` `M` · after: T033
  - No grammar-constrained decoding in MediaPipe — defend in layers.
  - **Syntactic repairs only.** Nothing invents a field or corrects a value. A pass that supplied a plausible `confidence` when the model omitted one would be fabricating the exact number the abstention threshold depends on, turning "the model did not answer" into "the model answered confidently".
  - Pinned by a test that repair is a **no-op on already-valid JSON**, or the pass becomes a source of corruption in the common case.
- [x] **T035** — Schema validation + confidence threshold, drop to T3  
  `P0` `M` · after: T034
  - **Blocked on §4.3's label space, which is now settled** (check-in). It had no label for four of the ten reserved commands. Every command now maps to exactly one intent, plus `open_app`, `check_item`, `define_alias` and `unknown`.
  - **Settled, not frozen** (user's call): adding a label stays cheap right up until §8 freezes the held-out split, and daily-driving is precisely how a missing command gets found. **T014's design is what makes that safe** — the log records the *resolution*, not a label, so usage of a command invented next month is already being captured and can be labelled retroactively.
  - **An invented or explicit `unknown` intent abstains.** A model that hallucinates a label is not trusted; abstention is a first-class desirable output (§4.3), not a failure.
  - **A missing confidence fails rather than defaulting**, for the same reason the repair pass will not invent one.
  - `DEFAULT_CONFIDENCE_THRESHOLD` is provisional and named rather than inlined: §8 measures abstention quality and T083's benchmark is what sets it honestly.
  - **↻ Demoted to the fallback path 2026-09-22** by [ADR-0011](adr/0011-constrained-decoding-and-fine-tuning-as-experiment.md). T190 makes schema-valid output structural, so this layer stops being the thing standing between the launcher and garbage. **It is not removed**: constrained decoding is probably GPU-only, so this still runs whenever the constraint is unavailable. The threshold and the `unknown` abstention are untouched — constraining shape says nothing about correctness.
  - 27 tests covering all three layers, including realistically messy output with fences, prose, a bare key, a trailing comma and smart quotes at once.
- [x] **T036** — Wire T2 into the command pipeline  
  `P0` `M` · after: T035
  - SLM never on the fast path.
  - **✅ Done 2026-09-23. T2 is wired, and the cascade is complete for the first time.** It runs at exactly one point: the line in `dispatchCommand` where `rankCandidates` returns nothing, which is after T0 and T1 have both missed. **The hard constraint is structural rather than checked** — the deterministic tiers have already answered or declined by the time that line is reached, so the SLM cannot be on the fast path.
  - **✨ T2 gets no actions of its own.** `slm/IntentCommandLine.kt` maps a validated intent back to a *command line*, which is then dispatched as if typed — so `add_task` runs the same code `:task` runs and inherits its due-date shortcuts, Room write and outbox row for free. The alternative was a second `when` duplicating twelve commands, which is the shape `android/CLAUDE.md` warns about under "command dispatch has exactly one home".
  - **The `when` over `Intent` is exhaustive, which is the durable fix flagged since 2026-09-21.** Adding a §4.3 label now fails to compile until someone says what it does. A test lists the three labels that are *deliberately* unmapped so a future label cannot be waved through with `-> null` to make the build green; a second test asserts every token produced is a real registered command, which catches `:tasksDone` before it reaches a device.
  - **⚠ `check_item` and `create_checklist` have no command line, and that is correct.** `check_item` describes tapping a checkbox — the item it names is on screen, not typeable. `create_checklist` is a conversion applied to an open note (T138). Mapping either to `:note` would open an editor and *look* like the command worked, which is worse than refusing.
  - **⚠ `allowSlm = false` on the recursion, and it is load-bearing.** `open_app` synthesises a bare app name, which can itself miss; without the guard that miss would ask the model about the model's own output, forever. Same shape and same reason as `includeAliases`. Aliases stay *enabled* on the recursion, deliberately: if the model says open `gm` and `gm` is an alias, expanding it is what the user meant.
  - **Everything T2 can fail at lands on T3.** No model, still loading, inference declined, unparseable output, a label outside §4.3, an explicit `unknown`, confidence below threshold, or an intent with no command line — all return null from `resolveIntent`/`commandLineFor` and fall through. §4.2 is explicit that the SLM may fail and the launcher may not.
  - **Single-word input never reaches the model**, pinned by a test. §4.2 sends only multi-word prose to T2, and T1 already had its shot at a one-word app name — asking the model there would put seconds of latency on a plain typo.
  - **`NoInferenceEngine` is still what production uses**, supplied through `CommandEnvironment` like every other capability. So wiring T2 in changes nothing on a device with no model, which is what makes this safe to ship before T087. A test pins that too.
  - **↻ The T3 message changed, because it had gone stale the moment this landed.** It used to read "natural language needs the SLM (not built yet)". The SLM *is* built now, and the branch is reached after it has declined for any of seven reasons the message cannot distinguish between, so it now reads "couldn't turn that into a command". Its test was rewritten to assert the real invariant — that prose is never described as a failed app-name match — rather than a particular wording.
  - Verified: **835 unit tests** (was 816), 0 failures; 19 new, mostly property tests over the label space and the tier boundary. Lint unchanged at 0 errors / 14 warnings.
  - **🐛 Shipped two instrumented regressions, caught by CI after three red merges.** `ExampleInstrumentedTest` still asserted the applicationId that T031's `applicationIdSuffix = ".debug"` had changed, and `MediaPipeEngineTest` still hardcoded `"model.task"` after T031 changed `DEFAULT_FILE_NAME` to `model.bin` — a duplicated constant in the very place meant to be its single source of truth. Both now derive rather than restate: a prefix check with the suffix's reason beside it, and `ModelLocation.DEFAULT_FILE_NAME` referenced directly.
  - **The process failure, not the code failure, is the thing worth keeping.** The whole spike run drove single test classes through `am instrument` and never ran `connectedDebugAndroidTest` again. Unit tests, lint and the compiler stayed green throughout, exactly as they did for T018's outbox cascade. `android/CLAUDE.md` now says in as many words that a targeted run is for iterating and the full suite is what you run before committing. Verified after the fix: **69 instrumented tests, 0 failures, 5 skipped** (the model spikes, by design).
- [ ] **T190** — Constrained decoding via `localagents-fc`  
  `P2` `M` · after: T031 · **parked — blocked on library support**
  - **⚠ READ THIS FIRST, the bullets below are a trail of two wrong diagnoses before the right one.** The blocker is **vocabulary support in `localagents-fc` 0.1.0** — not the artifact format, not the version deadlock. With the runtime the library declares it answers `Unsupported vocabulary type for Fst-Constraints library`; every other failure seen was downstream of using the wrong runtime. The dependency has been removed. **Do not retry by bumping a version: every cell of the matrix below has been measured.**
  - **↻ `blocks: T032` removed** — T032 shipped on 2026-09-23 without it, and demoting this from `P0` to `P2` follows from that: the prompt and the five layers carry T2 today, and this is a latency optimisation rather than a dependency.
  - **Added 2026-09-22 by [ADR-0011](adr/0011-constrained-decoding-and-fine-tuning-as-experiment.md).** Makes schema-valid output structural instead of probabilistic, which reverses ADR-0003's one accepted cost and demotes T033–T035 to a fallback path.
  - **⚠ Partially done 2026-09-23, and blocked on the artifact rather than on the approach.** The dependency is in, the three costs are resolved, and the chain is verified as far as `createConstraint`. **`createConstraint` then throws `java.lang.IllegalStateException: Invalid end token: 2`** — identically for `ToolCallOnly` **and** for `TextUntil`, so the blocker is the FST provider's tokenizer handling and not the constraint mode. `FstConstraintProvider(handle)` itself constructs without complaint.
  - **❌ That first diagnosis was wrong, and the real one is worse.** The guess was that the flat `-web` artifact lacked tokenizer metadata. The proper bundle (`gemma3-270m-it-q8.task`, 304 MB, verified to contain `TF_LITE_PREFILL_DECODE`, **`TOKENIZER_MODEL`** and `METADATA`) does not fix it — it fails **earlier and harder**, with a native `SIGSEGV` inside `Java_com_google_ai_edge_localagents_fc_FstConstraintProvider_nativeCreateFstConstraintProvider`. The constructor itself, before any constraint is described.
  - **⚠ It is a version deadlock between the two halves ADR-0011 depends on.** `localagents-fc:0.1.0` declares **`tasks-genai:0.10.24`**, and `getSentencePieceProcessorHandle()` hands a raw native pointer across the boundary — passing one between libraries built against different runtime versions is exactly how you get "invalid end token" on one artifact and a segfault on another. Tested directly by pinning the runtime down to 0.10.24, which fails differently and decisively:

    | `tasks-genai` | Gemma-3 270M bundle |
    |---|---|
    | **0.10.35** | runs the model; **SIGSEGV** in `nativeCreateFstConstraintProvider` |
    | **0.10.24** (what `localagents-fc` declares) | **SIGABRT** in the GPU delegate, `ml_drift_cl_kernel_tflite.cc:42 Graph general transformations failed` — it cannot run the model at all |

    0.10.24 predates Gemma-3: the runtime's `LLM_MODEL_TYPE_GEMMA3_300M` enum only appears in 0.10.35. **So there is currently no version of `tasks-genai` that both runs this model and works with this constraint library.** The runtime has been restored to 0.10.35; leaving it pinned down would have silently broken Gemma-3 support and the API shape T029 documented, to buy a constraint that still does not work.
  - **This still does not reopen [ADR-0011](adr/0011-constrained-decoding-and-fine-tuning-as-experiment.md), but it does change what "buildable" means.** The mechanism exists and the call chain is right; what is missing is a *pairing* that supports it. Three ways out, in the order they are worth trying: **(a)** test a model 0.10.24 does support — Gemma 1.1 2B `gpu-int4` is from that era, is already downloaded, and is T039's fallback candidate anyway, so this is nearly free; **(b)** wait for a `localagents-fc` built against a current runtime, since 0.1.0 is the only release and is plainly early; **(c)** bound generation by other means and leave §4.4's five layers carrying the load, which is what ships today.
  - **⚠ Do not simply raise or lower one of the two versions to make this build.** Both directions have now been measured and both fail, differently. Whoever picks this up should start from (a) rather than from a version bump.
  - **❌ Route (a) tested 2026-09-23, and it corrects the diagnosis above.** Gemma 1.1 2B `gpu-int4` (1,346,502,656 bytes, flat `TFL3`) was pushed and run against both runtimes. The full matrix:

    | Model | `tasks-genai:0.10.24` | `tasks-genai:0.10.35` |
    |---|---|---|
    | Gemma-3 270M bundle | SIGABRT, GPU delegate: model unsupported | SIGSEGV in `nativeCreateFstConstraintProvider` |
    | Gemma-3 270M `-web` flat | not tested | `Invalid end token: 2` |
    | **Gemma 1.1 2B `gpu-int4`** | **`Unsupported vocabulary type for Fst-Constraints library.`** | `Invalid end token: 2` |

  - **✨ `Invalid end token: 2` was a red herring.** It appears on **0.10.35 for both models**, which is the signature of the version mismatch rather than of anything about the model: `localagents-fc` reads a handle whose layout it does not understand and gets nonsense. On **0.10.24**, the declared pairing, the handle is read correctly — the constructor succeeds, no segfault — and the library returns a *meaningful* refusal instead.
  - **So the real blocker is vocabulary support in `localagents-fc` 0.1.0, not versions and not artifacts.** With the correct pairing the mechanism works end to end and reaches a genuine decision; that decision is "this model's vocabulary is not one I handle". Two of the three failures were downstream of using the wrong runtime, and only matching versions made the actual answer legible.
  - **Where that points: the FunctionGemma family.** The library ships `GemmaFormatter`, `LlamaFormatter` and **`HammerFormatter`**, all function-calling model families, and the 0.10.35 runtime carries `LLM_MODEL_TYPE_FUNCTIONGEMMA3_300M` as a first-class type. T039 already flagged `FunctionGemma-270M-Mobile-Actions` as on-point for this exact task. **⚠ But neither FunctionGemma repo publishes a MediaPipe-loadable artifact**: `functiongemma-270m-ft-mobile-actions` is `.litertlm` only (LiteRT-LM, not MediaPipe) and `FunctionGemma_270M_Mobile_Actions` is raw `model.safetensors`. Reaching it means either the LiteRT-LM runtime or a conversion, so this is a real lead and not a quick retry.
  - **Recommendation: stop spending on T190 for now and let §4.4's five layers carry T2.** They already absorb the failure mode this was meant to remove — T032 measured `firstJsonObject` discarding the model's invented continuations with correctness intact — and the cost is latency rather than wrongness. Revisit when `localagents-fc` has a release built against a current runtime, or when T091's LiteRT-LM move makes the `.litertlm` FunctionGemma artifacts reachable. **Do not retry by bumping a version**: every cell of that matrix has now been measured.
  - **↻ The dependency was removed 2026-09-23 and the task left open.** Carrying `localagents-fc` cost **6.7 MB of native libraries** on a phone that is somebody's home screen, for a capability measured as non-functional, plus a Guava major bump — and two of those libraries were formatters for **Llama and Hammer** models this project will never use. The release APK went **58.3 MB → 51.2 MB**. `ConstraintSpikeTest` went with it, so the reproduction now lives in the matrix above rather than in code.
  - **Re-adding is one line**, and the order to work in is: check whether a `localagents-fc` newer than 0.1.0 exists and what runtime it declares; if so re-add and run the matrix again before writing anything else. **Do not re-add it speculatively** — it ships native code to a daily driver and earns nothing until a model's vocabulary is supported.
  - **This does not reopen ADR-0011.** The decision was that constrained decoding is the reliability mechanism and fine-tuning is a measured experiment. What is unproven is whether *this artifact* supports it, not whether the mechanism exists — the API surface, the dependency graph and the call chain are all confirmed. If the non-`-web` bundle also fails, the fallback is bounding generation by other means and §4.4's five layers carry on as they already do.
  - **Costs measured rather than estimated.** Debug APK 90.4 MB → **104.4 MB** with both ABIs; release ships arm64-v8a alone so its share is roughly 4–7 MB. `localagents-fc:0.1.0` draws no `GradleDependency` staleness warning, so 0.1.0 is current.
  - **⚠ `org.json` is excluded from the production classpath** and that exclusion is load-bearing. Android ships `org.json` on the bootclasspath, so the bundled jar can never win at runtime and only makes "which `JSONObject` is this?" a question somebody has to answer later. `IntentSchema.kt` has used the framework one since T035. **The version catalog's `org-json` entry is for unit tests and must stay** — there is no framework there (T019), and the unit-test classpath was checked separately to confirm it still resolves.
  - Guava resolves upward, 27.0.1-android → 33.3.1-android. Nothing in this app calls Guava directly; it arrives under `generateResponseAsync`, which `MediaPipeEngine` does not use.
  - Verified after the dependency landed: **816 unit tests**, 0 failures; lint unchanged at 0 errors / 14 warnings.
  - The chain, verified against published artifacts: `LlmInference.getSentencePieceProcessorHandle()` → `new FstConstraintProvider(handle)` → `.createConstraint(List<Tool>, ConstraintOptions)` → `.getConstraintHandle()` → `LlmInferenceSessionOptions.builder().setConstraintHandle(...)`.
  - **Express §4.3's label space as `Schema.enum`** and the slots as typed parameters (`Type` covers STRING, NUMBER, INTEGER, BOOLEAN, ARRAY, OBJECT), with `ConstraintOptions.ToolCallOnly`. That is what makes a nineteenth intent unemittable rather than merely unlikely.
  - **⚠ Do not delete a layer of T033–T035.** Constrained decoding is probably GPU-only — the native library has only `TfLiteOpenClExternalOutputHandler::UpdateConstraintMask` and an error reading `UpdateConstraintMask not implemented for this backend` — so the five-layer path still runs whenever the constraint is unavailable. This task adds a mechanism; it removes nothing.
  - **⚠ Structural validity is not correctness.** A constrained `confidence` field is a number the model invents. T035's threshold and the `unknown` abstention stay exactly as they are.
  - **Three costs to check while wiring it.** `localagents-fc` is **0.1.0**. It pulls **`org.json`** into production, where this project has it as a *test-only* dependency and Android ships its own in the framework — check for a duplicate-class or shadowing problem. And it moves **Guava 27.0.1-android → 33.3.1-android**.
  - **APK cost is small and already-shaped.** Native libraries ship for **arm64-v8a and x86_64 only**, exactly the two ABIs T029's `abiFilters` already selects. `libjni_fst_constraint_android.so` is 2.67 MB plus `libjni_gemma_formatter_android.so` at 1.34 MB; exclude the Llama and Hammer formatters.
  - **⚠ Cannot be tested on an emulator**, for the same reason as everything else in this section (T039): no OpenCL, so engine init fails before any of this runs.
  - **Nothing here has been executed.** The whole chain is read off the AAR and the native library. Treat the first on-device run as the real verification.
- [x] **T037** — GO/NO-GO: LoRA viability spike on target device  
  `P0` `L` · after: T031
  - **DECISION GATE, TIMEBOXED.** Does a LoRA adapter load on the GPU backend with the chosen model? If no: fall back to few-shot and reframe the portfolio narrative. Do not start dataset work until answered.
  - **✅ ANSWERED 2026-09-23, from evidence rather than by running it. The gate is superseded, and the honest result is that the question no longer applies.**
  - **The chosen model cannot take a LoRA adapter, so the gate's question has no subject.** T039 chose **Gemma-3 270M** on measurement. MediaPipe documents LoRA for **Gemma-2 2B, Gemma 2B and Phi-2** only, and the runtime's own error string pins the reason: it expects `transformer.layer_(0-34).attn.(q|k|v|attn_vec_einsum).w_prime_(left|right)`, and **`attn_vec_einsum` is Gemma 1/2 architecture naming**. LoRA here is bound to the older architecture, not merely undocumented for the newer one.
  - **That is not a NO-GO, because [ADR-0011](adr/0011-constrained-decoding-and-fine-tuning-as-experiment.md) already removed what this gate protected.** The gate was written when the plan assumed a 2B model whose size made an adapter the only affordable way to fine-tune. At 270M a **full** fine-tune runs on free hardware, so there is no adapter to load and nothing for a GO/NO-GO to decide. The gate's own instruction — "if no: fall back to few-shot and reframe the portfolio narrative" — was carried out in advance by that ADR.
  - **Running it as written would have cost a training run to learn nothing.** The only LoRA-capable model this project can obtain is Gemma 1.1 2B, which T038 eliminated at 1,663–1,683 MB against §2's 1.5 GB budget. Proving an adapter loads on a model we will not ship answers no question we still have. **The timebox existed precisely to stop this**, so spending it here would have been the failure the gate was written to prevent.
  - **⚠ The risk it was covering has moved, and is now section 12's.** What is genuinely unproven is **converting a fine-tuned Gemma-3 checkpoint into something MediaPipe loads** — §4.4 names it as the unproven step, T030's convert half was never exercised because `litert-community` publishes pre-converted artifacts, and MediaPipe documents its Torch Generative converter as **CPU-only**, which would forfeit the GPU backend the launcher uses. **Answer that before T078 trains anything**, for exactly the reason this gate existed.
  - **⚠ Answer the memory question first — it is cheaper and it can eliminate a candidate outright** (T039, 2026-09-22). Gemma-2 2B publishes only q8 at 2.63 GB against §2's "< 1.5 GB resident", so a plain load plus `dumpsys meminfo` decides it with no adapter and no training. Spending the timebox proving LoRA on a model that cannot fit would answer the wrong question.
  - **⚠ This cannot be done on an emulator, demonstrated at T039.** The AVD has no OpenCL, so every load fails at engine init regardless of the file. The S20 FE is the only device that can answer this, which makes adb reachability a prerequisite rather than a convenience.
  - **Exercise both halves of LoRA.** `setSupportedLoraRanks` is declared on `LlmInferenceOptions` when the engine is built; `setLoraPath` is supplied per session. The runtime also requires the rank to be a multiple of 4 and the adapter metadata to carry `lora_rank` — both are `RET_CHECK` failures inside `libllm_inference_engine_jni.so`, so get them right before concluding anything about viability.
- [x] **T038** — Baseline latency + peak memory measurement  
  `P0` `M` · after: T031
  - **✅ Measured 2026-09-23 on the S20 FE**, both candidates, warm, through `MediaPipeEngine` and T032's prompt.

    | | **Gemma-3 270M q4** | **Gemma 1.1 2B int4** |
    |---|---|---|
    | File | 249 MB | 1.35 GB |
    | Load | 7.9 s | 10.3–11.0 s |
    | Infer, warm | 3568 / 3425 ms | 3936 / 4175 ms |
    | Generated | 554 chars (runs on) | **107 chars (stops cleanly)** |
    | PSS after load | **430 MB** | **1663–1683 MB** |
    | PSS after inference | 462 MB | 1666–1671 MB |
    | §2 budget, < 1.5 GB | ✅ 3x headroom | ❌ **over by ~11%** |
    | Answer | correct | correct |

  - **⚠ The 2B does not fit the device, not merely the budget.** `MemAvailable` at the time was **1,638 MB** and its PSS was **1,663–1,683 MB**. It loaded because the kernel reclaimed from elsewhere, which on the process that *is* the home screen means either the launcher gets killed or everything around it does. §2's number turns out to be generous rather than arbitrary.
  - **⚠ These are floors, not ceilings.** `summary.graphics` reported **0** on both runs, which is implausible for a GPU backend and means GPU-side allocation is probably not included in `totalPss`. Treat every number above as a lower bound; it makes the 2B's breach worse and never better.
  - **The 2B is ~5x slower per token, exactly as its parameter count predicts.** 107 chars in ~3.9 s against 554 chars in ~3.5 s. Its apparent parity on wall-clock is entirely because it wastes fewer tokens, not because it is fast.
  - **✨ And it stops.** The 2B emits one clean JSON object and ends; the 270M runs on with fabricated exchanges (T032). That is a real quality edge, and it is the one thing the smaller model is worse at.
- [x] **T039** — DECISION: Gemma-2 2B vs Gemma-3 1B — record rationale  
  `P0` `S` · after: T037
  - **✅ DECIDED 2026-09-23: Gemma-3 270M.** T038's measurements settle it, and the task's own title is now doubly stale — the winner was never one of the two models it names.
  - **Gemma 1.1 2B is eliminated on memory, which is structural.** 1,663–1,683 MB PSS against §2's 1.5 GB budget *and* against the 1,638 MB the device actually had free. There is no quantization below int4 published for it, T039 already established the Torch converter is CPU-only so we cannot make one without losing the GPU, and no prompt change moves a weights footprint. On the process that is the home screen this is not a tuning problem.
  - **The 270M's weakness is fixable and its rival's is not.** It runs on for ~450 wasted characters, which T033 already discards with correctness intact and which costs latency rather than accuracy. Fix the stopping and it projects to **~0.7 s**, inside §2's 1.5 s target; the 2B sits at 3.9 s *after* stopping cleanly, because it is five times the model.
  - **It also unlocks the better fine-tuning story** ([ADR-0011](adr/0011-constrained-decoding-and-fine-tuning-as-experiment.md)). At 270M a *full* fine-tune runs on free hardware with no adapter, so LoRA, the GPU-only conversion path and T037's whole gate stop being on the critical path. That was the point of demoting fine-tuning from dependency to experiment.
  - **Keep Gemma 1.1 2B as the LoRA experiment**, not as a shipping candidate. It is the only model here on MediaPipe's LoRA list, so if T037 ever runs it is the subject — and its result becomes a row in T083's table rather than something the launcher depends on.
  - **⚠ Do not read this as "bigger did not help".** The 2B answered correctly *and* stopped cleanly, which the 270M does not. It lost on a device constraint, and on a different phone it might well win. Record the constraint, not a general claim.
  - **What closes the remaining latency gap** is the stopping problem, which is T190's constrained decoder (parked on vocabulary support) or a cheaper bound on generation. Nothing about it depends on the model choice.
  - Gemma-3 1B LoRA support is not clearly documented; Gemma-2 2B is. Let measurement decide, then write it down.
  - **🔎 Narrowed 2026-08-22 while answering T095.** The MediaPipe docs are no longer ambiguous: *"The LLM Inference API only supports LoRA on attention layers"* for **Gemma-2 2B, Gemma 2B and Phi-2**. Gemma-3 1B is not on that list. So this is no longer "let measurement decide" between two LoRA-capable models — **if LoRA is required, Gemma-2 2B is the only Gemma option**, and Gemma-3 1B is a choice to *give up* fine-tuning in exchange for being roughly a quarter of the size (555–722 MB q4 against 2.71 GB q8).
  - That reframes T037's gate: it is no longer only "does LoRA work on this device", it is "does LoRA work on Gemma-2 2B on this device", with the fallback being either few-shot on the same model or a switch to the much smaller Gemma-3 1B.
  - **🔎 Reframed again 2026-09-22 (research plus a spike). This is now a four-way choice, and the task title is stale.** See §4.4 for the table. Everything below was checked against artifacts and the shipped runtime, not documentation.
  - **⚠ Gemma-2 2B breaches §2's own memory target, and nothing had noticed.** `litert-community` publishes **only q8** for it: 2.63–2.71 GB, no int4 exists. §2 says "Model resident memory < 1.5 GB during inference". The plan of record misses its own success criterion by ~1.8x on weights alone, before KV cache, in the process that *is* the home screen. This task had only ever treated size as a download cost.
  - **The escape hatch is closed, and that is the structural finding.** MediaPipe documents the Torch Generative converter as **CPU-only**, while LoRA inference is **GPU-only**. So "convert our own int4" and "use LoRA" are mutually exclusive. Any adapter-based path must use a base model Google already converted.
  - **✨ Gemma 2B (the original) was never on the list and is the only candidate meeting both constraints.** Kaggle's `google/gemma` LiteRT framework publishes nine variations including **`gemma-2b-it-gpu-int4`** and **`gemma-1.1-2b-it-gpu-int4`** at **1.35 GB** — under the memory target, and on the LoRA list. Prefer the **1.1** instruction tune: same size, better tuning. The cost is age (February 2024) and benchmarks below Gemma-2 2B.
  - **🧪 The format risk on that artifact is resolved, by experiment.** Those files are `.bin`, the pre-2024 flat format, and 0.10.35's loader is visibly `.task`-bundle-oriented (`"Expected uncompressed zip archive"`, bundled minizip), so reading strings suggested `.bin` might be dead. **It is not.** Three files down the same code path, differing only as controlled:

    | File | Size | Fails at |
    |---|---|---|
    | random bytes | 1 KB | `memory_mapped_file_posix.cc:108` — too small to mmap |
    | real file, first 4 KB zeroed | 1,354,301,440 | `model_data.cc:424` — **Error building tflite model** |
    | `gemma-2b-it-gpu-int4.bin` | 1,354,301,440 | `Can not find OpenCL library on this device` |

    The corrupted copy is byte-identical in size and differs only in its header, and it dies at **tflite parsing**. The real file gets past parsing and dies at a *device capability* check. **MediaPipe 0.10.35 parses the `.bin`.** The earlier read of the strings was wrong: the flat-flatbuffer path is live, not vestigial.
  - **⚠ What the spike did NOT establish.** Whether the file completes initialisation on hardware that *has* OpenCL. The Adreno 650 does, so it should, but that needs the phone and the phone was not reachable over adb. **That is the one gap left before this task can close**, and it is a two-minute check — the 1.35 GB artifact is already downloaded.
  - **⚠ Emulators cannot answer any model question, now demonstrated rather than quoted.** The AVD has no OpenCL, so *every* load attempt dies at engine init regardless of the file. T030, T031, T037 and T038 all require the S20 FE.
  - **`setPreferredBackend(CPU)` did not avoid OpenCL** for this artifact — it failed identically on both backends. The GPU-ness appears baked in at conversion, exactly as §4.4 warns, which matters for T030 choosing a conversion target.
  - **Below 1B, LoRA stops being the point.** A 270M model can be *fully* fine-tuned on free hardware and merged, which is a stronger artifact than an adapter and sidesteps the GPU-only constraint entirely. 0.10.35 carries `LLM_MODEL_TYPE_GEMMA3_300M` **and `LLM_MODEL_TYPE_FUNCTIONGEMMA3_300M`** as first-class model types, and Google publishes `FunctionGemma-270M-Mobile-Actions` — a Gemma fine-tuned for mobile action extraction, which is literally this project's task. Unproven step: converting a fine-tuned Gemma-3 checkpoint into something MediaPipe loads.
  - **⚠ Every official artifact is gated.** All `.task` files under `litert-community` return **HTTP 401** without a Hugging Face token — Gemma-2 2B, Gemma-3 1B and the 270M alike — and Kaggle's Gemma requires requesting access. Only unofficial `.bin` re-uploads are openly fetchable, and those have no redistribution standing under T094. The spike used one; T087 must not.
  - **⚠ MediaPipe's LLM stack is already LiteRT-LM underneath.** Every error path names `third_party/odml/litert_lm/runtime/...`. [ADR-0003](adr/0003-mediapipe-over-llama-cpp.md) and T091/T092 treat the v2 migration as swapping runtimes; it now looks like an API change over the same engine. Worth revisiting before budgeting it.
  - **Deliberately left open.** The remaining inputs are one on-device load and T037's actual LoRA gate. **Current lean: `gemma-1.1-2b-it-gpu-int4`**, because it is the only candidate that keeps fine-tuning *and* §2's memory target. If it fails on hardware, go to the 270M full-fine-tune line rather than back to Gemma-2 2B — the memory collision there does not go away.
  - **✨ Reopened 2026-09-22 by [ADR-0011](adr/0011-constrained-decoding-and-fine-tuning-as-experiment.md): LoRA is no longer a requirement.** Fine-tuning is demoted from a dependency of T2 to a measured experiment, so a candidate no longer has to support adapters. That removes the constraint this whole task was contorted around. **The 270M line is now the front-runner**: 249 MB at q4, *fully* fine-tunable on free hardware with no adapter and no GPU-only conversion path, and `FunctionGemma-270M-Mobile-Actions` is evidence the size class handles this exact task. Gemma 1.1 2B `gpu-int4` stays the fallback if a 270M proves too weak on the held-out split.
  - Which means **T037 can no longer sink this section.** A negative LoRA result now costs a row in T083's table rather than the fine-tune itself.
  - The spike lives at `app/src/androidTest/.../slm/ModelFormatSpikeTest.kt`. It **skips unless given a `modelPath` argument**, so it is inert in CI. Delete it once this task closes.


## 07 · Backend (Functions + Cosmos)

**T042 is the only hard blocker for section 08 — build it first within this section.**

- [x] **T042** — Notes.Shared: POCOs, DTOs, tag parsing  
  `P0` `L`
  - Referenced by both Desktop and Functions — single source of truth, cannot drift. **Unblocks section 08.**
  - ⚠️ **Replace the project, don't amend it.** `backend/Notes.Shared/Notes.Shared.csproj` is still T098's scaffold: non-SDK, `TargetFrameworkVersion` **v4.7.2**, `OutputType` **Exe** — it fails to build on its own (`CS5001: no static Main`). Target **`net10.0`**, not `net10.0-windows`: `Notes.Desktop` can reference the former, never the reverse (`NU1201`). Re-add the `ProjectReference` T052 removed.
  - **The fixtures are the spec and they define more than three parsers.** 95 cases across ~9 functions: `tag-parsing.json` (26 cases); `checklist-parsing.json` (20 parse + `renderCases`, `conversionCases`, `normalizeCases`, `cleanCases` — so parse, render, `toNoteBody`, `normalized()`, `cleaned()`); `due-date-parsing.json` (`storedCases`, `renderCases`, and 21 `inputCases` for `@tomorrow`/`@t+3` resolution). Each file's `rules` and `knownLimits` are part of the contract, not commentary. **Don't transcribe the regexes from §6.2.1** — that section says to copy them from the fixtures or Kotlin source, because the prose is orientation and the fixture wins.
  - `inputCases` carries a `today` field, so shortcut resolution must **take "today" as a parameter**, never call `DateTime.Now` — otherwise the tests aren't reproducible.
  - **Fixture harness:** Android puts repo-root `fixtures/` on the unit-test classpath via a `sourceSets` entry in `android/app/build.gradle.kts`. The C# side needs the equivalent, with xUnit `[Theory]` so each case reports as a named test.
  - **↻ Scope grew with [ADR-0008](adr/0008-server-side-task-index.md) (2026-08-25).** The shared contract now also needs the **task type and the derivation function**, because the server derives its own task index using these same parsers. Deciding this after `Notes.Shared` shipped would have meant changing a contract two clients already implemented — which is exactly why it was decided before this task rather than after.
  - **↻ Merge rules moved out to [T070] (2026-09-14).** They were in this task's title but are a different kind of work: §7.3 says the three body-syntax fixtures "already exist and are already pinning Android's implementation", so the parsers here were *matching a contract already live in production*, while the sync corpus is "not yet built on either side, so it is genuinely new design, not a mirror". Writing the rules here would mean writing them, then their corpus in T070, then finding the disagreements in T071 — the reverse of the order that made the parsers work. See T070.
  - **✅ Parsers, types and derivation are done — 180 tests, all 95 fixture cases green.** `Notes.Shared` (`net10.0`) holds `TagParser`, `ChecklistParser`, `InputShortcutParser`, `TaskIndex` and `Model/`; `Notes.Shared.Tests` (`net10.0`, references neither WPF nor EF Core, which is *why* it is separate from `Notes.Desktop.Tests`) executes the corpus. **`Notes.Desktop` references it again**, restoring what T052 removed, and T057 consumes `TagParser` and `TaskIndex` from the EF Core save path — so the contract is live on this side, not merely compiled.
  - **Wire-format decisions, none of which are fixture-pinned** — the three corpora cover body *syntax* only, so the document envelope rests on §6.2 plus `Notes.Shared.Tests`:
    - **`NoteType` serializes as a lowercase string** via `[JsonConverter(typeof(JsonStringEnumConverter<NoteType>))]` plus `[JsonStringEnumMemberName]` per member. System.Text.Json writes enums as **integers** by default, so left alone C# would emit `"type": 0` and Kotlin would never parse it. The attribute sits on the *type* so it travels to every serializer (desktop `HttpClient`, Functions runtime, Cosmos SDK) rather than depending on each configuring options.
    - **camelCase on the wire achieved by naming the C# properties in camelCase** (`id`, `userId`, `createdAt`, and `text`/`@checked` on `ChecklistItem`) rather than by `[JsonPropertyName]` or a naming policy. Self-consistent and attribute-free; the cost is that the public contract breaks C# convention and every consumer writes `note.createdAt` / `item.@checked`.
    - **Timestamps: see §6.2** — settled as ISO 8601 with an explicit numeric offset and whole seconds. A `JsonConverter<DateTimeOffset>` was designed and rejected as disproportionate, since §7.1 already makes clocks non-load-bearing.
  - **🐛 Four bugs worth remembering, all shipped into a fully green suite and caught only when a test finally exercised the new path:**
    1. *Tag boundary written as a blocklist* (`!IsLetterOrDigit && !IsSymbol`) where the rule is an allowlist — `#` is category `Po` and `(` is `Ps`, so `##work` and `(#work)` both slipped through. `char.IsWhiteSpace` is the whole test.
    2. *Due-date extraction:* `FirstOrDefault()` on `IReadOnlyCollection<DateOnly>` returns `DateOnly.MinValue`, not null, so every **undated** item got `due = 0001-01-01` — 38 failures. Also first-wins where the contract is last-wins, and `match.Value` parsed instead of `.Groups[1].Value` (the pattern *consumes* its leading whitespace).
    3. *Shortcut resolution held one fact in two places* — a `HashSet` of reserved words **and** a `switch` mapping them. The loop matched against one and returned from the other, so every non-ISO input returned whatever `HashSet` iteration yielded first: **nondeterministic across runtimes**. One `switch (input)` removed the second list and the bug together.
    4. *`TaskRow.due` used bare `DateOnly.ToString()`* — the current culture's short-date pattern (`8/25/2026`), not ISO. That column is what both clients query "what's due today" against, and a non-ISO string doesn't sort or range-compare, which is the entire justification for storing it as text in ADR-0007.
  - **Pattern behind all four: production code written before its test, into a suite that stayed green because nothing exercised the new path.** Write the fixture theory first.
  - ⚠️ **`Position` in `TaskIndex.TasksOf` is the index in the *full* checklist, not among the dated subset** — Kotlin uses `mapIndexedNotNull`. `Where(...).Select((item, i) => …)` reads naturally, renumbers from zero, and passes every other test; it would break a UI tap's ability to find the right line to tick.
  - **`TasksOf` lives in its own `TaskIndex.cs`**, not on `ChecklistParser`: the parser is body-level (`string` → `ChecklistItem`) and has no knowledge of the `Note` DTO, which is the right direction for that dependency. Mirrors Kotlin's `TaskIndex.kt`. **No fixture covers `tasksOf` at all** — its 10 tests are hand-written.
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
- [ ] **T181** — Server-side derived task index  
  `P1` `M` · after: T042, T046
  - [ADR-0008](adr/0008-server-side-task-index.md). On every accepted write, derive the note's tasks from `body` with the `Notes.Shared` parsers and store them as a derived array on the document — so the backend can answer "what is due today" without scanning every body.
  - ⚠️ **Never accept a task array from a client.** The server derives or it does nothing; a client-supplied array is two writers of one fact, and 0008 rejected it explicitly. No outbox row, no conflict case — tasks are a projection, not a synced entity, so sync gains nothing new from this.
  - **Set a targeted Cosmos indexing policy over the due path** rather than inheriting index-everything. Indexing cost lands on writes, throughput is manually pinned at 1000 RU/s (T050) with no autoscale headroom, and the budget is $0.
  - **Open, deliberately left to the first consumer:** whose "today"? The stored token is date-only so timezones stay out of the wire format (§6.2.1), which works only because "today" is evaluated locally. A digest or notification must source the user's timezone from somewhere. **Adding a time to the token is the wrong answer** — it breaks the reason the format is date-only and invalidates `fixtures/due-date-parsing.json` plus both existing implementations.
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

- [x] **T052** — Notes.Desktop project: .NET 10 WPF + Generic Host + DI  
  `P0` `M` · after: T042
- [x] **T053** — EF Core + SQLite context and migrations  
  `P0` `M` · after: T052
  - Offline source of truth; the most-quoted .NET data skill.
- [x] **T054** — MVVM scaffolding (CommunityToolkit.Mvvm)  
  `P0` `M` · after: T052
  - Source-generated observables; no hand-rolled INotifyPropertyChanged.
- [x] **T055** — Two-pane shell: note list + editor  
  `P0` `L` · after: T054
  - **ViewModel-first composition, no navigation framework.** `MainWindowViewModel` grew into the shell VM (holding `List`/`Editor` child VMs) rather than a separate `ShellViewModel`; implicit `DataTemplate`s keyed by `DataType` map each child VM to its `UserControl`, so the shell never names a View. Views take **parameterless** constructors — WPF's BAML loader instantiates template content, not the DI container, and the template sets each `DataContext` automatically. A View constructor taking its VM cannot work here and was the first thing to get wrong.
  - **Splitter position persists across launches** (`ListColumnWidth`), via a `TwoWay` binding on the list `ColumnDefinition.Width` and `IRegistryAccess`, written on `OnExit`. This is the project's **first settings store** — see §5.1. T059's theme override and anything else needing durable UI state should use it rather than inventing a second one.
  - ⚠️ **Fully collapsing the list pane is not what the splitter does, and `MinWidth` is why.** The guard that stops a drag reaching zero also clamps a programmatic width of 0, so a collapse toggle has to drive `MinWidth` as well as `Width`. Deferred deliberately — not attempted and not half-built.
  - **🐛 Test registry cleanup silently did nothing**, leaving values in `HKCU` that accumulated every run. `SetRegistryString` writes a *value*, so `DeleteSubKeyTree` was the wrong API and its `throwOnMissingSubKey: false` swallowed the miss. Fixed by putting `DeleteRegistryKey` on `IRegistryAccess` (`DeleteValue`), which the test needed anyway — better than widening `BASIC_REG_PATH`'s visibility to let the test build the path itself.
- [x] **T056** — Note list VM: sort by updatedAt, category multi-select filter  
  `P0` `M` · after: T055
  - Filter computed locally from SQLite — instant, no round trip.
- [ ] **T180** — Note list search field  
  `P1` `M` · after: T056
  - Sits at the top of the note list sidebar. **Mirrors Android's T026 grammar** so one query means the same thing on both clients: a lone `#tag` is an exact lookup against the indexed `NoteTags` table; anything else is free text over `Title` and `Body`.
  - **Composes with T056's category multi-select, doesn't replace it** — the filter scopes, search narrows within that scope. Two affordances, one result set.
  - `LIKE`, not FTS — same reasoning as T026: hundreds of rows, and FTS costs a migration.
  - ⚠️ **Wildcard escaping is load-bearing, and T026 already paid for this once.** `%` and `_` are `LIKE` wildcards, so unescaped, searching "50%" returns every note and "snake_case" also matches "snakeXcase". Whether it bites here depends on how the EF Core query is written — `EF.Functions.Like` passes the pattern straight through, while `string.Contains` translates differently — so decide deliberately and cover it with a test against real SQLite rather than assuming the LINQ provider handles it.
  - **Open decision:** whether the query grammar belongs in `Notes.Shared` (so the C# and Kotlin implementations can't drift) or stays per-client. §7.3 doesn't currently list it, and Android's `parseNoteQuery` defers to the fixture-pinned tag parser but is not itself pinned — so this is a genuine call to make, not a mirror of an existing decision.
- [x] **T057** — Markdown editor + Markdig live preview  
  `P0` `L` · after: T055
  - **`Markdig.Wpf` renders to a `FlowDocument`, not a browser** — [ADR-0009](adr/0009-markdig-wpf-over-webview2.md). Markdig parses to HTML and WPF has no HTML renderer, so the library settled parsing and left display open. WebView2 was rejected on cost: it complicates T063's MSIX packaging with a runtime dependency and makes T059 maintain CSS in parallel with WPF styles. The preview is now ordinary WPF content that themes and packages like everything else.
  - **Derivation lives in `AppDbContext.SaveChangesAsync`, not the ViewModel.** Overriding the two-argument `SaveChangesAsync(bool, CancellationToken)` is what makes this reliable: `DbContext.SaveChangesAsync(ct)` delegates to it virtually, so **that** is the real chokepoint and an override of the one-argument form alone can be bypassed. Every `Note` write rebuilds its `NoteTags` and `Tasks` rows and enqueues one `Outbox` row, all inside the transaction `base.SaveChangesAsync` opens. `SaveChanges()` throws — derivation queries for the rows it replaces, so there is no correct synchronous path, and throwing beats silently writing a note with no tags, no tasks and no outbox row.
  - **Autosave is a 3s debounce with two guards**: a blank body never persists (so opening the editor and closing it leaves nothing behind) and a non-dirty save is a no-op (so the timer does not churn `UpdatedAt`, which orders the note list and feeds the sync comparison). A blank title is stamped with `createdAt` under `"ddd d MMM ' - ' HH':'mm"` and `InvariantCulture` — deliberately not the machine culture, or the same note lists as `Mo 14 Sep` on one desktop and `Mon 14 Sep` on another.
  - **A separate `NoteDraft` type was tried and removed.** Android's `NoteDraft` is an immutable record with free functions — it *is* the state. That does not port: `[ObservableProperty]` must own the bindable state in WPF, so a draft object beside it is a second copy of every field plus the wiring to keep them in step. The Kotlin shape was ported too literally; the pure half survives as `GetFormattedTitle()`/`GetFormattedBodyString()`.
  - **🐛 Three bugs, each invisible at runtime for a different reason:**
    1. *`[ObservableProperty] partial string Title { get; set; }` with no access modifier is **private**.* `MainWindowViewModel` writes `public partial`; this one did not. WPF's binding engine never reflects over non-public members, so all three bindings in `NoteEditorView.xaml` — title, body, and the preview's `Markdown` — resolved to nothing and logged a `BindingExpression path error` rather than throwing. The editor could not have worked. Found because the tests would not compile, which is the second time a silent binding failure has cost this project real time.
    2. *The debounce read `_debounce` after an `await`.* Keystroke A's continuation disposed keystroke B's `CancellationTokenSource`, so C threw `ObjectDisposedException` inside a fire-and-forget task and autosave died silently. Fixed by passing the token as a parameter instead of re-reading the field.
    3. *The update branch bypassed the insert branch's normalisation.* `DraftToNote()` substituted the blank-title stamp and stripped CR; the update branch assigned `Title` and `Body` raw. Three symptoms, one seam: an untitled note wrote `null` over its own stamp (`SQLite Error 19: NOT NULL constraint failed: Notes.Title`), a cleared title saved as `"   "`, and CRLF persisted — `NoteToDraft` converts LF→CRLF on load, so every edit after the first re-introduced it. **All three were silent in the running app**, because the update path is what every autosave after the first three seconds takes and `AutosaveAfterDelayAsync` catches and logs the exception. The visible symptom was a note that stops persisting three seconds after it is created. Fixed by making the two branches read the same pure formatters.
  - ⚠️ **The editor cannot author a checklist yet.** `noteType` is hard-coded to `NoteType.Note` and `TaskIndex.TasksOf` short-circuits on that type, so a dated `- [ ]` line typed into the editor derives no task row today. T058 is what gives the editor a way to set the type; a test pins the current behaviour so the change is deliberate rather than accidental.
  - **↻ Two more bugs found 2026-09-21, while breaking down T056** — both latent in the code above and unreachable until something could select a second note. Recorded here rather than under T056 because T057 is where they were written, and because the pattern is the point: *a bug with no caller is still a bug, and the task that adds the caller is the one that pays for it.*
    1. *Loading a note marked it dirty.* `NoteToDraft` assigns `Title` and `Body`, so the generated setters fired the change handlers, which set `IsDirty` and armed the autosave. Opening a note to **read** it would have rewritten it three seconds later, bumping `UpdatedAt` — the column the note list sorts on. Reading a note would have reordered the list, which would have looked like a T056 list bug and been nothing of the kind.
    2. *Switching notes dropped up to three seconds of typing.* `LoadAsync` overwrote `Title` and `Body` unconditionally while an autosave was still pending. Fixed by **flushing before loading** — `LoadAsync` awaits `SaveAsync` first — and the fix belongs in the editor rather than in the shell that will call it, so "loading never loses work" holds for every future caller rather than for the careful ones.
  - **The fix changed what `IsDirty` *is*, and that was the point.** It was a flag set by the change handlers; it is now derived — a comparison against the last-persisted title and body, snapshotted in both `LoadAsync` and `SaveAsync`. A flag records that something *might* have changed; a comparison knows whether it *did*. Typing a character and deleting it again now leaves nothing to write, where before it saved byte-identical content. Three things this needs to stay correct: the snapshot must refresh after `SaveChangesAsync` and not before, it must hold the ViewModel's CRLF strings rather than the LF-normalised persisted ones (or every loaded note reads as permanently dirty), and `[NotifyPropertyChangedFor(nameof(IsDirty))]` has to sit on `Title` and `Body` because a computed property announces nothing on its own.
  - **🐛 A third disposed-`CancellationTokenSource` bug in this one file.** `CancelPendingAutosave()` cancelled and disposed but left `_debounce` pointing at the dead source, so the next `Debounce()` cancelled a disposed source and threw `ObjectDisposedException` — from inside a property setter, mid-load, on exactly the type-then-switch path the flush was added to protect. The durable fix was not the null assignment: it was making `Debounce()` call `CancelPendingAutosave()` instead of repeating the cancel/dispose pair, so one method owns that field. **Three bugs from one field with two writers is the lesson**, not the individual null check.
  - **59 tests in `Notes.Desktop.Tests`.** Derive-on-save is covered against real SQLite (not EF's `InMemory` provider, which enforces no relational constraints at all), with every assertion read back through a *second* context over the same connection — asserting against the writing context proves only what the change tracker holds. Outbox coalescing is pinned in both directions: three edits leave one row, and `BaseRev` stays at the first-dirty revision even after `Rev` moves on, because re-stamping it would tell the merge that a revision which was never the common ancestor was one.
  - **Autosave *timing* is deliberately untested.** `AutosaveDelay` is a `const` with no seam, so asserting the 3s delay (and that a save does not land early) costs seconds of wall clock per run. Noted in the test file rather than worked around — an injected delay would make both testable and is a design change worth considering on its own merits, not as a testing workaround.
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
- [ ] **T070** — Shared JSON fixture corpus (base/local/remote/expected) **+ the merge rules themselves**  
  `P0` `M` · after: T042
  - The seam where Kotlin/C# drift will live. Commit once, execute from both test suites.
  - **↻ Scope grew 2026-09-14: the merge rules moved here from T042.** Unlike the three body-syntax parsers — which T042 implemented against fixtures *already pinning Android's production code* — this corpus does not exist on either side yet (§7.3: "genuinely new design, not a mirror"). So the corpus and the rules are one piece of work with no oracle until it is written, and splitting them would mean writing rules, then their spec, then discovering the disagreements in T071.
  - **The rules are small; the scenarios are the work.** §7.2 is one paragraph — compare the pushed `baseRev` against the server's current `rev`, accept the newer write, return a conflict marker, client materialises the loser as a separate note titled `<title> (conflict, <device>, <timestamp>)`. What needs designing is the scenario set: offline edit on one client, concurrent conflicting edits, delete-vs-edit, interrupted push, and clock skew (which must *not* affect ordering — `rev` is server-assigned and never client-invented).
  - Lives in `Notes.Shared` alongside the parsers, per §7.3, and must stay testable with no Functions-runtime or Cosmos SDK dependency.
  - ⚠️ **Write the corpus theory before the rules.** Every bug in T042 — four of them — was production code written ahead of its test, landing in a suite that stayed green because nothing exercised the new path.
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

- [x] **T158** — GitHub Actions: unit, lint and instrumented tests  
  `P0` `M`
  - **Added 2026-08-21 (user request).** Fast feedback on a pull request, needing no Azure org, billing link or service connection to exist first.
  - **Coexists with Azure Pipelines rather than replacing it** (check-in). This section is 50–55% of AZ-400 and the reason its tasks exist; Actions takes build-and-test, Azure keeps the exam-shaped work — environments, approval gates, slot swaps, IaC what-if. T118 and T123 narrow rather than disappear.
  - **Instrumented tests run on `ubuntu-latest` with KVM enabled** by hand: `/dev/kvm` is not world-writable by default, and without the udev rule the emulator silently falls back to software rendering and the job hangs rather than failing. API 33 matches `minSdk`/`targetSdk` and the S20 FE; `aosp_atd` because nothing in the suite needs Play Services and it boots far faster.
  - **Worth the minutes** (check-in): the instrumented suite is the only thing that has ever caught a bug in the database layer, and it has done so three times — T018's outbox cascade, T151's runtime serialization conflict, and T157's index.
  - **🔎 Path filtering is an allow-list, not `paths-ignore`** (review, 2026-08-21). The first version excluded `website/` and `docs/` and forgot `desktop/`, `backend/` and `infra/`, so .NET and Bicep changes would have run the whole Android suite for nothing — and every top-level directory added later would silently do the same until someone remembered to exclude it. An allow-list fails safe.
  - **`fixtures/` is on that list, and it is the non-obvious entry.** The cross-language corpora sit on the Android unit-test classpath (`resources.srcDir("$rootDir/../fixtures")`), so editing a fixture *is* editing what the Android tests assert. Omitting it would let someone change a contract both clients must satisfy without running the tests that enforce it. Verified by simulating the match against a spread of real paths.
  - **🐛 The shared path list cannot be a YAML anchor.** Written first as `&android-paths` / `*android-paths`, which every YAML linter accepts and GitHub Actions does not — its workflow parser is custom and has never supported anchors, so it would have failed at runtime. The list is duplicated instead.
  - The three jobs are independent, not chained. Serialising them would add minutes to every run to save quota only on runs that were already failing.
  - **🐛 First run failed: `./gradlew: Permission denied` (exit 126) on every job.** `android/gradlew` was committed as mode 100644, because this machine has `core.filemode=false` and git never recorded the executable bit; Linux runners then check out a script they cannot execute. Fixed with `git update-index --chmod=+x`, which is a property of the file rather than a workaround in the workflow. Noted in `android/CLAUDE.md` because a wrapper upgrade on Windows will silently undo it.
  - **🔎 Also from the first run: the release-variant build was running on pull requests.** Kept, but moved off PRs. A check called "Assemble release" sitting on a PR reads like the PR is cutting a release, and it doubled CI time per PR for very little — `isMinifyEnabled` is false, so the release variant differs from debug only by a signing config deliberately absent in that job. Pushes to `main` still catch a release-only break, which is early enough given `main` is protected and the release workflow rebuilds at tag time.
  - **🐛 Second run failed differently: `/usr/bin/env: 'sh
': No such file or directory` (exit 127).** CRLF in `gradlew`. The committed blob was fine — the fault was `.gitattributes`, where the exemption was written `/gradlew`, anchored to the repo root, while the wrapper lives at `android/gradlew`. It matched nothing, so the blanket `* text=auto eol=crlf` applied, and `eol=crlf` forces CRLF on *every* platform rather than only Windows. Patterns unanchored so they match at any depth; verified with `git check-attr`.
  - **🔎 The same audit caught `scripts/export-command-log.sh` at mode 100644** — documented at T014 as a command to run, and not executable for anyone on Linux or macOS. Fixed alongside.
  - **This was the point of "never executed."** Three separate problems, all invisible to YAML validation, to the Gradle task check, and to every local run — and each one only surfaced after the previous was fixed, since they all failed at the same first step.
- [x] **T159** — Signed release APK on a tag  
  `P0` `M` · after: T158
  - **Added 2026-08-21 (user request).** `git tag android-v0.1.0 && git push origin android-v0.1.0` cuts a GitHub Release with an installable APK attached. Setup written up in [`releasing.md`](releasing.md).
  - **🔎 Tags are `android-v*`, not `v*`** (review). This is a monorepo with three shippable things in it, and a bare `v*` would mean the desktop client could never be versioned independently — tagging `v0.2.0` for a desktop release would build and publish an Android APK. `desktop-v*` and `backend-v*` are the siblings when those exist, and their numbers never have to agree with the launcher's. Workflow renamed `android-release.yml` to match.
  - **🔎 The reason this needs a real keystore, found while scoping it: there was no signing config at all.** Release builds were unsigned and would not install; debug builds use `~/.android/debug.keystore`, which is generated per machine and freshly on every CI runner. Every build would have carried a different signature, so updating the launcher would have required uninstalling it — taking its database and every unsynced note with it. Sync does not exist yet (§7), so that is real data loss on the device being daily-driven.
  - **Signing is configured only when the environment supplies a keystore**, so a local `assembleRelease` and any fork build still succeed and simply produce an unsigned APK. Absent secrets must not become a build failure for someone who only wants to compile the project. **Both paths were verified locally** — unsigned without the env vars, and `apksigner`-confirmed signed with a throwaway key, which was then deleted.
  - **The release job verifies the APK is signed before publishing.** Without that, a misconfigured secret yields `app-release-unsigned.apk`, the upload glob matches the wrong file or nothing, and the first symptom is "app not installed" on the phone.
  - **Tests run again at tag time** rather than trusting the branch was green, since a tag can be placed on any commit, including one that never went through a pull request.
  - **🔎 `.gitignore` had no keystore rules** — added `*.jks`, `*.keystore`, `*.jks.base64` and verified with `git check-ignore`. The releasing doc had claimed they were already ignored; they were not.
  - ⚠️ **Needs one manual step:** create the keystore and add four repository secrets. Passwords are deliberately not something an assistant handles.
  - **🐛 First release run: the signature *verification* failed, not the signing.** The APK existed and was correctly signed; `"$ANDROID_HOME"/build-tools/*/apksigner` matched several build-tools versions installed on the runner, so the first path ran as the binary and the rest were passed to it as arguments. apksigner reports that as `Unsupported command: <path>`, which reads like the APK is bad when the invocation is. Now selects the newest explicitly with `sort -V | tail -1` and echoes which one it used.
  - **The guard still did its job**, in a way: it stopped a release publishing on an unverified APK. It just blamed the wrong thing, which is worth remembering when reading its output.

- [x] **T160** — Rename the package off `com.example`  
  `P1` `S`
  - **`com.example.dailydriver` → `com.flambeauriver.dailydriver`.** `android/CLAUDE.md` had flagged this since T001 as something to "rename deliberately in one commit if/when a real namespace is chosen"; this is that commit.
  - **Prompted by an install failure that turned out not to be about the build.** The first signed release (`android-v0.1.0`) would not install on the S20 FE, showing only "App wasn't installed". Ruled out in turn: Android version (13, matches `minSdk` 33), an existing conflicting install (none), unknown-sources permission, Play Protect, storage, and download integrity.
  - **The APK was then proved innocent by installing it on a clean API 33 emulator — `Success` first try.** That narrowed the cause to the device rather than the artifact, which is what made a rebuild pointless and a rename worth trying: Play Services' package verifier is a separate mechanism from the Play Protect scan toggle, and is known to treat `com.example.*` as suspicious.
  - **Worth doing regardless of whether it fixes the install.** This is a portfolio app; shipping under the Android Studio default package is a detail an interviewer notices, and nothing was installed anywhere yet, so there was no migration cost to pay later.
  - **The Room schema directory moved with it** (`app/schemas/<fully-qualified-db-class>/`), which is the non-obvious part: that path is derived from the database class's FQCN, so a package rename relocates all three exported schemas. Missed, it would have broken `SchemaExportTest` and every migration test.
  - Source trees moved with `git mv` so history follows the files. 593 unit and **57 instrumented** tests pass, including schema export and all three migrations; a signed release APK under the new package installs cleanly on API 33.
  - **❌ It did not fix the install, because the package name was never the cause.** Once wireless debugging finally paired, `adb install` put the *same published APK* on the S20 FE first try. The manual sideload path was the problem: a user-initiated install runs through Google Play's package verifier, which is a **separate mechanism from the "Scan apps with Play Protect" toggle** that had already been turned off. Every property of the artifact checked out because the artifact was never wrong.
  - **Kept anyway, on its own merits.** A portfolio app shipping as `com.example` is a detail an interviewer notices, and there was no migration cost while nothing was installed. But it was proposed as a probable fix and was not one — the thing that actually resolved this was getting a real error channel instead of reasoning from "App wasn't installed", which is Android collapsing a dozen distinct failures into one string.
  - **Lesson worth keeping:** four device-side causes were eliminated by reasoning and the fifth was found in one command. The elimination was not wasted — it is what proved the APK innocent — but the order should have been reversed.

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
- ~~**T120** — Consume Notes.Shared from the feed in Desktop + Functions~~ **CUT 2026-09-24.**
  - Its own description argued against it: *"Optional: project references are simpler for a solo monorepo. Do this only for the exam practice, and note in an ADR that you would keep project references for a project this size."* A task that tells you not to do it is not a task. T119 still publishes the package, which is the AZ-400 objective; consuming it from the feed adds ceremony and nothing else.
  - Left struck through rather than deleted so the ID is never silently reused and the reasoning survives.
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

- [ ] **T188** — Instrumented-test job fails about half the time on an emulator that never boots  
  `P1` `M` · after: T158 · blocks: T123
  - **Found 2026-09-22 while checking T029's CI run.** Three of the last five occasions failed, each with `##[error]Timeout waiting for emulator to boot.` at the 600-second limit. `adb -s emulator-5554` reports `device 'emulator-5554' not found` for the entire window, so the APK is never installed and **not one test runs** — this is not a failing test, it is a job that never started.
  - **Not caused by any particular change.** The identical error hit `588269a` on 2026-09-21, which was a **docs-only** commit that cannot break an emulator boot. T029 then hit it on both its branch and its merge commit, which is what prompted the look.
  - **Not an emulator-version regression either.** The same build, `37.1.11.0`, booted successfully at 08:20 on 2026-09-22 (`795fbab`) and failed at 12:53 the same day. Verified by reading the version banner out of both logs rather than assuming.
  - **KVM is not the missing piece** — T158 already installs the udev rule, and it is present in the failing runs. The emulator prints its version banner and then emits nothing at all for ten minutes, so it dies or hangs immediately after start rather than failing to find acceleration.
  - **Two suspects, neither confirmed.** `aosp_atd` is a stripped-down image chosen at T158 for boot speed and is the less-travelled path; and `reactivecircus/android-emulator-runner` runs `sdkmanager --install emulator --channel=0` on every job, so the emulator binary is **whatever is newest that day** — an unpinned dependency inside a pinned-looking workflow.
  - **Why this is worth fixing rather than re-running.** T158's own record says the instrumented suite is the only thing that has ever caught a bug in the database layer, and that it has done so three times (T018's outbox cascade, T151's serialization conflict, T157's index). A check that fails at random gets ignored, and the next bug of that class rides through a red X nobody reads any more.
  - **It also blocks T123.** That task wires required status checks to branch protection, and `main` cannot be gated on a check that fails half the time for reasons unrelated to the code.
  - Try in this order, cheapest first: **pin the emulator version** so the variable stops moving; swap `aosp_atd` for `default` or `google_apis` and measure what it costs in boot time; add the action's AVD snapshot cache so boot is restored rather than cold. **Add a retry last, if at all** — a retry around a job that fails half the time makes the symptom go away while destroying the evidence, and T158 exists because CI problems here were only ever solved by reading the actual failure.
- [ ] **T189** — The website builds on every merge and nothing serves it  
  `P1` `S` · after: T158
  - **Found 2026-09-22 while checking T029's CI run.** The Docs workflow succeeds on every branch and fails on every push to `main`, going back to at least 2026-09-14. The build is fine; `actions/deploy-pages@v4` returns `Failed to create deployment (status: 404) ... Ensure GitHub Pages has been enabled`. `gh api repos/flambeauRiverTours/dailyDriver/pages` also 404s: **Pages has never been enabled on this repository.**
  - So `https://flambeauRiverTours.github.io/dailyDriver/` (the `site_url` in `mkdocs.yml`) serves nothing, and every accuracy pass on `website/` has been landing in an artifact that is built and then discarded.
  - **Cannot simply be turned on** (user's call, 2026-09-22): Pages on a private repository requires a paid plan.
  - **⚠ And "just make the repo public" is worse than it sounds.** The first T014 command-log export was committed in `2553b0c` and is **still reachable in history** — `git cat-file -s` confirms the 34 KB blob. Removing it from the working tree and gitignoring it (T014's follow-up) did not remove it from the repository. Making this repo public would publish a month of everything typed at the home screen, including full `:google` queries, and T075's redaction pass has not happened. Any route that goes through making the repo public needs history rewritten first.
  - **`deploy` is disabled with `if: false` rather than the workflow deleted**, so `mkdocs build --strict` still runs on pull requests and on main. A broken internal link or a page missing from the nav still fails a check. Only publishing is off.
  - **This does not relax the root `CLAUDE.md` rule.** "Keep the site current" exists because `website/` is a portfolio surface, and the surface has to be accurate by the time anyone is pointed at it — a site that is wrong on the day it first goes live is exactly what that rule is written against.
  - Resolve by choosing one: a paid plan that allows private-repo Pages; a separate public repository holding only the built site; or another host for the artifact. Whichever it is, **re-enable the `deploy` job in the same change** so the fix is not "it works now" with the publish still switched off.


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
  - **⚠ BLOCKED ON T087 (2026-09-24).** Latency, tier-hit distribution and abstention rate are all constant while `NoInferenceEngine` ships, so there is nothing to measure until a model reaches the device.
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

**↻ Reframed 2026-09-22 by [ADR-0011](adr/0011-constrained-decoding-and-fine-tuning-as-experiment.md).** This section is no longer what makes T2 work — T190's constrained decoding is. Fine-tuning is now a **measured experiment**: benchmarked against a constrained few-shot baseline on the frozen held-out split, with whichever wins on the numbers being what ships. That is a deliberate demotion and it makes the section *safer*, not less important. A disappointing fine-tune used to mean a launcher that emitted malformed JSON; now it means a row in T083's comparison table, which is a result worth publishing either way.

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
  - **⚠ Assumes an adapter the chosen model cannot use** (T037, 2026-09-23). T039 chose Gemma-3 270M and MediaPipe's LoRA support is bound to Gemma 1/2 architecture naming. At 270M this becomes a **full fine-tune**, which is cheaper on free hardware and a stronger portfolio artifact than an adapter ([ADR-0011](adr/0011-constrained-decoding-and-fine-tuning-as-experiment.md)). Retitle when it is picked up; `q/k/v/o_proj` targeting stops applying.
  - Trained off-device.
- [ ] **T079** — Convert adapter via MediaPipe ConversionConfig  
  `P0` `M` · after: T078
  - **⚠ There is no adapter to convert** (T037, 2026-09-23). This becomes converting a **fully fine-tuned checkpoint**, and it is now the riskiest step in the section: §4.4 names it unproven, T030's convert half was never exercised because pre-converted artifacts exist, and MediaPipe's Torch Generative converter is documented **CPU-only** — which would forfeit the GPU backend. **Spike this before T078 spends a training run.**
- [ ] **T080** — Deploy adapter, verify load on GPU backend  
  `P0` `M` · after: T079
  - **⚠ Deploys a converted model, not an adapter** (T037, 2026-09-23). `setSupportedLoraRanks`/`setLoraPath` do not apply. What still applies is the on-device verification: T031's extension rule (`.task` selects the zip loader, anything else the flat one) and T038's memory budget.
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
  - **Explicitly v2, not blocked — deliberately deferred.** Two findings from 2026-09 make it cheaper and more attractive than it looked: MediaPipe's LLM stack is **already LiteRT-LM underneath** (every error path in the native library names `third_party/odml/litert_lm/runtime`), so this is likely an API change over the same engine rather than a change of engine; and `litertlm-android` is actively released at 0.17.1 while `tasks-genai` has not moved since 0.10.35 in April. It is also the likely route to **T190's constrained decoding** and to the FunctionGemma artifacts, which publish as `.litertlm` rather than `.task`.
- [ ] **T092** — Benchmark MediaPipe vs LiteRT-LM backends  
  `P2` `M` · after: T091
  - Strong portfolio artifact in its own right.


## ⚑ Open questions (standing)

**Not sequential.** Each gates specific work — resolve when its dependent task comes up, not all at once.

- [ ] **T093** — Verify Azure AI Language F0 monthly allowance  
  `P2` `S`
  - Before depending on it. Auto-categorisation is a stretch goal, not critical path.
- [x] **T094** — Confirm Gemma licence permits hosting converted artifact  
  `P1` `S`
  - Otherwise require user-side download from Hugging Face.
- [x] **T095** — Confirm Blob free-tier egress survives multi-GB downloads  
  `P1` `S` · after: T051
  - A single re-download could dominate the monthly allowance.
- [ ] **T096** — Confirm Entra free tier covers 1 app, 2 platform configs  
  `P1` `S` · after: T040
  - Expected yes; verify before building auth.
- [ ] **T097** — Decide whether aliases sync  
  `P2` `S`
  - Currently no. Desktop has no apps to launch, so probably still no.
- [ ] **T186** — Decide what to do with four reserved commands nobody uses  
  `P2` `S` · after: T014 · blocks: T074
  - **The first T014 export (2026-09-21) shows `:notes`, `:tasksdone`, `:alias` and `:settings` were used zero times in 27 days of real daily driving.** Not rarely. Zero. Meanwhile `:google` ran 60 times and `:apps` 147. This is the launcher's own usage data answering a question nobody asked it.
  - **Why it is an open question and not a task:** the four are not one problem. `:settings` and `:alias` have obvious substitutes already in use — Settings is reachable by typing `sett` and fuzzy-matching the app (the log shows exactly that, 3 times, plus `:setting` itself falling through to the app twice), and aliases get *defined* rather than listed. `:notes` and `:tasksdone` have no substitute, which points at discoverability instead of redundancy: `:tasks` was used 12 times by the same person who never once asked what was already done.
  - **It gates T074, which is why it is worth answering before section 12 rather than after.** Synthetic examples are generated per intent, and generating a few dozen phrasings for a command that is about to be cut wastes the budget twice — once writing them, once when they dilute the held-out split for the commands that matter. Cheaper to decide than to undo after T077 freezes.
  - **The wrong answer is to delete them on this evidence alone.** One user, one month, and a launcher whose whole premise is that typing beats hunting — a command nobody discovered is not the same as a command nobody wants, and `:help` was itself used once. Shipping `:notes` and then finding it unused is a finding; shipping nothing and inferring it would not have been used is not.
  - Resolve by picking one per command: keep and make discoverable (surface in `:help` output or the empty-state hint), keep and stop spending dataset budget on it, or remove and free its token. Record which and why — **§4.3's label space has an entry for each**, so removing one means removing a label, and after T077 that means retraining.

---

**188 tasks — 105 done, 83 open** (recounted 2026-09-24, after cutting T120). Originally generated from the Todoist backlog; keep both in step when either changes. The count drifts as tasks are split (T006 → T140–T145) or added from use (T146–T151) — recount rather than trusting this line.
