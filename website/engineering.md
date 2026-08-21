# Engineering practices

How the project is actually run. This section is in use today, unlike the desktop, backend and ML
workstreams.

---

## Cross-language test fixtures

Kotlin and C# both have to parse the same note bodies — hashtags, checklist markers — and must
agree exactly. Divergence wouldn't throw an error; it would quietly reorder someone's grocery list
between their phone and their desktop.

**Neither language owns the rules.** They live in JSON fixture corpora that both test suites
execute:

```
fixtures/tag-parsing.json
fixtures/checklist-parsing.json
```

Each file carries the rules in prose, the known limits, and the cases — parse, render, conversion,
normalisation. Both implementations run *these* examples rather than maintaining their own, so a
divergence fails a test instead of reaching a user.

The corpora also record what the format deliberately *doesn't* do — nested checklists flatten,
checked state is lost converting a checklist to prose — so a future implementer can tell a design
limit from a bug.

---

## Testing

| Suite | Count | Scope |
|---|---|---|
| Android unit (JVM) | 508 | Parsing, matching, view models, formatting |
| Android instrumented | 32 | Room, migrations, schema export |

**Device verification is part of "done."** Three bugs in the checklist editor passed 500-plus unit
tests, a clean build and lint, and were only found by running the app on hardware — a drag gesture
losing silently to a text field's own long-press handler, a layout pushing the action row
off-screen, and a focus request that failed *without throwing*, so a new row appeared but couldn't
be typed into. Each is written up with its root cause in the task log.

Room's exported schema is diffed against the compiled entities by a test, so an entity change
without a migration fails the build rather than corrupting a database later.

---

## Source control

Trunk-based. `main` is the only long-lived branch.

- **Protected `main`** — pull request required, force-push and deletion blocked, and the
  restriction applies to administrators too. No direct pushes, including by the repository owner.
- **Conventional commits carrying the task ID** — `feat(android): T003 build app index from
  PackageManager`
- **Merged by rebase or merge commit, never squash** — squashing collapses every commit's task
  reference into one, which defeats the traceability the convention exists for.

Commit messages record *why*, not just what: the alternative that was rejected, the constraint
that forced the shape, or the bug the change fixes and how it was found.

---

## Documentation

Three layers, each with a different job:

**Architecture decision records** — five so far, covering the calls that would be expensive to
reverse: WPF over Avalonia, MediaPipe over llama.cpp, Markdown over rich-text JSON, conflict
copies over last-write-wins, one repository over several. Each records what is *given up*, not
only what is gained. They're treated as permanent: a changed decision gets a new ADR superseding
the old one rather than an edit that pretends it always said that.

**The design document** — architecture, data model, sync semantics, evaluation plan. Read before
any design-affecting change.

**The task log** — every task with its status and, for finished work, what was decided while
building it and what went wrong. Closer to an engineering log than a checklist; it's where the
device-only bugs above are written up.

---

## CI

GitHub Actions. The documentation site builds in strict mode on pull requests — a broken internal
link or a page missing from the navigation fails the check rather than shipping a 404 — and
deploys only from `main`, in a separate job, so a fork's pull request can build but never publish.

Broader build and test validation is planned but not yet wired up; the Android suites currently
run locally.

---

## Infrastructure

Azure resources are defined in Bicep so the environment is reproducible from source rather than
assembled by clicking through a portal. The budget is a fixed $0/month, which is a design
constraint rather than an aspiration — it's what forces the Cosmos DB capacity-mode decision
described on the [backend page](backend.md#two-constraints-that-are-easy-to-get-wrong).
