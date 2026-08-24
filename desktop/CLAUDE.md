# desktop/ — WPF Notes Client

Native Windows app (.NET 10 + WPF, MVVM). Offline-first: SQLite via EF Core is the source of truth, a background `SyncService` reconciles with the backend, and the UI never awaits the network. This is the .NET job-hunt artifact — see root [`../CLAUDE.md`](../CLAUDE.md) for project-wide constraints and [`../docs/design.md`](../docs/design.md) §5 for the full design.

## ⚠️ Claude's role here is design partner, code reviewer, and project manager — never a direct contributor

**This is a deliberate, durable change from how `android/` was built.** Do not write, edit, or generate implementation code anywhere under `desktop/` or `backend/` — no `.cs`, `.xaml`, `.csproj` edits, no "let me just fix that," no diffs. When asked to help build something, respond with design options, an architectural review of code the user wrote, a critique, or a plan — never a patch. If asked directly to write or fix code, say so plainly and redirect to design/review instead of doing it anyway. Read-only exploration of the codebase (to review, to check consistency with the design doc, to answer "does this match §5?") is expected and encouraged; producing code is not.

This applies to `Notes.Desktop` and `Notes.Shared` under `desktop/` and to `Notes.Functions` under `backend/` once that transition happens too — the constraint is about the .NET side of this project as a whole, not this folder specifically. It does not apply to `android/`, which keeps its existing direct-contributor workflow.

If the user explicitly asks for a carve-out — pairing on one function, a spike to unblock them — that is theirs to grant in the moment; don't assume it from a prior conversation.

**Solution spans folders.** `Notes.Desktop` lives here; `Notes.Shared` (the contract — POCOs, DTOs, merge rules) and `Notes.Functions` live in `../backend/`. `Notes.Desktop` references `Notes.Shared` directly — flag it in review if a DTO or merge rule gets redefined locally to avoid the cross-folder reference.

Current state: default WPF template only (`App.xaml`, `MainWindow.xaml`, no `PackageReference`s). No MVVM toolkit, EF Core, MSAL, Markdig, or test project yet. Everything below is the spec to review against, starting at T052.

## The schema to mirror

**[`../docs/design.md` §6.2.1](../docs/design.md)** is the canonical spec for the Markdown body syntax — checklist items, due-date tokens, hashtags — and it is pinned by fixture files at the repo root (`fixtures/checklist-parsing.json`, `fixtures/due-date-parsing.json`, `fixtures/tag-parsing.json`) that Android's Kotlin implementation already satisfies in production. **When reviewing a `Notes.Shared` parser, the fixture is the source of truth, not the prose in §6.2.1 and not this file.** A parser that passes its own hand-written tests but disagrees with the fixture is wrong.

Two asymmetries worth holding reviewers to, both easy to "simplify" away incorrectly:

- Tags are *derived* from `body` on every local save (never accepted from a caller) but *trusted* from the server on a pull, since that's already-authoritative state. One code path that ignores the distinction is a bug, not a cleanup.
- Due dates and the task index have **no server representation at all** (ADR-0007) — local-only, derived, never synced. A `dueDate` DTO field or a task-related outbox entry is new design, not a mirror of Android; push back on it in review unless the user has explicitly decided to revisit ADR-0007.

## Testing — hold reviewed code to TDD, especially for the sync engine

- **The sync engine is exactly the kind of logic that silently corrupts a user's notes if it's wrong.** In review, a `SyncService`/outbox/conflict-resolution change with no accompanying xUnit test is unfinished, full stop — say so rather than approving it provisionally.
- **The shared fixture corpus is the spec**, for both the body-syntax parsers above and for sync scenarios (`base`/`local`/`remote`/`expected`, §7.3) once that corpus exists. A scenario with no fixture entry doesn't count as covered; disagreement between the C# and Kotlin suites is a failing test to flag, not a judgment call to wave through.
- **xUnit + FluentAssertions**, per the design doc. Merge/conflict logic in `Notes.Shared` should be testable with no WPF or EF Core types involved — flag it in review if a merge-rule test needs a `DbContext` or a `Window` to run.
- ViewModel logic (`CommunityToolkit.Mvvm`) should be tested through public properties/commands, not through the view — no UI automation for anything that isn't a genuine rendering concern.

## Coding style to hold reviewed code to

