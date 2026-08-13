# desktop/ — WPF Notes Client

Native Windows app (.NET 10 + WPF, MVVM). Offline-first: SQLite via EF Core is the source of truth, a background `SyncService` reconciles with the backend, and the UI never awaits the network. This is the .NET job-hunt artifact — see root [`../CLAUDE.md`](../CLAUDE.md) for project-wide constraints and [`../docs/design.md`](../docs/design.md) §5 for the full design.

**Solution spans folders.** `Notes.Desktop` lives here; `Notes.Shared` (the contract — POCOs, DTOs, merge rules) and `Notes.Functions` live in `../backend/`. `Notes.Desktop` references `Notes.Shared` directly — never redefine a DTO or merge rule locally to avoid the cross-folder reference.

Current state: default WPF template only (`App.xaml`, `MainWindow.xaml`, no `PackageReference`s). No MVVM toolkit, EF Core, MSAL, Markdig, or test project yet. Everything below is the spec for T052 onward.

## Testing — TDD, and the sync engine is non-negotiable

- **Test first, always, for the sync engine.** Outbox writes, `rev`/`baseRev` conflict detection, and conflict-copy materialisation are exactly the kind of logic that silently corrupts a user's notes if it's wrong — write the failing xUnit test before the code, not after.
- **Shared fixture corpus is the spec.** Sync scenarios (`base`/`local`/`remote`/`expected` JSON, T070) drive both this suite and the Android Kotlin tests. Write or extend the corpus first; a scenario with no corpus entry doesn't count as covered, and disagreement between the two suites is a failing test, not a judgment call.
- **xUnit + FluentAssertions**, per the design doc. Keep merge/conflict logic in `Notes.Shared` free of WPF and EF Core types so it's testable without a UI or a database.
- ViewModel logic (via `CommunityToolkit.Mvvm`) should be tested through the ViewModel's public properties/commands, not through the view — no UI automation for anything that isn't a genuine rendering concern.
- A PR touching `SyncService`, the outbox, or conflict resolution without a corresponding test is unfinished.

## Simplicity

- **This is a single-user, single-tenant app.** Don't add abstractions (repository-over-repository, generic CRUD frameworks, plugin systems) that only pay off with multiple consumers or implementations. EF Core's `DbContext` is the repository.
- **Three projects, not more.** `Notes.Shared` / `Notes.Desktop` / `Notes.Functions` is the whole structure (design doc §5.2). Don't split further for "separation of concerns" — at this size that's overhead, not architecture.
- **No premature sync generality.** The conflict policy is last-write-wins-on-`rev` with a conflict copy — deliberately not a CRDT (design doc §7.4). Don't build toward one "in case requirements grow."
- Prefer the terse, idiomatic C# construct (expression-bodied members, pattern matching, `is null`) over a verbose equivalent, but not at the cost of readability in the sync/conflict code — that code gets read under pressure when something's gone wrong.

## Conventions

- **MVVM via `CommunityToolkit.Mvvm`.** Source-generated `[ObservableProperty]`/`[RelayCommand]` — no hand-rolled `INotifyPropertyChanged`.
- **The UI never awaits the network.** All reads/writes go through EF Core; `SyncService` (an `IHostedService`) reconciles in the background on a timer, on reconnect, and on demand. A ViewModel calling an `HttpClient` directly is a bug.
- **Outbox in the same transaction as the entity change.** This is what makes an interrupted sync resumable — don't write to the outbox as a separate step that can fail independently.
- **`HttpClient` via `IHttpClientFactory` + Polly** for retry/backoff — the network is assumed flaky, not exceptional.
- **Auth is MSAL.NET, public client, PKCE, no client secret.** A desktop binary can't keep a secret; don't add one.
- **Markdig** for Markdown rendering/round-trip — matches the storage format from the root constraint. Checklist checkboxes rewrite the underlying `- [ ]` / `- [x]` line; there is no separate checklist-item table.
- **Light + dark, bound to system preference.** This is the one client with both themes (design doc §5.3) — deliberate asymmetry with Android's dark-only.
