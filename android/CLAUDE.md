# android/ — CLI Launcher

Registered as the device HOME activity: a single full-screen Compose surface (scrollback transcript + input line) that replaces the home screen and routes input through the T0–T3 command pipeline. Room is the local source of truth for notes, checklists, and aliases. See root [`../CLAUDE.md`](../CLAUDE.md) for project-wide constraints and [`../docs/design.md`](../docs/design.md) §4 for the full design.

Current state: `com.example.dailydriver`, `minSdk`/`targetSdk` pinned to 33, `compileSdk` at 36 (see note below), Compose + Material3. Working end to end and on-device verified: the CLI shell (`shell/`) with a live-filtering, tap-or-Enter-or-auto-launch `:apps` browse view backed by the real app index (`apps/`, wired into `MainActivity` at T012). `settings/SettingsLauncher` is now wired in — `:settings` fires it via T140's dispatcher (T142). Room now exists (T007, `alias/`) — aliases only so far, no notes/checklists schema yet. No MediaPipe dependency yet. T0 now has a real dispatcher (`command/ReservedCommandParser.kt`, T140) — `:apps` and `:settings` are both registered through it; `:note`/`:alias`/`:help` each need one registry line to add themselves (T143–T145). T1 (T008) is live — fuzzy app matching previews and auto-launches on Enter/pause. T009 added the whitespace-based T2/T3 escalation classification (`command/EscalationTier.kt`), but T2/T3 (T010) still have no real handler — an unmatched submit writes one of two placeholder lines depending on which gap it hit.

**Running Gradle from the command line just works** — no `JAVA_HOME=` override needed. The system-wide `JAVA_HOME` on this machine points at JDK 11, which AGP 8.10 rejects (requires 17+), but `~/.gradle/gradle.properties` (machine-local, outside this repo, not something a fresh clone gets) pins `org.gradle.java.home` to Android Studio's bundled JBR. If a fresh environment hits "Android Gradle plugin requires Java 17," that's this setting missing, not a project problem — same fix, or point `JAVA_HOME` at a real JDK 17+.

## Target device

**Samsung Galaxy S20 FE 5G, model SM-G781V — Snapdragon 865 — Android 13 (API 33).** Pinned at T001; every latency/memory/battery benchmark in the project cites this device. `minSdk`/`targetSdk` are set to 33 to match it exactly — no broader compatibility is a project goal, so there's no reason to target a wider runtime range.

`compileSdk` is 36, not 33 — it's a build-time-only setting (which API surface the compiler sees), independent of the runtime `minSdk`/`targetSdk` above. Already-present AndroidX dependencies (`core-ktx` among them) hard-require `compileSdk 36`; keep it at whatever the newest dependency needs, and don't conflate it with the device-pinning decision when adding future dependencies.

SM-G781V is the Verizon 5G variant, which ships with Snapdragon 865 — relevant once GPU-backend LoRA work starts (T037), since the Exynos 990 variant of this phone (the international 4G-only SM-G780F) is a different chipset with different GPU/LoRA behavior.

## Testing — TDD is the default workflow

Write the test before the implementation for anything with logic in it. This module has more test-shaped surface than most Android apps — tier resolution, JSON repair, Room queries, and merge/tag parsing are all pure-ish functions with clear inputs and outputs, which is exactly where TDD pays for itself.

- **Red, green, refactor.** For new pipeline logic, DAOs, parsers, or ViewModels: write a failing unit test first, make it pass with the minimum code, then clean up. Don't write the implementation and backfill tests afterward — if a task shows up without tests in the same commit, treat that as unfinished, not done.
- **Keep pure logic Android-independent** so it lives in `src/test/` (fast, JVM-only JUnit) instead of `src/androidTest/` (slow, needs a device/emulator). T1 fuzzy matching, T2's JSON extraction/repair/validation chain, tag/hashtag parsing, and alias resolution should all be plain Kotlin with no `android.*` imports, tested with local JUnit.
- **Cover the edge cases the design doc calls out as risky**, not just the happy path: empty/whitespace-only input, malformed JSON (trailing commas, unquoted keys, markdown fences) at every stage of the T2 repair pass, low-confidence and `unknown`-intent outputs that must drop to T3, and T0/T1 boundary cases (ties, near-threshold fuzzy scores).
- **Room DAOs get instrumented or in-memory-database tests before the schema is treated as stable — when the DAO itself carries real logic to get wrong.** The outbox (T018+) is the clear case: a mutation whose transactional write isn't covered by a test isn't trusted. A DAO that's just a `SELECT *` and an `@Insert(onConflict = REPLACE)`, with every actual decision (conflict detection, parsing, cycle checks) living in already-tested pure functions elsewhere — `AliasDao`, T007, is the precedent — doesn't need its own instrumented test just to exercise Room's own well-tested codegen. Judge by how much logic the DAO itself carries, not by "it's a DAO."
- **The shared fixture corpus is the contract, not an afterthought** (T070–T072). Sync/merge logic is TDD by construction here: the JSON corpus (`base`/`local`/`remote`/`expected`) is written before the Kotlin implementation that must satisfy it, and the same corpus drives the C# xUnit suite. If Kotlin and C# disagree, that's a failing test, not a judgment call.
- **Compose UI tests** (`androidx-ui-test-junit4`, already in the scaffold) cover the critical interaction paths — editor overlay open/dismiss, checklist check/reorder, keyboard shortcuts — using semantics/test tags rather than brittle text matching. UI tests are lower priority than the logic tests above; don't let them block on the emulator becoming available.
- No test dependencies beyond what's already in `libs.versions.toml` (JUnit4, AndroidX Test, Espresso, Compose UI test) are assumed. Adding a framework (Robolectric, Turbine, MockK, etc.) is a deliberate one-line addition to the version catalog, not an incidental import.

