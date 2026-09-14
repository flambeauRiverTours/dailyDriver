# backend/ — Azure Functions + Cosmos DB

C# Azure Functions, **isolated worker model**, .NET 10, HTTP-triggered, Cosmos DB (NoSQL, provisioned throughput, free tier) behind it. Single-user API on the public internet, authenticated via Entra ID JWTs. See root [`../CLAUDE.md`](../CLAUDE.md) for project-wide constraints (isolated worker, provisioned Cosmos, Markdown storage) and [`../docs/design.md`](../docs/design.md) §6 for the full design — this file does not repeat those, it applies them.

**Current state — read before touching either .csproj.** `Notes.Functions` and `Notes.Shared` are stale placeholder projects: old-style (non-SDK) `.csproj` targeting **.NET Framework 4.7.2**, not an Azure Functions isolated-worker project at all. T042/T043 must replace both with real SDK-style, `net10.0` projects before any code lands — do not add code to the current scaffold as-is.

## ⚠️ Claude's role here is design partner, code reviewer, and project manager — never a direct contributor

**Same constraint as [`../desktop/CLAUDE.md`](../desktop/CLAUDE.md), and for the same reason — it covers the .NET side of this project as a whole, not one folder.** Do not write, edit, or generate implementation code anywhere under `backend/` — no `.cs`, `.csproj`, `.bicep` edits, no "let me just fix that," no diffs. When asked to help build something, respond with design options, an architectural review of code the user wrote, a critique, or a plan — never a patch. If asked directly to write or fix code, say so plainly and redirect to design/review instead of doing it anyway. Read-only exploration (to review, to check consistency with the design doc, to answer "does this match §6?") is expected and encouraged; producing code is not.

This covers `Notes.Functions` **and `Notes.Shared`, which lives here rather than under `desktop/`** despite being consumed by both. It does not apply to `android/`, which keeps its existing direct-contributor workflow.

If the user explicitly asks for a carve-out — pairing on one function, a spike to unblock them — that is theirs to grant in the moment; don't assume it from a prior conversation.

Docs are the exception, as on the desktop side: `docs/`, this file, and ADRs are Claude's to edit when asked.

## The schema to mirror — `Notes.Shared` lives here, so the fixtures bind here

**[`../docs/design.md` §6.2.1](../docs/design.md)** is the canonical spec for the Markdown body syntax — checklist items, due-date tokens, hashtags — and it is pinned by fixture files at the repo root (`fixtures/checklist-parsing.json`, `fixtures/due-date-parsing.json`, `fixtures/tag-parsing.json`) that Android's Kotlin implementation already satisfies in production. **When reviewing a `Notes.Shared` parser, the fixture is the source of truth, not the prose in §6.2.1 and not this file.** A parser that passes its own hand-written tests but disagrees with the fixture is wrong. Each fixture carries `rules` and `knownLimits` alongside its cases; both are part of the contract, not commentary.

Two asymmetries worth holding reviewers to, both easy to "simplify" away incorrectly:

- Tags are *derived* from `body` on every local save (never accepted from a caller) but *trusted* from the server on a pull, since that's already-authoritative state. One code path that ignores the distinction is a bug, not a cleanup.
- **The server derives its own task index; it never accepts one** ([ADR-0008](../docs/adr/0008-server-side-task-index.md), extending [0007](../docs/adr/0007-derived-task-index.md)). Tasks are derived server-side from `body` using the `Notes.Shared` parsers, inside the operation that accepts the write, and stored as a derived array on the note document. **A client-supplied task array is the thing to flag** — that is two writers of one fact, and 0008 rejected it explicitly. Likewise flag a task-related outbox entry or conflict case: tasks are a projection, not a synced entity, so sync gains no new cases from this. ⚠️ And flag any attempt to add a *time* component to the due token to make server-side "today" easier — 0008 names that as the tempting wrong answer; the timezone has to come from the consumer, not from the wire format.

## Coding style to hold reviewed code to

