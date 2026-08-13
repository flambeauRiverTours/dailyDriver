# dailyDriver

Personal-use side project: an Android CLI-style home-screen launcher backed by an on-device SLM (MediaPipe, cascaded T0–T3 command pipeline), a native WPF (.NET 10) desktop notes client with full offline capability, and a C# Azure Functions backend on a fixed $0/month budget. Notes are shared across both clients via a local-first outbox sync with server-assigned `rev` ordering and conflict-copy resolution. The project doubles as a portfolio piece for a .NET job search — end-to-end .NET (WPF/MVVM, EF Core, isolated-worker Functions) and a full on-device GenAI pipeline (fine-tune → quantize → deploy → benchmark) are first-class goals, not side effects.

## CURRENT PHASE

**Phase 0 — Launcher shell.** Monorepo skeleton just created (`android/`, `desktop/`, `backend/`, `infra/`, `docs/`); no application code yet. Exit criterion: daily-drivable as the real home screen with T0/T1/T3 only, zero ML. Update this line as phases complete — see `docs/design.md` §9 for the full phase table.

## Docs

- [`docs/design.md`](docs/design.md) — architecture, decisions, and rationale. Read before making any design-affecting change.
- [`docs/tasks.md`](docs/tasks.md) — task list and current status. Check before starting work; update as tasks move.

## Hard constraints

These are easy to get wrong by defaulting to the more common/tutorial answer. Do not deviate without discussing it first.

- **Azure Functions: isolated worker model only.** Never write or copy in-process-model code. In-process support ends **2026-11-10** — most tutorials and Stack Overflow answers predate this and show the wrong model.
- **Cosmos DB: provisioned throughput, not serverless.** The free tier requires the provisioned-throughput capacity mode and is only selectable **at account creation**. Selecting serverless silently forfeits the free tier and cannot be fixed later without recreating the account. ("Serverless" applies to Functions compute, not to Cosmos.)
- **The SLM never sits on the launcher's fast path.** It is one tier (T2) of a cascade behind T0 (exact match) and T1 (fuzzy match); T3 is the non-ML fallback. The launcher must stay fast and fully usable even if the model is absent, loading, or wrong.
- **Notes are stored as Markdown, not rich-text JSON.** Checklists are `- [ ]` / `- [x]` lines parsed into structured items. This is the wire and storage format for both Android (Room) and desktop (EF Core/SQLite) — do not introduce a rich-text document model (Quill Delta, ProseMirror, etc.).

## Commits

Reference the task ID from `docs/tasks.md` (format `T0xx`), e.g. `T098 · Create monorepo skeleton`.
