# dailyDriver

Personal-use side project: an Android CLI-style home-screen launcher backed by an on-device SLM (MediaPipe, cascaded T0–T3 command pipeline), a native WPF (.NET 10) desktop notes client with full offline capability, and a C# Azure Functions backend on a fixed $0/month budget. Notes are shared across both clients via a local-first outbox sync with server-assigned `rev` ordering and conflict-copy resolution. The project doubles as a portfolio piece for a .NET job search — end-to-end .NET (WPF/MVVM, EF Core, isolated-worker Functions) and a full on-device GenAI pipeline (fine-tune → quantize → deploy → benchmark) are first-class goals, not side effects.

## CURRENT PHASE

**Phase 0 — Launcher shell.** Monorepo scaffolding (section 01) complete. CLI shell (T001–T005) and app launching (T011–T013) built and on-device verified on the S20 FE. T140 (the general T0 dispatcher/registry) replaced the one-off `:apps` parser pulled forward at T012, so `:settings`/`:note`/`:alias`/`:help` (T142–T145) can each register into it with one line. T007 landed Room and aliases — definition confirmed on-device, resolution wired in afterward and still needing a real run. Still open: T008–T010 (T1 fuzzy match + T3 fallback — the biggest remaining gap, since an unmatched submit still just writes a placeholder), T014 (training-data log), T015/T016 (BACK/HOME, scrollback cap), T146 (status line: time/date). Not yet installed as the daily driver (T017). Exit criterion: daily-drivable as the real home screen with T0/T1/T3 only, zero ML. Update this line as phases complete — see `docs/design.md` §9 for the full phase table.

## Docs

- [`docs/design.md`](docs/design.md) — architecture, decisions, and rationale. Read before making any design-affecting change.
- [`docs/tasks.md`](docs/tasks.md) — task list and current status. Check before starting work; update as tasks move.

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

Reference the task ID from `docs/tasks.md` (format `Txxx`), e.g. `T098 · Create monorepo skeleton`.