## Kotlin/Compose conventions

- **One Activity.** The launcher, the editor, and every overlay live in the same Activity as Compose surfaces — no `startActivity`, no navigation library, no extra Recents entries. The editor (§4.6 of the design doc) is a Compose overlay composited over the launcher, not a separate screen.
- **State hoisting.** Composables stay stateless where practical; state lives in a ViewModel (`androidx.lifecycle.viewmodel.compose`) or the Room-backed repository layer beneath it, not in the composable itself.
- **Dark-only theme.** `ui/theme/` defines one palette. Do not add a light theme or a theme toggle — this is a deliberate asymmetry with the desktop client (design doc §5.3).
- **Package namespace.** Still the Android Studio default (`com.example.dailydriver`). Rename deliberately in one commit if/when a real namespace is chosen — don't let it drift piecemeal across files.

## T0–T3 command pipeline

The command pipeline is the core of the app. **The SLM (T2) is never the front door** — see the root hard constraints.

| Tier | Handler | Latency budget | Handles |
|---|---|---|---|
| T0 | Exact match — reserved commands (`:apps`, `:settings`, `:note`, `:alias`, `:help`/`?`) and user aliases | < 5 ms | Muscle-memory input |
| T1 | Fuzzy match over the `PackageManager` app index (normalized edit distance + prefix/acronym boost) | < 50 ms | `chr` → Chrome |
| T2 | SLM intent + slot extraction (MediaPipe) | ~1 s | Free-form natural language |
| T3 | Fallback — app-name search, else help | — | Everything else |

- Escalate T0 → T1 → T2 only when the prior tier's confidence is below threshold; T1 short-circuits on a single high-confidence match.
- Input containing whitespace with no T0/T1 hit goes straight to T2 — multi-word input is almost always prose, don't waste a tier on it.
- **Log every T0/T1 resolution** as `(raw_input, resolved_intent)` locally (T014). This is the fine-tuning dataset's real-usage seed — treat it as load-bearing, not telemetry.
- T3 is what makes T2 shippable: the SLM is allowed to fail, the launcher is not.

## Room patterns

- **Outbox from day one** (T018). Every mutation to notes/checklists/aliases writes to an outbox row in the same transaction as the entity change — this is what makes sync resumable after an interrupted push. Do not add a table that can be mutated outside a transaction that also touches the outbox.
- **Markdown is the stored format**, per the root constraint — not a rich-text model. Checklist items are `- [ ]` / `- [x]` lines parsed into structured items for the UI, not stored as separate normalized rows. Hashtags (`#work`) are parsed out of the body on save and denormalized to a `tags` column/array for querying; the body stays the single source of truth.
- **Aliases are device-local and not synced** (open item T097, currently resolved as "no"). Don't add an alias sync path without revisiting that decision first.
- Room is authoritative; the network is an optimization. No UI read or write should ever await a network call — that's the desktop client's rule too (design doc §5.4), and Android follows the same shape even though it isn't stated as an explicit constraint there.

## MediaPipe (T2) — GPU backend + LoRA constraints

- **Runtime:** MediaPipe LLM Inference API (`LlmInference` / `LlmInferenceOptions`). All inference sits behind an `InferenceEngine` interface (`suspend fun infer(prompt: String): String` + lifecycle) — `MediaPipeEngine` is the only implementation for now, but the interface exists so `LiteRtLmEngine` can be swapped in later (MediaPipe is in maintenance-only mode upstream; see design doc §4.4).
- **LoRA requires `backend = "gpu"`.** It is not available on CPU backend, applies to attention layers only, and MediaPipe documents it as experimental. Do not attempt LoRA on the CPU backend.
- **Model choice is gated on LoRA support**, not picked upfront: candidates are Gemma-2 2B (LoRA explicitly documented) and Gemma-3 1B 4-bit (smaller/faster, LoRA support undocumented). T037 is a timeboxed GO/NO-GO spike on the target device before committing — don't build downstream fine-tuning work against a model that hasn't cleared that gate.
- LoRA adapters are trained with PEFT (`target_modules = ["q_proj","v_proj","k_proj","o_proj"]`) and converted with MediaPipe's `converter.ConversionConfig()`.
- **No grammar-constrained decoding** in MediaPipe (unlike llama.cpp's GBNF). Schema adherence for the intent/slot JSON output is defended in layers, in order: tight few-shot prompt with the schema inline → low temperature (~0.1) → extract the first balanced `{...}` span → repair pass (trailing commas, unquoted keys, markdown fences) → validate against schema, drop to T3 on failure or low confidence. Don't skip layers to simplify — each one is there because the previous one alone was measured as insufficient.
- Model weights are downloaded at first run (WorkManager, SHA-256 verified, unmetered network only) from Blob Storage — never bundled in the APK. The LoRA adapter (single-digit MB) may be bundled. The launcher must be fully functional at T0/T1/T3 before the model exists on-device.
