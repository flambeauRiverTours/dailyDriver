# dailyDriver

**A keyboard-first Android launcher with an on-device language model, a native WPF notes client, and a C# Azure backend.**

You type into a prompt instead of hunting through a grid of icons. Typing `spotify` launches
Spotify. Typing `:note standup` opens a note. Later, a fine-tuned small language model running on
the phone will handle phrases like *"remind me to call the dentist tomorrow"*, with no network
call.

A personal project, picked to cover two areas at once: a full .NET stack (WPF/MVVM desktop, EF
Core, isolated-worker Azure Functions, Cosmos DB), and a full on-device GenAI workflow from dataset
through fine-tuning, quantization, deployment and benchmarks.

---

## Where it stands

| Area | Status | What exists |
|---|---|---|
| **[Android launcher](android.md)** | :material-check-circle:{ .status-done } **Shipped** | CLI shell, app launching, fuzzy matching, aliases, notifications, and a full notes, checklist and task system on Room. 656 automated tests. |
| **[Desktop client (.NET/WPF)](desktop.md)** | :material-circle-outline:{ .status-todo } Not started | Project scaffold. Design complete. |
| **[Azure backend](backend.md)** | :material-circle-outline:{ .status-todo } Not started | Project scaffold. Design and data model complete. |
| **[On-device ML pipeline](ml-pipeline.md)** | :material-circle-outline:{ .status-todo } Not started | Design, model shortlist and evaluation plan complete. |

The Android launcher is finished to its first milestone and in daily use as a real home screen,
installed from a signed release built by CI. The other three have finished designs and empty
project scaffolds. Every page says which it is at the top.

---

## Design decisions

The parts where the obvious answer was not the one I took.

<div class="grid cards" markdown>

-   **The language model never sits on the critical path**

    Input falls through four tiers: exact match, fuzzy match, model, deterministic fallback. The
    launcher works normally when the model is missing, still downloading, or wrong.

    [Details :material-arrow-right:](android.md#the-command-cascade)

-   **Notes are stored as Markdown**

    Checklists are `- [ ]` lines inside the note body. No items table, no rich-text JSON. Two
    clients in two languages have to agree on the format, and with plain text a disagreement is
    easy to see.

    [Details :material-arrow-right:](android.md#notes-and-checklists)

-   **Two languages, one parsing contract**

    Kotlin and C# have to parse tags and checklists identically or notes corrupt as they sync.
    Both run the same JSON fixture files, so any difference fails a test.

    [Details :material-arrow-right:](android.md#one-contract-two-languages)

-   **Sync keeps both sides of a conflict**

    The server assigns revision numbers, so a device with a wrong clock cannot win. When two
    offline edits collide, the loser is kept as a conflict copy. Last-write-wins would throw it
    away.

    [Details :material-arrow-right:](backend.md#sync-model)

</div>

---

## Stack

=== "Android"

    Kotlin, Jetpack Compose, Room, MediaPipe LLM Inference.
    Min and target SDK 33, single activity, no fragments.

=== "Desktop"

    .NET 10, WPF, MVVM (CommunityToolkit.Mvvm), EF Core with SQLite.

=== "Backend"

    C# Azure Functions (isolated worker), Cosmos DB NoSQL, Bicep IaC.

=== "ML"

    Gemma-2 2B or Gemma-3 1B, PEFT LoRA, MediaPipe converter, on-device benchmarks.

---

## Source

**[github.com/flambeauRiverTours/dailyDriver](https://github.com/flambeauRiverTours/dailyDriver)**

The repository also holds the working engineering documents: the
[design document](https://github.com/flambeauRiverTours/dailyDriver/blob/main/docs/design.md), a
[task log](https://github.com/flambeauRiverTours/dailyDriver/blob/main/docs/tasks.md) recording what
was decided and what broke while building each feature, and six
[architecture decision records](https://github.com/flambeauRiverTours/dailyDriver/tree/main/docs/adr).
This site is the overview and links to them.

!!! info "Last updated: 22 August 2026"

    Reflects the state of `main` on that date.