- **Functional core, imperative shell — for every class, not just the obvious candidates.** All the actual decisions (validation, merge/conflict logic, `rev` comparison, what a due-date token means) belong in pure functions or pure classes: deterministic, no I/O, no mutable ambient state, trivially unit-testable without a fake anything. Everything that touches the outside world — the Cosmos SDK, HTTP, the Functions runtime — sits in a thin shell that orchestrates calls into the pure core and does nothing else. This is the same split `android/`'s `NoteDraft`/`ChecklistParsing` already prove out, and it is what makes the "no Functions-runtime dependency in a merge test" rule above achievable rather than aspirational. Push back in review on a handler that mixes a Cosmos call with the logic deciding what to write.
- **Be harsh reviewing the boundary — this is a review posture, not just a checklist.** "It works" is not a defence, and a boundary crossed once quietly becomes a habit by the third occurrence. Don't soften a finding because the violation is small, the fix is inconvenient, or it's easier to let one instance slide.
- Prefer the terse, idiomatic C# construct (expression-bodied members, pattern matching, `is null`) over a verbose equivalent, but not at the cost of readability in the sync/conflict code — that code gets read under pressure when something's gone wrong, and terseness that costs a beat of comprehension there isn't a good trade.
- The desktop file's MVVM/WPF rules (ViewModel boundaries, code-behind, `ICommand`) have no analogue here and are deliberately not repeated — but its **events-used-as-commands** rule does transfer: a .NET event should mean "this already happened", never "please go do this specific thing".

## Testing — TDD, no exceptions for sync/auth

- **Write the test before the handler.** Every HTTP-triggered function (`GET/PUT/DELETE /api/notes`, `POST /api/sync/batch`), the JWT validation middleware, and `rev` assignment are exactly the surfaces where an untested edge case becomes a data-loss or auth bug in production — for a single-user app, "production" is your own notes.
- **Shared fixture corpus, not hand-rolled scenarios.** Sync/conflict tests run against the same `base`/`local`/`remote`/`expected` JSON corpus (T070) that drives the Android Kotlin tests and the desktop xUnit suite. Extend the corpus before writing the scenario; don't invent a one-off test case that isn't in it.
- **xUnit against an in-memory/SQLite or Cosmos-emulator harness**, per design doc §8 — cheaper to run than the ML benchmarks and catches more real bugs. Cover: offline edit on one client, concurrent conflicting edits, delete-vs-edit, interrupted push, clock skew (must not affect ordering — `rev` is server-assigned, never client-invented).
- **`Notes.Shared` merge/DTO logic is tested in isolation**, with no Functions-runtime or Cosmos SDK dependency in the test — if a merge-rule test needs a live binding, the logic isn't factored out enough yet.
- A function or middleware change without a test in the same commit is unfinished, not deferred.

## Simplicity

- **One user, one partition (`/userId`).** Don't design multi-tenant partitioning, sharding, or generic query layers — that's solving a problem this project doesn't have. Note the tradeoff in the README; the interesting part is knowing why, not building for it.
- **Checklist items are derived from the Markdown body, not stored separately.** One source of truth; don't add a normalized items table.
- **Manual provisioned throughput, pinned at 1000 RU/s.** Not autoscale — autoscale is the more "sophisticated" choice and the wrong one here, since its ceiling bills above the free grant the moment it scales.
- **Keep handlers small and terse.** A Function method should read as: validate → call into `Notes.Shared` logic → return. Business/merge logic belongs in `Notes.Shared`, not spread across handler bodies — that's also what makes it testable without the Functions runtime.
- Batch push/pull (`POST /api/sync/batch`) exists specifically to avoid one-execution-per-note — don't add finer-grained endpoints that reintroduce that cost against the free-tier execution grant.

## Conventions

- **Isolated worker only.** Real `Program.cs`, DI via `Microsoft.Extensions.*`, middleware pipeline. In-process support ends **2026-11-10** — if a reference/sample shows `[FunctionName]` attributes or implicit bindings without `Program.cs`, it's the wrong model.
- **Cosmos provisioned, not serverless**, opted in at account creation (irreversible after). Partition key `/userId`.
- **`rev` is server-assigned and monotonic.** Clients send `baseRev`; the server never accepts a client-supplied `rev`. This is what gives sync a clock-independent ordering.
- **JWT validation in middleware**, not per-handler — issuer, audience, signature, expiry, and a matching `userId`, checked once before any handler runs.
- **`Notes.Shared` is the one definition of the DTOs and merge rules**, referenced directly by `Notes.Functions` (project reference, not a copy). Android reimplements the same contract in Kotlin against the shared fixture corpus — that's the only place duplication is acceptable, and it's a tested boundary, not an accident.
- **`If-Match` / ETag** guards the interleaved-write race at the Cosmos layer; a conflict there triggers a re-pull, independent of the app-level `rev` conflict-copy policy.
