# dailyDriver

A keyboard-first, CLI-style Android launcher with an on-device SLM for natural-language commands, paired with a native WPF desktop notes client that works fully offline. The two are designed to share notes through a C# Azure Functions backend; that sync layer is designed and not yet built.

This is a personal side project that doubles as a portfolio piece for a .NET job search — see [`docs/design.md`](docs/design.md) for the full design and [`docs/adr/`](docs/adr/) for the reasoning behind the big calls (WPF over Avalonia, MediaPipe over llama.cpp, Markdown storage, conflict-copy sync, monorepo layout).

📖 **[Browse the docs site](https://flambeauRiverTours.github.io/dailyDriver/)** — the same files, rendered and searchable.

**Status:** work in progress — see [`docs/tasks.md`](docs/tasks.md) for the current phase and task list, and the [status table on the docs site](https://flambeauRiverTours.github.io/dailyDriver/) for a per-area summary.

- **Android launcher — working, and in daily use as the real home screen.** CLI shell, app launching, fuzzy matching, aliases, notifications, and a full notes/checklist/task system on Room. The T0–T3 command pipeline is live end to end, including the on-device model: a Gemma-3 270M loads and answers correctly on the target phone through the same code path the launcher ships.
- **Desktop client (.NET/WPF) — in progress.** Boots on the .NET Generic Host with DI, stores notes in EF Core/SQLite behind real `dotnet ef` migrations, and has a working Markdown editor with live preview and debounced autosave. No sync or sign-in yet.
- **Azure backend — designed, partly built.** `Notes.Shared`, the C# contract shared by the desktop client and the backend, is real and covered by tests. The Functions app and the Bicep infrastructure are designed but not written.
- **ML pipeline — in progress.** The model is chosen on measurement rather than assumption, and a month of real usage has been logged on-device to seed the fine-tuning set. No training run yet.

## Stack

- **Android:** Kotlin, Jetpack Compose, Room, MediaPipe LLM Inference (on-device SLM)
- **Desktop:** .NET 10, WPF, MVVM (CommunityToolkit.Mvvm), EF Core + SQLite
- **Backend:** C# Azure Functions (isolated worker), Cosmos DB (NoSQL, provisioned throughput, free tier)
- **Shared:** `Notes.Shared` — one C# contract (DTOs, tag/checklist/due-date parsing) referenced by both desktop and backend. Merge rules land with the sync-scenario corpus that gives them an oracle.

## Repo layout

```
android/              CLI launcher + on-device SLM (Kotlin)
desktop/              WPF notes client (Notes.Desktop)
backend/              Notes.Shared contract + the Functions app (not yet written)
DailyDriverDotNet/    solution file spanning desktop/ and backend/
infra/                Bicep IaC (config only so far)
fixtures/             cross-language test corpus, executed by both suites
docs/                 design doc, task list, ADRs
website/              the docs site published to GitHub Pages
scripts/              release and maintenance helpers
```

**`fixtures/` is worth a look.** Tag parsing, checklist parsing and due-date rules are a contract between a Kotlin implementation and a C# one, so the rules live in JSON at the repo root and the Android (JUnit) and .NET (xUnit) suites both execute the same corpus. If the two implementations disagree, that is a failing test rather than a judgment call.

Each of `android/`, `desktop/`, and `backend/` has its own `CLAUDE.md` with module-specific conventions.

## License

MIT — see [`LICENSE`](LICENSE).
