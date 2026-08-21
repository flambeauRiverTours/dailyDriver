# dailyDriver

**A keyboard-first Android launcher with an on-device language model, a native WPF notes client,
and a C# Azure backend — built end to end.**

You type into a prompt instead of hunting through a grid of icons. `spotify` launches Spotify.
`:note standup` opens a note. Eventually, *"remind me to call the dentist tomorrow"* will be parsed
by a fine-tuned small language model running entirely on the phone, with no network call.

It's a personal project chosen deliberately to exercise two things at once: **a complete .NET
story** — WPF/MVVM desktop, EF Core, isolated-worker Azure Functions, Cosmos DB — and **a complete
on-device GenAI story**, from dataset through fine-tuning, quantization, deployment and benchmarks.

---

## Where it stands

| Area | Status | What exists |
|---|---|---|
| **[Android launcher](android.md)** | :material-check-circle:{ .status-done } **Working** | Full CLI shell, app launching, fuzzy matching, aliases, notifications, and a complete notes/checklist system on Room. 540 automated tests. |
| **[Desktop client (.NET/WPF)](desktop.md)** | :material-circle-outline:{ .status-todo } Not started | Project scaffold only. Design complete. |
| **[Azure backend](backend.md)** | :material-circle-outline:{ .status-todo } Not started | Project scaffold only. Design and data model complete. |
| **[On-device ML pipeline](ml-pipeline.md)** | :material-circle-outline:{ .status-todo } Not started | Design, model shortlist and evaluation plan complete. |
| **[Engineering practices](engineering.md)** | :material-check-circle:{ .status-done } **In use** | Trunk-based flow, protected `main`, ADRs, cross-language test fixtures, CI. |

The honest summary: **the Android application is real and substantial; everything else is designed
but unwritten.** This site marks the difference on every page rather than describing plans in the
present tense.

---

## The engineering decisions worth asking about

If you're evaluating this project, these are the parts where something non-obvious was decided:

<div class="grid cards" markdown>

-   **The language model is never on the critical path**

    Input falls through a four-tier cascade — exact match, fuzzy match, model, deterministic
    fallback. The launcher stays fully usable when the model is absent, still downloading, or
    simply wrong. An LLM feature that can take the home screen down with it isn't shippable.

    [Details :material-arrow-right:](android.md#the-command-cascade)

-   **Notes are Markdown, not a document model**

    Checklists are `- [ ]` lines in the note body — no separate item table, no rich-text JSON.
    Two clients in two languages have to agree on the format, and a plain-text format is the one
    where disagreement is visible rather than silent.

    [Details :material-arrow-right:](android.md#notes-and-checklists)

-   **Two languages, one parsing contract**

    Kotlin and C# must parse tags and checklists identically or notes quietly corrupt across
    devices. Both sides execute the *same* JSON fixture corpus, so divergence fails a test
    instead of a user's grocery list.

    [Details :material-arrow-right:](engineering.md#cross-language-test-fixtures)

-   **Sync resolves conflicts by preserving both sides**

    Server-assigned revisions order writes, so a device with a wrong clock can't win. Losing edits
    become conflict copies rather than being discarded — last-write-wins silently destroys work.

    [Details :material-arrow-right:](backend.md#sync-model)

</div>

---

## Stack

=== "Android"

    Kotlin · Jetpack Compose · Room · MediaPipe LLM Inference
    Min/target SDK 33 · single-activity, no fragments

=== "Desktop"

    .NET 10 · WPF · MVVM (CommunityToolkit.Mvvm) · EF Core + SQLite

=== "Backend"

    C# Azure Functions (isolated worker) · Cosmos DB NoSQL · Bicep IaC

=== "ML"

    Gemma-2 2B / Gemma-3 1B · PEFT LoRA · MediaPipe converter · on-device benchmarks

---

## Source

Everything is public: **[github.com/flambeauRiverTours/dailyDriver](https://github.com/flambeauRiverTours/dailyDriver)**

The repository also carries the working engineering documents this site summarizes — the
[full design document](https://github.com/flambeauRiverTours/dailyDriver/blob/main/docs/design.md),
the [task log](https://github.com/flambeauRiverTours/dailyDriver/blob/main/docs/tasks.md) recording
what was decided and what broke while building each feature, and five
[architecture decision records](https://github.com/flambeauRiverTours/dailyDriver/tree/main/docs/adr).
They're deliberately not reproduced here — this site is the overview; those are the detail.

!!! info "Last updated: 21 August 2026"

    Reflects the state of `main` as of that date. See each area page for what has changed most
    recently.
