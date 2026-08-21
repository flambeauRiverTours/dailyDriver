# 0006. Tasks are dated checklist items, not a separate entity

Status: **Superseded by [0007](0007-derived-task-index.md)** (2026-08-21)

> Superseded in part, and quickly. 0007 keeps this ADR's core decision — a task is a dated
> checklist item, authored in the note body, one source of truth — and reverses the "no table"
> half by adding a **derived** index for querying. The reasoning below about why a task must not
> be independently editable still holds and is why 0007's table is an index rather than a store.
> What this ADR got wrong is under "Consequences" in 0007: it treated the loss of queryability as
> a throughput question, when the real problem is that the feature's primary query, "what is due
> today", had no efficient expression at all.


## Context

`:task` and `:tasks` add due-dated tasks to the launcher (§4.8). A task has to be creatable directly from the prompt, and it also has to be possible to give a due date to a checklist item that already exists inside a note. Both routes have to produce the same thing, or `:tasks` will show one kind and miss the other.

That leaves the question of what a task *is* in storage.

§6.2 already fixes one rule that bears on this: checklist items are derived from the Markdown body and never stored separately, so there is exactly one place an item exists. §4.6 puts checklists in the body as `- [ ]` lines for the same reason, and ADR-0004 chose Markdown over a rich-text model so that two clients in two languages could not disagree silently.

Three options were considered.

**A separate `tasks` table** with a due-date column, indexed and fast to query. This is what a task feature normally looks like.

**A new `NoteType.TASK`**, where a task is a note whose type says so, with the due date as a real field on the note.

**A due date on a checklist item**, written into the body next to the item text.

## Decision

A task is a checklist item carrying an `@YYYY-MM-DD` token in the note body. There is no tasks table, no new `NoteType`, and no change to the §6.2 document model.

`:task buy milk @fri` appends one dated line to a note titled `tasks`, opening or creating it by name the same way `:note <name>` already does. `:tasks` finds tasks by parsing note bodies and collecting the items that carry a date.

## Consequences

**A due date is not queryable.** Finding tasks means parsing every non-deleted note body, which is linear in the number of notes. At personal scale this is a few hundred short strings and does not matter. If it ever does, an index over dated items has to be rebuildable from the bodies and treated as disposable, because it would be a cache of something the body already states.

**Sorting and grouping happen in memory**, not in SQL. `:tasks` cannot push "order by due date" down to Room, and neither will the desktop client push it down to EF Core.

**No date validation at the storage layer.** A malformed date is just text in a body, and both clients have to agree on that. `fixtures/due-date-parsing.json` is what makes them agree, in the same pattern §7.3 sets out for tags.

**The two creation routes converge for free**, which was the point. Typing `:task` and adding a date to an existing checklist item produce byte-identical lines, so there is no possibility of two classes of task that behave differently.

**Sync gets this for nothing.** A due date is body text, so it moves through the existing outbox, revision ordering, and conflict-copy machinery with no new cases. The separate-table option would have needed its own sync path, its own conflict semantics, and its own entry in the shared C# contract.

**What is given up:** the things a real column would have made cheap. Querying tasks by date range, indexing them, aggregating them, or letting the server reason about them at all. A server-side "what is due today" endpoint is now impossible without parsing bodies on the backend, which is not currently something the backend does. If tasks ever need to drive notifications or a server-side digest, that is the constraint that will hurt, and it would justify revisiting this in a new ADR rather than quietly adding a table.

**Also given up:** anything a task might want that an item in a list cannot carry. Recurrence, a start date distinct from a due date, priorities, and time of day all become new tokens in the body, each needing agreement between both clients. The design deliberately starts with one token and date-only values.
