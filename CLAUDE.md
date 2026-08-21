# dailyDriver

Personal-use side project: an Android CLI-style home-screen launcher backed by an on-device SLM (MediaPipe, cascaded T0–T3 command pipeline), a native WPF (.NET 10) desktop notes client with full offline capability, and a C# Azure Functions backend on a fixed $0/month budget. Notes are shared across both clients via a local-first outbox sync with server-assigned `rev` ordering and conflict-copy resolution. The project doubles as a portfolio piece for a .NET job search — end-to-end .NET (WPF/MVVM, EF Core, isolated-worker Functions) and a full on-device GenAI pipeline (fine-tune → quantize → deploy → benchmark) are first-class goals, not side effects.

## CURRENT PHASE

**Phase 0 — Launcher shell.** Monorepo scaffolding (section 01) complete. CLI shell, app launching, and Room/aliases (T001–T005, T007, T011–T013) are built and on-device verified on the S20 FE.

**The full T0–T3 pipeline is live.** T140's dispatcher registers all six reserved commands — `:apps`, `:settings`, `:note` (placeholder until T020's editor exists), `:alias`, `:notif`, `:help`/`?` (T141–T145, T147). T1 (T008/T009) does fuzzy app-matching with no ambiguity gate — best match always wins. T3 (T010) now gives unmatched input a real answer pointing at `:help` instead of a placeholder. **Every reserved command in the design doc exists; only T2 (the SLM, T036) is missing** — exactly the Phase 0 exit shape.

T146/T148/T149 added the status row (clock, date, unread-notification dot) and immersive fullscreen (both system bars hidden). T150 swept the unit suite from 187 to 410 tests and fixed two latent bugs it found.

**Not yet verified on-device:** T146, T147, T148, T149 — permission grant/revoke and real notification/window behavior can't be unit-tested, so these are the ones to actually run before trusting them.

**Phase 1 has started ahead of Phase 0's exit gate.** **T020/T021 landed the note editor** — `:note` opens a bottom-anchored overlay that grows as you write, autosaves on a 500ms debounce, and returns you to the prompt with the keyboard up. Verified on-device. T018 landed the notes/outbox/tags Room schema (`dailydriver.db`, replacing T007's aliases-only database — one DB, because a transaction can't span two SQLite files and the outbox guarantee depends on it), verified by 20 instrumented tests on an emulator. Those tests earned their keep immediately: they caught `INSERT OR REPLACE` on a note silently cascade-deleting its pending outbox row. T151 tracks Room schema export, which is off due to a kotlinx-serialization conflict.

**Still open:** T014 (training-data log — land **before** T017, since daily-driving without it throws away real usage T075 needs), T016 (scrollback cap), T017 (install as daily driver — the Phase 0 exit gate, not yet done). **T015 is now unblocked** — T020 shipped, and already handles BACK for the editor overlay, so what's left there is HOME re-focusing the input.

Exit criterion: daily-drivable as the real home screen with T0/T1/T3 only, zero ML. Update this line as phases complete — see `docs/design.md` §9 for the full phase table.

## Docs

Two audiences, two sets of files. Don't confuse them:

- [`docs/design.md`](docs/design.md) — architecture, decisions, and rationale. Read before making any design-affecting change.
- [`docs/tasks.md`](docs/tasks.md) — task list and current status. Check before starting work; update as tasks move.
- [`docs/adr/`](docs/adr/) — architecture decision records, permanent once accepted.
- [`website/`](website/) — **the public, employer-facing site** (MkDocs Material → GitHub Pages). Short summary pages organised by project area, written for an interviewer rather than a builder. It *links out* to the files above rather than rendering them; `docs/` is the source of any fact, `website/` is the summary.

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
