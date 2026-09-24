# dailyDriver

Personal-use side project: an Android CLI-style home-screen launcher backed by an on-device SLM (MediaPipe, cascaded T0–T3 command pipeline), a native WPF (.NET 10) desktop notes client with full offline capability, and a C# Azure Functions backend on a fixed $0/month budget. Notes are shared across both clients via a local-first outbox sync with server-assigned `rev` ordering and conflict-copy resolution. The project doubles as a portfolio piece for a .NET job search — end-to-end .NET (WPF/MVVM, EF Core, isolated-worker Functions) and a full on-device GenAI pipeline (fine-tune → quantize → deploy → benchmark) are first-class goals, not side effects.

## CURRENT PHASE

> ### ➤ Next: finish the desktop client to the T063 milestone.
>
> **Android is done.** Sections 01–06 are complete apart from items explicitly blocked on a model
> reaching the device (T087), and each of those now says so in its own record. Everything left is
> .NET and Azure.
>
> **The desktop client is the .NET job-hunt artifact** and it is 6 of 13 done. T063 — "desktop
> usable offline as a standalone notes app" — is the nearest real milestone, depends on neither
> the SLM nor the backend, and is the part a hiring manager actually opens. It has had one session;
> Android has had a dozen.
>
> **Then 07 backend → 09 sync**, which is where the .NET depth is: isolated-worker Functions,
> Cosmos, and an offline sync engine with conflict copies.
>
> **Sections 10 and 11 are AZ-400 exam surface — 29 of the 83 open tasks.** Section 10's own
> description says to work them alongside study rather than upfront. Do that; they are the largest
> block on the board and the least load-bearing for the portfolio.
>
> **⚠ Don't cut a release yet.** The APK is 51.2 MB against 22.0 MB at `android-v0.4.2`, and 26.6 MB
> of that is a native inference library with no model to load. A release buys the device nothing
> until T087 lands.

**Phase 0 is complete (2026-08-22).** The launcher is installed on the S20 FE from a signed GitHub Release and set as the real home screen, with One UI Home kept as a fallback (T017). That was §9's exit criterion: daily-drivable on T0/T1/T3 with zero ML. Sections 01–03 are empty.

**The full T0–T3 pipeline is live.** All twelve reserved commands are registered through T140's dispatcher (`:apps`, `:notes`, `:note`, `:task`, `:tasks`, `:tasksdone`, `:alias`, `:notif`, `:settings`, `:google`, `:export`, `:help`/`?`), T1 does fuzzy app-matching with best-match-wins, and T3 answers unmatched input by pointing at `:help`. **✅ T2 landed at T036 (2026-09-23), so the cascade is complete for the first time.** It runs only where T0 and T1 have both missed, which is structural rather than enforced, and **`NoInferenceEngine` is still what ships** — so a device with no model behaves exactly as before. A validated intent is mapped back to a command line and dispatched as if typed, so T2 gets no actions of its own.

**T014's training-data log is running**, which is why it was sequenced before T017: every day of daily-driving without it discards real usage that §8's dataset needs and that cannot be reconstructed later. It records the *resolution* rather than a §4.3 intent label (that label space is stale — see the warning in §4.3), logs T3 misses as the `unknown` examples, deduplicates with a count, and never syncs. Export with **`:export`** at the prompt, which writes JSONL to Downloads and works on the signed release build; `scripts/export-command-log.sh` does the same from adb but only against a debuggable build (T166).

**Phase 1 is well underway.** Notes, checklists and tasks all work: Room-backed storage with a transactional outbox, an inline editor with autosave, hashtag parsing and tag chips, checklists with drag-reorder and note↔checklist conversion, due dates with typed shortcuts, `:task`/`:tasks`/`:tasksdone` over a derived task index (ADR-0007), and one-deep undo for every destructive action.

**Desktop client is underway** (T052–T055: Generic Host + DI, EF Core/SQLite with real migrations, CommunityToolkit.Mvvm, and the two-pane shell — both panes still placeholders pending T056/T057. See [`desktop/CLAUDE.md`](desktop/CLAUDE.md) for current state). **Not started:** the backend. **Section 06's SLM spike is largely done** — a Gemma-3 270M loads and answers correctly on the phone through the shipping code path, the model is chosen on measurement (T039), and T2 is wired (T036). What is left there is T037's LoRA gate, no longer urgent since [ADR-0011](docs/adr/0011-constrained-decoding-and-fine-tuning-as-experiment.md) made fine-tuning an experiment, and T190's constrained decoding, parked on library support.

**CI runs on GitHub Actions** (T158/T159): unit tests, lint and instrumented tests on an API 33 emulator per PR, and a signed APK attached to a GitHub Release on an `android-v*` tag. This coexists with section 10's Azure Pipelines work rather than replacing it — that section is AZ-400 exam surface and keeps the environments, approval gates and IaC. **Install releases with `adb install -r`, not by tapping the APK**; see `docs/releasing.md` for why, and note the launcher has no app-drawer icon by design.

## Docs

Two audiences, two sets of files. Don't confuse them:

- [`docs/design.md`](docs/design.md) — architecture, decisions, and rationale. Read before making any design-affecting change.
- [`docs/tasks.md`](docs/tasks.md) — task list and current status. Check before starting work; update as tasks move.
- [`docs/adr/`](docs/adr/) — architecture decision records, permanent once accepted.
- [`website/`](website/) — **the public, employer-facing site** (MkDocs Material → GitHub Pages). Four short pages, one per project area: Android, Desktop, Azure backend, ML pipeline. Written for an interviewer, not a builder. It *links out* to the files above rather than rendering them; `docs/` is the source of any fact, `website/` is the summary. Keep the prose plain: no em-dashes, no "not X, it's Y" constructions, no aphoristic closing lines.

