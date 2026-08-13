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

**Commit format:** `<type>(<scope>): T0xx <summary>` — e.g. `feat(desktop): T052 scaffold WPF project`

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
- [ ] **T105** — Directory.Build.props + Central Package Management  
  `P1` `S` · after: T098
  - `Directory.Packages.props` with `ManagePackageVersionsCentrally`. *AZ-400 D3: package management.*
- [ ] **T106** — ADR directory + backfill decisions already made  
  `P0` `M` · after: T098
  - `docs/adr/`. Backfill: WPF over Avalonia/WinUI, MediaPipe over llama.cpp, Markdown over rich-text JSON, conflict-copies over plain LWW, monorepo over split repos. These are the questions an interviewer asks.
- [ ] **T107** — PR template + commit convention referencing task IDs  
  `P1` `S` · after: T098
  - `feat(desktop): T052 scaffold WPF project`. *AZ-400 D1: traceability.*
- [ ] **T108** — Branch strategy + protection rules  
  `P0` `M` · after: T098
  - Trunk-based, short-lived branches. Protect `main`: require PR, require status checks, no direct pushes — even solo. *AZ-400 D2.*
- [ ] **T109** — DECIDE: Azure Boards vs Todoist for work-item traceability  
  `P1` `S`
  - AZ-400 D1 wants commit→work-item→build linkage; Todoist can't do it. Suggested compromise: mirror only the *active* section into Boards, then judge whether it earned the duplication.
- [ ] **T110** — README skeleton  
  `P1` `S` · after: T098
  - Stub now, fill in T088.


## 02 · Launcher shell

Daily-drivable as your real home screen, zero ML.

- [ ] **T001** — Create Android project (Compose, pinned min/target SDK)  
  `P0` `S`
  - Pin the ONE target device here; all benchmarks cite it.
- [ ] **T002** — Register HOME activity intent filters  
  `P0` `S` · after: T001
  - `ACTION_MAIN` + `CATEGORY_HOME` + `CATEGORY_DEFAULT`.
- [ ] **T003** — Build app index from PackageManager  
  `P0` `M` · after: T001
  - Cache it; refresh on package install/uninstall broadcast.
- [ ] **T004** — CLI shell UI: scrollback + input line, IME auto-open  
  `P0` `M` · after: T002
- [ ] **T005** — Dark-only theme tokens  
  `P1` `S` · after: T004
  - No light palette exists by design.
- [ ] **T011** — Launch app by package intent  
  `P0` `S` · after: T003
- [ ] **T012** — Open all-apps list view  
  `P1` `M` · after: T003
- [ ] **T013** — Open Settings via intents  
  `P1` `S` · after: T006
- [ ] **T015** — BACK/HOME key behaviour  
  `P1` `S` · after: T004, T020
  - BACK is a no-op at the prompt, but **closes the editor overlay** when open — the only state where BACK does anything. HOME re-focuses input.
- [ ] **T016** — Scrollback cap + clear on process death  
  `P2` `S` · after: T004
  - Ephemeral, never synced.
- [ ] **T017** — Install as daily driver; keep fallback launcher installed  
  `P0` `S` · after: T011
  - Never ship an untested build to your only home screen.


## 03 · Command pipeline (T0/T1/T3)

Tiered cascade. **The SLM is not the front door.**

- [ ] **T006** — T0 reserved command parser (:apps :settings :note :alias)  
  `P0` `M` · after: T004
- [ ] **T007** — Alias store (Room) + T0 resolution  
  `P1` `M` · after: T006
  - Device-local, not synced.
- [ ] **T008** — T1 fuzzy matcher (edit distance + prefix/acronym boost)  
  `P0` `L` · after: T003
  - The 90% path. Must feel instant.
- [ ] **T009** — T1 confidence thresholding + short-circuit  
  `P0` `M` · after: T008
- [ ] **T010** — T3 fallback handler + help output  
  `P0` `S` · after: T009
  - The safety net that makes the SLM shippable.
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
