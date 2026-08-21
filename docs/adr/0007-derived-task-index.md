# 0007. A derived, queryable task index over note bodies

Status: Accepted (supersedes [0006](0006-tasks-as-dated-checklist-items.md))

## Context

[ADR-0006](0006-tasks-as-dated-checklist-items.md) decided that a task is a checklist item carrying an `@YYYY-MM-DD` token in a note body, with no table behind it. `:tasks` was to find tasks by parsing every non-deleted note body on each invocation.

That ADR named the cost and accepted it: "a due date is not queryable", "sorting and grouping happen in memory", and finding tasks is "linear in the number of notes". It also named the escape hatch, in advance — if an index were ever needed, it "has to be rebuildable from the bodies and treated as disposable".

Reviewing the decision before building `:tasks` (2026-08-21), the cost lands harder than it read on paper. **"What is due today" is not an incidental query; it is the entire reason the feature exists.** A task list that cannot answer it without a full scan has put its most common operation on its slowest path. The same applies to every view worth having: overdue, this week, due on a given day. Each is a filter over a date, and a date is exactly what a database is good at filtering on when it is a column and hopeless at when it is a substring in a Markdown blob.

Scanning also spreads. `:tasks` is one caller today, but a due-today count in the status row, or a notification, or the desktop client's task view, each add another full parse of every note.

The question is therefore not whether to index, but **what the index is allowed to be**. ADR-0006's underlying rule, from §6.2, is that a checklist item exists in exactly one place. A `tasks` table that could be edited directly would break that and put the same task in a body line and a row, free to disagree.

## Decision

Add a **`tasks` table that is a derived index, not a store**. It carries the note id, the item's position, its text, its checked state, and an indexed `due` column, so `:tasks` becomes an ordinary indexed query.

Three rules make it an index rather than a second source of truth:

1. **It is only ever written by deriving it from a note body**, in the same transaction as the note write. Nothing can insert a task directly, the way `NoteDao.upsertLocal` already derives tags rather than accepting them.
2. **It is rebuildable from scratch at any time.** If it is ever wrong, dropping and rebuilding it from the bodies is a complete repair.
3. **It never syncs.** No outbox row, no document-model entry, no C# contract. Each client derives its own index from the bodies it already has.

Editing a task still means editing the checklist item in the body. The token format from ADR-0006 and `fixtures/due-date-parsing.json` is unchanged, and remains the only place a task is authored.

**This is the same shape as `note_tags`**, which already exists and already works: hashtags are parsed out of the body on save and indexed into a table so they can be queried exactly, while the body stays authoritative. Tasks are that pattern with a date instead of a tag. That precedent is most of the reason to be comfortable here.

## Consequences

**`:tasks` becomes a real query.** Due today, overdue, this week and due-on-a-date are all indexed lookups. A due-today count in the status row becomes cheap enough to consider, which it was not before.

**Every note write now costs a re-derive** of that note's tasks. This is the same cost already paid for tags, on the same transaction, and it is bounded by one note rather than by the whole database. The trade is a small fixed cost on write in exchange for removing an unbounded cost on read.

**The index can be stale or wrong, and that is a new failure mode ADR-0006 did not have.** A body that changes without the index updating means `:tasks` lies. Three things bound it: derivation happens inside the note's own transaction so it cannot be skipped; a rebuild is always available; and bodies win by definition in any disagreement. It should be tested the way the outbox was, with instrumented tests against real SQLite, because that is the layer where this class of bug is invisible to unit tests.

**The Room schema changes**, which means a migration, and it lands while `exportSchema` is off (T151). That guard should be working before this ships, so the migration is checked rather than assumed.

**What ADR-0006 got right is kept.** A task is still a dated checklist item, still authored in the body, still one source of truth. The two creation routes still converge on byte-identical lines. Sync still needs no new cases, because nothing new syncs.

**What it got wrong is the reasoning about cost.** ADR-0006 argued that a few hundred short strings do not matter at personal scale, which is true in isolation and beside the point: the objection was never throughput, it was that the feature's primary query had no efficient expression at all. "It is fast enough" is not an answer to "this cannot be indexed."

**Still given up:** server-side reasoning about tasks. The index is client-local and derived, so the backend still cannot answer "what is due today" without parsing bodies. A server-side digest or push notification would still need a new decision, and that decision is now narrower and easier: the client-side shape is settled, and only the server's view of it would be open.
