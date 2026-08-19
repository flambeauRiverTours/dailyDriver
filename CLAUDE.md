# dailyDriver

Personal-use side project: an Android CLI-style home-screen launcher backed by an on-device SLM (MediaPipe, cascaded T0–T3 command pipeline), a native WPF (.NET 10) desktop notes client with full offline capability, and a C# Azure Functions backend on a fixed $0/month budget. Notes are shared across both clients via a local-first outbox sync with server-assigned `rev` ordering and conflict-copy resolution. The project doubles as a portfolio piece for a .NET job search — end-to-end .NET (WPF/MVVM, EF Core, isolated-worker Functions) and a full on-device GenAI pipeline (fine-tune → quantize → deploy → benchmark) are first-class goals, not side effects.

## CURRENT PHASE

**Phase 0 — Launcher shell.** Monorepo scaffolding (section 01) complete. CLI shell (T001–T005) and app launching (T011–T013) built and on-device verified on the S20 FE. T140 (the general T0 dispatcher/registry) replaced the one-off `:apps` parser pulled forward at T012, so `:settings`/`:note`/`:alias`/`:help` (T142–T145) can each register into it with one line. T007 landed Room and aliases. T008 landed the T1 fuzzy matcher — typing `chr` now previews and launches Chrome, so the launcher finally does its primary job rather than only its explicit commands; Enter/pause always commits to the best match, no ambiguity gate (revised 2026-08-19). T009 landed the T1→T2 escalation classification (whitespace → T2, else → T3), though neither tier has a real handler yet. T142 wired `:settings` into T140's dispatcher — Enter fires the system Settings app and returns to an empty prompt. T143 registered `:note` too, scope cut back to a placeholder ("will open notes in the future") until T020's editor overlay exists. T144 added `:alias`, listing currently-defined aliases. T147 added `:notif` — browsing/opening active notifications, the project's first special-access permission (`NotificationListenerService`, granted by hand, no runtime dialog); not yet verified on-device, which matters more here than for the other reserved commands since permission grant/revoke and real notification behavior can't be unit-tested. T145+T010 closed out the tier cascade: `:help`/`?` prints a registry-generated command listing, and unmatched input now gets a real T3 answer pointing at it instead of a placeholder. **T0, T1, and T3 are all live and every reserved command exists** — only T2 (the SLM, T036) is missing, which is exactly the Phase 0 exit shape. T146 added the status row (time left, date right, blinking colon, ticking only while resumed), T148 its unread-notification dot, and T149 made the app immersive — both system bars hidden, which is what removed the duplicate system clock sitting above our own. Still open: T014 (training-data log — worth landing **before** T017, since daily-driving without it throws away real usage that T075 needs), T016 (scrollback cap), T017 (install as daily driver — the Phase 0 exit gate). T015 stays blocked on T020. Not yet installed as the daily driver (T017). Exit criterion: daily-drivable as the real home screen with T0/T1/T3 only, zero ML. Update this line as phases complete — see `docs/design.md` §9 for the full phase table.

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
