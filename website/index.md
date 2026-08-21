# dailyDriver

**A keyboard-first Android launcher with an on-device language model, a native WPF notes client, and a C# Azure backend.**

You type into a prompt instead of hunting through a grid of icons. Typing `spotify` launches
Spotify. Typing `:note standup` opens a note. Later, a fine-tuned small language model running on
the phone will handle phrases like *"remind me to call the dentist tomorrow"*, with no network
call.

This is a personal project. I picked it to cover two areas at once: a full .NET stack (WPF/MVVM
desktop, EF Core, isolated-worker Azure Functions, Cosmos DB), and a full on-device GenAI
workflow, from dataset through fine-tuning, quantization, deployment and benchmarks.

---

## Where it stands

| Area | Status | What exists |
|---|---|---|
| **[Android launcher](android.md)** | :material-check-circle:{ .status-done } **Working** | Full CLI shell, app launching, fuzzy matching, aliases, notifications, and a complete notes and checklist system on Room. 540 automated tests. |
| **[Desktop client (.NET/WPF)](desktop.md)** | :material-circle-outline:{ .status-todo } Not started | Project scaffold only. Design complete. |
| **[Azure backend](backend.md)** | :material-circle-outline:{ .status-todo } Not started | Project scaffold only. Design and data model complete. |
| **[On-device ML pipeline](ml-pipeline.md)** | :material-circle-outline:{ .status-todo } Not started | Design, model shortlist and evaluation plan complete. |
| **[Engineering practices](engineering.md)** | :material-check-circle:{ .status-done } **In use** | Trunk-based flow, protected `main`, ADRs, cross-language test fixtures, CI. |

The Android application is built and working. The other three areas have finished designs and
empty project scaffolds. Every page here says which it is at the top.

---

## Design decisions

If you are looking at this project, these are the parts where the obvious answer was not the one I
took.

<div class="grid cards" markdown>

-   **The language model never sits on the critical path**

    Input falls through four tiers: exact match, fuzzy match, model, then a deterministic
    fallback. The launcher works normally when the model is missing, still downloading, or wrong.
    I would not ship a home screen that an LLM feature can take down with it.

    [Details :material-arrow-right:](android.md#the-command-cascade)

-   **Notes are stored as Markdown**

    Checklists are `- [ ]` lines inside the note body. There is no items table and no rich-text
    JSON. Two clients written in two languages have to agree on the format, and with plain text a
    disagreement is easy to see.

    [Details :material-arrow-right:](android.md#notes-and-checklists)

-   **Two languages, one parsing contract**

    Kotlin and C# have to parse tags and checklists identically, or notes corrupt quietly as they
    sync. Both languages run the same JSON fixture files, so any difference between them fails a
    test.

    [Details :material-arrow-right:](engineering.md#cross-language-test-fixtures)

-   **Sync keeps both sides of a conflict**

    The server assigns revision numbers, so a device with a wrong clock cannot win. When two
    offline edits collide, the losing version is kept as a conflict copy. Last-write-wins would
    throw it away.

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

Everything is public: **[github.com/flambeauRiverTours/dailyDriver](https://github.com/flambeauRiverTours/dailyDriver)**

The repository also holds the working engineering documents this site summarizes: the
[design document](https://github.com/flambeauRiverTours/dailyDriver/blob/main/docs/design.md), the
[task log](https://github.com/flambeauRiverTours/dailyDriver/blob/main/docs/tasks.md) recording what
was decided and what broke while building each feature, and five
[architecture decision records](https://github.com/flambeauRiverTours/dailyDriver/tree/main/docs/adr).
Those are the detail. This site is the overview, so it links to them and does not reproduce them.

!!! info "Last updated: 21 August 2026"

    Reflects the state of `main` on that date. Each area page covers what changed most recently.
