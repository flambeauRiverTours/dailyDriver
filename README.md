# dailyDriver

A keyboard-first, CLI-style Android launcher with an on-device SLM for natural-language commands, paired with a native WPF desktop notes client that works fully offline, syncing through a C# Azure Functions backend.

This is a personal side project that doubles as a portfolio piece for a .NET job search — see [`docs/design.md`](docs/design.md) for the full design and [`docs/adr/`](docs/adr/) for the reasoning behind the big calls (WPF over Avalonia, MediaPipe over llama.cpp, Markdown storage, conflict-copy sync, monorepo layout).

**Status:** early — see [`docs/tasks.md`](docs/tasks.md) for current phase and task list. The Android launcher shell builds, installs, and runs on-device (dark CLI prompt, live-filtering `:apps` app browser/launcher); the natural-language command pipeline, notes, desktop client, and backend don't exist yet.

## Stack

- **Android:** Kotlin, Jetpack Compose, Room, MediaPipe LLM Inference (on-device SLM)
- **Desktop:** .NET 10, WPF, MVVM (CommunityToolkit.Mvvm), EF Core + SQLite
- **Backend:** C# Azure Functions (isolated worker), Cosmos DB (NoSQL, provisioned throughput, free tier)
- **Shared:** `Notes.Shared` — one C# contract (DTOs, merge rules) referenced by both desktop and backend

## Repo layout

```
android/    CLI launcher + on-device SLM
desktop/    WPF notes client
backend/    Azure Functions + Cosmos
infra/      Bicep IaC
docs/       design doc, task list, ADRs
```

Each of `android/`, `desktop/`, and `backend/` has its own `CLAUDE.md` with module-specific conventions.

<!-- TODO (T088): architecture diagram + benchmark table go here once section 12/13 produce them -->

## License

MIT — see [`LICENSE`](LICENSE).
