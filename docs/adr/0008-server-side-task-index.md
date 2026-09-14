# 0008. A server-side derived task index

Status: Accepted (extends [0007](0007-derived-task-index.md), 2026-08-25)

## Context

[ADR-0007](0007-derived-task-index.md) gave each client a derived, queryable `tasks` index over its own note bodies, and closed with the one thing it deliberately did not decide:

> **Still given up:** server-side reasoning about tasks. The index is client-local and derived, so the backend still cannot answer "what is due today" without parsing bodies. A server-side digest or push notification would still need a new decision, and that decision is now narrower and easier: the client-side shape is settled, and only the server's view of it would be open.

This is that decision. It is not a reversal — 0007's client-side rules stand unchanged.

**What forces it now is T042.** `Notes.Shared` is the one definition of the DTOs, referenced by both `Notes.Desktop` and `Notes.Functions`. Deciding the server's view *after* that contract ships means changing a contract two clients already implement, against fixtures a third (Kotlin) implementation already satisfies. The cheap moment to decide is before the first line of it exists.

**A correction worth recording, because it changed what was actually on the table.** The rethink began from a belief that tasks did not reach the desktop. They do. The `@YYYY-MM-DD` token lives inside `body`, and `body` syncs — so every client already derives the same tasks from the same bytes. Nothing about cross-device task visibility was ever missing. What is missing is narrower and only ever server-side: the backend cannot answer "what is due today" without parsing every note body, which rules out a digest, a push notification, or any scheduled job that reasons about dates.

**There is also an ambiguity in 0007 that has to be resolved either way.** Its third rule reads "It never syncs. No outbox row, no document-model entry, **no C# contract**." Read literally, the last clause forbids `Notes.Shared` from containing due-date logic at all — which directly contradicts `fixtures/due-date-parsing.json`, whose own description states that *"Notes.Shared implements the same rules in C# (T042)"*. The intent was plainly "no DTO in the sync contract", not "no parser in the shared library", but it does not say that, and T042 is the task that trips over the difference.

## Decision

**The server maintains its own derived task index, derived server-side from `body`, using the same `Notes.Shared` parsers the clients use.**

Three rules, deliberately mirroring 0007's so the shape is recognisably the same one:

1. **It is only ever written by deriving it from the note body**, inside the same operation that accepts the write. The server never accepts a task array from a client, exactly as `NoteDao.upsertLocal` never accepts tags from a caller.
2. **It is rebuildable from scratch** from the stored bodies. If it is ever wrong, rebuilding is a complete repair.
3. **It is not a synced entity.** No outbox row, no conflict cases, no client ever pushes or pulls a task. It is a projection the server keeps for its own queries.

**Representation:** a derived `tasks` array on the existing note document, not separate task documents. A task has no identity independent of the note line that authors it, and the note is already the unit of transfer, partitioning and conflict resolution.

**And the ambiguity is resolved explicitly: `Notes.Shared` owns the fixture-pinned checklist and due-date parsers.** That is not an exception to 0007 — it is what 0007 meant. Those parsers are the contract; what 0007 forbade, and this ADR continues to forbid, is a *client-authored* task or due-date field crossing the wire.

Editing a task still means editing the checklist item in the body. The token format from ADR-0006 and `fixtures/due-date-parsing.json` is unchanged, and remains the only place a task is authored.

## Consequences

**Sync does not change at all, and that is the main thing this buys.** Because both the clients and the server derive from the one artefact that already syncs, there is no new entity, no new outbox path, no new conflict case, and no new failure mode in the riskiest part of the project (§7). A design where clients pushed their derived tasks would have touched every one of those.

**Two derived indexes now exist over one source of truth** — the client's local `tasks` table and the server's array. That is one more place that can be stale, but not one more place that can *disagree authoritatively*: the body wins by definition on both sides, and either index is rebuildable.

**Timezone becomes a real, new problem, and it is the largest cost here.** §6.2.1 keeps time out of the wire format on purpose — a due date is date-only so that two devices in different zones cannot disagree about which day a task falls on. That works precisely because "today" is always evaluated locally, by a client that knows where it is. A server answering "what is due today" has no such knowledge. Any consumer of this index — digest, notification, scheduled job — must therefore carry the user's timezone from somewhere, and **this ADR does not decide where.** It is left to the first feature that needs it, with the constraint recorded here so it is not discovered late: do not add a time component to the stored token to solve it, because that reopens a settled decision on both clients and in three fixture files.

**Every accepted write costs a server-side re-derive.** Bounded by one note, the same trade 0007 already accepted on the client, and the same shape as the tag derivation the server will do anyway.

**Cosmos cost is real but bounded, and the $0 budget is a hard constraint.** The document grows by its own task array; Cosmos indexes all paths by default, so a targeted indexing policy over the due path is worth setting deliberately rather than inheriting, since indexing cost lands on writes and the throughput is manually pinned at 1000 RU/s (T050) with no autoscale headroom to absorb a mistake.

**The server becomes a third consumer of the fixture-pinned parsers.** This is the good version of that — one implementation in `Notes.Shared` used by both .NET sides, with Kotlin as the only reimplementation — but it raises the stakes on T042. A parser bug now shows up in scheduled server output as well as in two UIs.

**T042's shape changes.** The shared contract now needs the task type and the derivation function, not just the parsers. That is why this was decided before T042 rather than after.

## Alternatives considered

- **Leave it client-local (status quo, 0007 unchanged).** Cheap, correct, and loses nothing that users can currently see — the only cost is that server-side features stay impossible. Rejected because the decision is far more expensive to make after `Notes.Shared` ships than before, and a digest/notification is a wanted feature rather than a hypothetical one.
- **Clients push their derived tasks to the server.** Rejected outright: it makes the client the author of a fact the server also holds the source for, so the server would store a task array alongside a body that could contradict it. That is precisely the two-writers-of-one-fact problem 0006 and 0007 exist to prevent, and it would additionally add outbox entries and conflict cases to the sync engine.
- **Separate task documents in Cosmos.** Rejected. It gives a task an identity independent of the line that authors it, raises partition-key questions for an entity that has no natural one beyond its note, multiplies RU cost per note write, and reopens "a task exists in two places" from the other direction.
- **A single `dueDate` field on the note document.** Rejected as simply not fitting the data: one note holds many checklist items with different dates, so a per-note field cannot represent them without picking an arbitrary winner.
- **Add a time component to the due token so the server can reason in UTC.** Rejected. It solves the timezone problem by breaking the reason the format is date-only, and would invalidate `fixtures/due-date-parsing.json` plus both existing implementations to serve one server-side consumer.