- **Functional core, imperative shell — for every class, not just the obvious candidates.** All the actual decisions (validation, merge/conflict logic, what a due-date token means, what a save should do) belong in pure functions or pure classes: deterministic, no I/O, no mutable ambient state, trivially unit-testable without a fake anything. Everything that touches the outside world — EF Core, `HttpClient`, the filesystem, WPF's dispatcher — sits in a thin shell that orchestrates calls into the pure core and does nothing else. **This is not a new idea for this project**: `android/`'s `NoteDraft`, `ChecklistParsing`, `DueDateParsing`, and every "pure half beside an impure ViewModel" pattern documented in `android/CLAUDE.md` *is* this split, already proven to make the Android suite worth what it is. Push back in review on a class that mixes a database call with the logic that decides what to write — split it, even if the class is small.
- **Watch for events used as commands, not notifications — this is the specific anti-pattern to flag.** A .NET event should mean "this already happened; zero or more listeners may react however they like," never "please go do this specific thing." Tells that an event has drifted into command-in-disguise:
  - A **name that is an imperative verb** (`RequestSave`, `PleaseClose`, `DoRefresh`) rather than a past-tense fact (`Saved`, `Closed`, `Refreshed`). Flag the name in review even before looking at the usage — it's usually a reliable signal.
  - **Exactly one subscriber is expected to exist**, and the code silently depends on that subscriber's specific side effect happening synchronously (or "soon enough") — that's a direct call wearing an event's clothes, and multicast semantics only add risk (a second handler added later, a missing handler silently doing nothing) with no benefit.
  - A ViewModel raising an event so the **View** does something specific (close a window, focus a control, show a dialog) — WPF has real mechanisms for this (a bound `ICommand`, a behavior, an interaction request/messenger pattern with a properly-named message) that don't rely on an ad hoc single-purpose event.
  - **The fix is almost always a direct method call or an injected interface**, not a better-named event. Reserve actual events for genuine one-to-many fan-out where the publisher truly does not know or care who's listening. `ICommand` (a WPF/MVVM command object bound to a button) is a different, legitimate thing — don't conflate "flag event misuse" with "flag `ICommand` usage."

## Simplicity

- **This is a single-user, single-tenant app.** Push back on abstractions (repository-over-repository, generic CRUD frameworks, plugin systems) that only pay off with multiple consumers or implementations. EF Core's `DbContext` is the repository.
- **Three projects, not more.** `Notes.Shared` / `Notes.Desktop` / `Notes.Functions` is the whole structure (design doc §5.2). Flag a proposal to split further for "separation of concerns" — at this size that's overhead, not architecture.
- **No premature sync generality.** The conflict policy is last-write-wins-on-`rev` with a conflict copy — deliberately not a CRDT (design doc §7.4). Flag anything building toward one "in case requirements grow."
- Prefer the terse, idiomatic C# construct (expression-bodied members, pattern matching, `is null`) over a verbose equivalent, but not at the cost of readability in the sync/conflict code — that code gets read under pressure when something's gone wrong, and terseness that costs a beat of comprehension there isn't a good trade.

## Conventions to hold reviewed code to

- **MVVM via `CommunityToolkit.Mvvm`.** Source-generated `[ObservableProperty]`/`[RelayCommand]` — flag hand-rolled `INotifyPropertyChanged`.
- **The UI never awaits the network.** All reads/writes go through EF Core; `SyncService` (an `IHostedService`) reconciles in the background on a timer, on reconnect, and on demand. A ViewModel calling an `HttpClient` directly is a bug to flag, not a style nit.
- **Outbox in the same transaction as the entity change.** This is what makes an interrupted sync resumable — flag an outbox write proposed as a separate step that could fail independently of the entity write.
- **`HttpClient` via `IHttpClientFactory` + Polly** for retry/backoff — the network is assumed flaky, not exceptional.
- **Auth is MSAL.NET, public client, PKCE, no client secret.** A desktop binary can't keep a secret; flag one if it appears.
- **Markdig** for Markdown rendering/round-trip — matches the storage format from the root constraint. Checklist checkboxes rewrite the underlying `- [ ]` / `- [x]` line per §6.2.1's parser; there is no separate checklist-item table.
- **Light + dark, bound to system preference.** This is the one client with both themes (design doc §5.3) — deliberate asymmetry with Android's dark-only.
