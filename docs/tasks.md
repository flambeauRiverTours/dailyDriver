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
- [ ] **T099** — Root CLAUDE.md  
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
- [ ] **T015** — BACK/HOME key behaviour  
  `P1` `S` · after: T004, T020
  - BACK is a no-op at the prompt, but **closes the editor overlay** when open — the only state where BACK does anything. HOME re-focuses input.
- [ ] **T016** — Scrollback cap + clear on process death  
  `P2` `S` · after: T004
  - Ephemeral, never synced.
- [ ] **T146** — Status line: time + date above the transcript, flashing colon  
  `P1` `S` · after: T004
  - **New (2026-08-16, user request)** — not in the design doc; §4.1 describes only the transcript and input line. A fixed status line at the top of the shell surface showing the current time and date, with the time's colon blinking on a one-second cycle (the classic digital-clock/terminal look).
  - **Keep the formatting pure and unit-tested** — `(Instant, ZoneId, colonVisible: Boolean) -> String` is exactly the kind of Android-independent logic `android/CLAUDE.md`'s testing section says belongs in `src/test/`. Only the ticking itself needs Compose.
  - **Battery matters here in a way it doesn't elsewhere in this app.** This is the home screen; a naive `while(true) { delay(1s) }` recomposing forever is a real drain, and unlike a normal app there's no "user closed it" to save you. Tick from a `LaunchedEffect` that stops when the Activity isn't resumed, and scope recomposition to just the status line rather than the whole shell.
  - **Decisions to make when building it, none settled by the design doc:** 12h vs 24h (follow the system setting via `DateFormat.is24HourFormat`, or pin one?); the exact date format; whether the colon is 500ms-on/500ms-off or 1s-on/1s-off (the request said "one second flashing," which reads as the full cycle); and whether the status line stays visible in `:apps` browse mode (it replaces the transcript, not the whole surface — so probably yes, but confirm).
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
- [ ] **T142** — `:settings` reserved command  
  `P1` `S` · after: T140, T013
  - Routes to the Settings intent T013 builds.
- [ ] **T143** — `:note` reserved command  
  `P1` `S` · after: T140, T020
  - Opens the editor overlay. Design doc §4.6 also shows `note standup` (space-separated, with a name argument) as the worked example — confirm whether bare `:note` and `note <name>` are the same command or two before building this; the design doc doesn't fully reconcile the colon-prefix table (§4.2) against the prose example (§4.6).
- [ ] **T144** — `:alias` reserved command  
  `P1` `S` · after: T140, T007
  - Lists stored aliases (§4.7). Distinct from T007's user-alias *resolution* (typing `gm` and having it resolve) — this is the no-argument `:alias` listing what's defined.
- [ ] **T145** — `:help` / `?` reserved command  
  `P1` `S` · after: T140
  - **New — not in the original design doc's reserved-command list.** Two aliases, `:help` and a bare `?`, route to the same handler. Owns the actual help text; T010's T3 fallback reuses this content rather than defining its own copy when nothing else matches.
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
- [ ] **T008** — T1 fuzzy matcher (edit distance + prefix/acronym boost)  
  `P0` `L` · after: T003
  - The 90% path. Must feel instant.
- [ ] **T009** — T1 confidence thresholding + short-circuit  
  `P0` `M` · after: T008
- [ ] **T010** — T3 fallback handler + help output  
  `P0` `S` · after: T009, T145
  - The safety net that makes the SLM shippable. Reuses T145's help content rather than defining its own — same text whether the user typed `:help` explicitly or fell through to T3 with no match.
- [ ] **T014** — Log (raw_input, resolved_intent) for every T0/T1 hit  
  `P0` `S` · after: T009
  - **THIS IS TRAINING DATA.** Do it now or section 12 has nothing real to learn from.


## 04 · Notes storage

Room schema, Markdown format, outbox from day one.

- [ ] **T018** — Room schema: notes, outbox, aliases  
  `P0` `M` · after: T007
  - Outbox from day one — retrofitting it later is painful.
- [ ] **T019** — Markdown as storage format + hashtag parsing to tags  
  `P0` `M` · after: T018
  - Load-bearing: keeps desktop parity with Markdig, diffs cleanly, no proprietary lock-in.
- [ ] **T025** — Note list + open by name  
  `P0` `M` · after: T018
- [ ] **T026** — Local note search  
  `P1` `M` · after: T018


## 05 · Notes editor (Keep-style)

**v3 design.** Google Keep-style touch editor rendered *inline* in the CLI surface. Entry is still a CLI command; the editor is an overlay, not a separate Activity.

- [ ] **T020** — Inline editor overlay (Compose, same Activity)  
  `P0` `M` · after: T004
  - No `startActivity`, no window transition, no extra Recents entry. Transcript stays visible behind. On dismiss, restore focus to the input line with IME open.
- [ ] **T021** — Title + body fields with debounced autosave  
  `P0` `M` · after: T020
  - **Autosave is the real save.** Debounce to Room continuously so process death / a call / a swipe-away loses nothing. The buttons mean *done*, not *commit*.
- [ ] **T022** — Top-right action bar: close, confirm, overflow  
  `P0` `M` · after: T020
  - `[x]` closes, `[check]` confirms and closes. Overflow: delete, convert note↔checklist, hide checked items.
- [ ] **T023** — Checklist items: add, check, delete, drag-reorder  
  `P0` `L` · after: T020, T019
  - Keep behaviour: checking an item moves it *below* the unchecked ones. Serialises to `- [ ]` / `- [x]` lines.
- [ ] **T024** — Hide-checked-items + convert note ↔ checklist  
  `P1` `M` · after: T023
  - Conversion flips `type` and reformats the body; must round-trip without losing content.
- [ ] **T027** — Keyboard shortcuts so the editor stays keyboard-finishable  
  `P0` `S` · after: T022
  - `Esc` closes, `Ctrl+Enter` confirms, `Tab`/`Enter` walks items. A note started at the prompt must be finishable without reaching for the screen.
- [ ] **T138** — Undo snackbar for destructive actions  
  `P1` `M` · after: T023, T024
  - Autosave removed the `:w` safety net — a mis-tap is now instantly durable and syncs. Undo replaces it. Soft-delete + timer, reusing the tombstone mechanism.
- [ ] **T139** — Tag chips row + inline #hashtag parity  
  `P1` `M` · after: T021, T019
  - Chips and inline `#work` must produce the same `tags` array — one operation, not two sources of truth.


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

**139 tasks.** Generated from the Todoist backlog; keep both in step when either changes.
