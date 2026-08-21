# Engineering practices

How the project is actually run. This section is in use today, unlike the desktop, backend and ML
workstreams.

---

## Cross-language test fixtures

Kotlin and C# both have to parse the same note bodies, meaning hashtags and checklist markers, and
they have to agree exactly. A difference would not throw an error. It would quietly reorder
someone's grocery list between their phone and their desktop.

Neither language owns the rules. They live in JSON fixture files that both test suites run:

```
fixtures/tag-parsing.json
fixtures/checklist-parsing.json
```

Each file carries the rules in prose, the known limits, and the cases for parsing, rendering,
conversion and normalisation. Both implementations run those examples, so a divergence fails a test
before it can reach a user.

The files also record what the format deliberately does not do. Nested checklists flatten, and
checked state is lost when converting a checklist to prose. A future implementer can tell a design
limit from a bug.

---

## Testing

| Suite | Count | Scope |
|---|---|---|
| Android unit (JVM) | 508 | Parsing, matching, view models, formatting |
| Android instrumented | 32 | Room, migrations, schema export |

Device verification counts as part of finishing a feature. Three bugs in the checklist editor
passed 500-plus unit tests, a clean build and lint, and only turned up when the app ran on
hardware: a drag gesture losing silently to a text field's own long-press handler, a layout pushing
the action row off screen, and a focus request that failed without throwing, so a new row appeared
but could not be typed into. Each one is written up with its root cause in the task log.

Room's exported schema is diffed against the compiled entities by a test, so an entity change with
no migration fails the build instead of corrupting a database later.

---

## Source control

Trunk-based. `main` is the only long-lived branch.

- **Protected `main`.** Pull request required, force-push and deletion blocked, and the restriction
  covers administrators too, so there are no direct pushes even from the repository owner.
- **Conventional commits carrying the task ID**, for example
  `feat(android): T003 build app index from PackageManager`.
- **Merged by rebase or merge commit, never squash.** Squashing collapses every commit's task
  reference into one, which defeats the traceability the convention exists for.

Commit messages record why, not just what: the alternative that was rejected, the constraint that
forced the shape, or the bug being fixed and how it was found.

---

## Documentation

Three layers, each with a different job.

**Architecture decision records.** Five so far, covering the calls that would be expensive to
reverse: WPF over Avalonia, MediaPipe over llama.cpp, Markdown over rich-text JSON, conflict copies
over last-write-wins, and one repository over several. Each one records what is given up, not only
what is gained. They are treated as permanent, so a changed decision gets a new ADR superseding the
old one instead of an edit that pretends it always said that.

**The design document.** Architecture, data model, sync semantics and evaluation plan. Read before
any design-affecting change.

**The task log.** Every task with its status and, for finished work, what was decided while
building it and what went wrong. It reads more like an engineering log than a checklist, and it is
where the device-only bugs above are written up.

---

## CI

GitHub Actions. The documentation site builds in strict mode on pull requests, so a broken internal
link or a page missing from the navigation fails the check instead of shipping a 404. Deployment
runs as a separate job gated on `main`, so a fork's pull request can build the site but never
publish it.

Broader build and test validation is planned but not wired up yet. The Android suites currently run
locally.

---

## Infrastructure

Azure resources are defined in Bicep, so the environment can be rebuilt from source instead of
assembled by clicking through a portal. The budget is a fixed $0/month, and that is a real design
constraint. It is what forces the Cosmos DB capacity-mode decision described on the
[backend page](backend.md#two-constraints-that-are-easy-to-get-wrong).