### ⚠️ Keep the site current

**`website/` must be updated whenever development completes a feature or changes a project area's status.** It is a portfolio surface a potential employer may be reading, and a site claiming something is unbuilt after it ships — or, worse, claiming something works that doesn't — is actively harmful in a way that stale internal docs are not.

When finishing a task that changes what a project area *does* or *is*:

1. Update the relevant `website/<area>.md` page.
2. Update the status table on `website/index.md` if the area moved between not-started / working.
3. Update the "Last updated" date on `website/index.md`.
4. Replace a stub page wholesale when its area goes from designed to built — the stubs are written to be thrown away, not amended.

Accuracy matters more than completeness here: **never describe unbuilt work in the present tense.** Every page carries a status admonition at the top; keep it honest.

## Hard constraints

These are easy to get wrong by defaulting to the more common/tutorial answer. Do not deviate without discussing it first.

- **Azure Functions: isolated worker model only.** Never write or copy in-process-model code. In-process support ends **2026-11-10** — most tutorials and Stack Overflow answers predate this and show the wrong model.
- **Cosmos DB: provisioned throughput, not serverless.** The free tier requires the provisioned-throughput capacity mode and is only selectable **at account creation**. Selecting serverless silently forfeits the free tier and cannot be fixed later without recreating the account. ("Serverless" applies to Functions compute, not to Cosmos.)
- **The SLM never sits on the launcher's fast path.** It is one tier (T2) of a cascade behind T0 (exact match) and T1 (fuzzy match); T3 is the non-ML fallback. The launcher must stay fast and fully usable even if the model is absent, loading, or wrong.
- **Notes are stored as Markdown, not rich-text JSON.** Checklists are `- [ ]` / `- [x]` lines parsed into structured items. This is the wire and storage format for both Android (Room) and desktop (EF Core/SQLite) — do not introduce a rich-text document model (Quill Delta, ProseMirror, etc.).
- **A task is a dated checklist item, authored only in the note body.** Due dates are `@YYYY-MM-DD` tokens inside the body, next to the `#hashtags` (§4.8, ADR-0007 superseding ADR-0006). There **is** a `tasks` table, but it is a **derived index**, not a store: only ever written by deriving it from a body in the same transaction as the note write, rebuildable from scratch, and never pushed or pulled — the same shape as `note_tags`. Nothing may insert a task directly, and no `NoteType.TASK` or due-date column on `notes`. Date-only, never a time, so timezones stay out of the wire format. **[ADR-0008](docs/adr/0008-server-side-task-index.md) (2026-08-25) added a server-side derived index too** — the server derives its own from `body` so it can answer "what is due today" for digests and notifications. That changes nothing above: tasks are still authored only in the body, still derived everywhere, and still never a synced entity, so no client ever sends one. Adding a time to the token to make the server's "today" easier is the specific wrong answer 0008 names. Input shortcuts (`@tomorrow`, `@t+3`) are rewritten to ISO before the body is written and must never be persisted.

## Branching

Trunk-based. `main` is the only long-lived branch; everything else is short-lived and disposable.

- **Naming:** `<type>/<task-id(s)>-<short-desc>`, e.g. `feat/T052-scaffold-wpf`, matching the commit-type prefixes below. One branch per task or tight cluster, mirroring the "one Claude Code session per task or cluster" guidance in `docs/tasks.md`.
- **Short-lived means ~a week.** This is a side project, not a team sprint — don't let a branch outlive that without a reason.
- **Merge via rebase or merge commit, never squash.** Squash-merging collapses every commit's task-ID reference into one, which defeats the traceability the commit convention exists for, and makes `git blame`/reversion coarser than it needs to be.
- **`main` is protected:** PR required, 0 required approvals (solo repo — GitHub won't let you self-approve toward a required count), administrators included in the restriction (no direct pushes, even from the owner), force-push and deletion blocked. Required status checks are deferred until T123 stands up a PR-validation pipeline to check against.

## Commits

Conventional commits, with the task ID leading the summary:

```
<type>(<scope>): Txxx <summary>
```

e.g. `feat(android): T003 build app index from PackageManager`

- **Types in use:** `feat`, `fix`, `docs`, `chore`, `style` — the last for changes with no behavioural effect at all (a glyph swap, a font size), where calling it `fix` would overstate what happened.
- **Scope is the module** — `android`, `desktop`, `backend`, `infra` — and is **omitted when the change is repo-wide**, which is why `docs:` and `chore:` commits usually carry none.
- **Reference the task ID** from `docs/tasks.md` (format `Txxx`) whenever a commit serves one. Housekeeping with no task behind it (`chore: remove fulfilled .github/TODO.txt placeholder`) legitimately has none — don't invent one to fill the slot.
- **Multiple tasks are space-separated**, e.g. `docs: T100 T101 T102 module CLAUDE.md files`.

This matches `.github/pull_request_template.md`'s checklist, which is the other place this is written down — keep the two in step.

**Corrected 2026-08-19.** This section used to read "Reference the task ID … e.g. `T098 · Create monorepo skeleton`" — the shape of the repo's *first* commit, from before the convention settled at T107 (`docs: T107 PR template + commit convention fix`). The example was never updated to match, so it contradicted the PR template for ~140 tasks and misled a later session into a run of non-conforming `Txxx · summary` commits. Those stay in history; don't rewrite them, and don't copy them either.
