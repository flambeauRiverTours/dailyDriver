# dailyDriver

A keyboard-first, CLI-style **Android launcher** with an on-device SLM for natural-language
commands, paired with a native **WPF desktop notes client** that works fully offline, syncing
through a **C# Azure Functions** backend.

It's a personal side project that doubles as a portfolio piece for a .NET job search — so the
end-to-end .NET story and the full on-device GenAI pipeline (fine-tune → quantize → deploy →
benchmark) are first-class goals, not side effects.

!!! note "This site is the repo's `docs/` folder, rendered"

    Nothing here is a second copy of the documentation. [Design](design.md),
    [Tasks](tasks.md) and the [ADRs](adr/README.md) are the same files the project is
    built from, edited in the repo. Every page has an edit link pointing back at its source.

## Start here

<div class="grid cards" markdown>

-   **[Design](design.md)**

    The architecture and the reasoning behind it — the T0–T3 command cascade, the local-first
    sync model, the phase table. Read this before making any design-affecting change.

-   **[Tasks](tasks.md)**

    Every task, its status, and — for the finished ones — what was decided while building it and
    what went wrong. Closer to an engineering log than a checklist.

-   **[Decisions](adr/README.md)**

    Five ADRs covering the calls that would be expensive to reverse: WPF over Avalonia,
    MediaPipe over llama.cpp, Markdown over rich-text JSON, conflict copies over last-write-wins,
    and one repo over several.

-   **[High-level design (PDF)](cli-launcher-hld.pdf)**

    The original write-up, kept as a snapshot of where this started.

</div>

## Where it stands

**Phase 0 — the launcher shell — is nearly complete.** The CLI surface, app launching, aliases,
and the full reserved-command set (`:apps`, `:notes`, `:note`, `:settings`, `:notif`, `:help`) all
run on-device. The T0–T3 cascade is live end to end; only T2, the SLM itself, is missing, which is
exactly the shape Phase 0 is meant to exit in.

**Phase 1 has started early.** Notes are real: a Room-backed store with a sync outbox, an inline
editor with autosave, hashtag parsing, local search, and a checklist mode with drag-reorder and
note↔checklist conversion.

**Not started:** the desktop client, the backend, and the SLM spike.

[Current status and full task list :material-arrow-right:](tasks.md){ .md-button .md-button--primary }

## Stack

| Layer | Choice |
|---|---|
| Android | Kotlin, Jetpack Compose, Room, MediaPipe LLM Inference |
| Desktop | .NET 10, WPF, MVVM (CommunityToolkit.Mvvm), EF Core + SQLite |
| Backend | C# Azure Functions (isolated worker), Cosmos DB (provisioned throughput, free tier) |
| Shared | `Notes.Shared` — one C# contract (DTOs, merge rules) used by desktop and backend alike |

## Constraints worth knowing

These are the ones easy to get wrong by reaching for the more common answer:

- **Azure Functions: isolated worker only.** In-process support ends 2026-11-10, and most
  tutorials predate that.
- **Cosmos DB: provisioned throughput, not serverless.** The free tier requires it, and the
  choice is only available at account creation.
- **The SLM never sits on the launcher's fast path.** It's one tier of a cascade; the launcher
  stays fully usable if the model is absent, loading, or wrong.
- **Notes are Markdown**, not a rich-text document model — checklists included.

The full reasoning for each is in the [design doc](design.md).
