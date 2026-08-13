# backend/ — Azure Functions + Cosmos DB

C# Azure Functions, **isolated worker model**, .NET 10, HTTP-triggered, Cosmos DB (NoSQL, provisioned throughput, free tier) behind it. Single-user API on the public internet, authenticated via Entra ID JWTs. See root [`../CLAUDE.md`](../CLAUDE.md) for project-wide constraints (isolated worker, provisioned Cosmos, Markdown storage) and [`../docs/design.md`](../docs/design.md) §6 for the full design — this file does not repeat those, it applies them.

**Current state — read before touching either .csproj.** `Notes.Functions` and `Notes.Shared` are stale placeholder projects: old-style (non-SDK) `.csproj` targeting **.NET Framework 4.7.2**, not an Azure Functions isolated-worker project at all. T042/T043 must replace both with real SDK-style, `net10.0` projects before any code lands — do not add code to the current scaffold as-is.

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
