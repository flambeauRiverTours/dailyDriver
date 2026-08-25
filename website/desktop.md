# Desktop client, .NET and WPF

!!! warning "In progress. The foundations are built, the notes features are not."

    What runs today: the application starts on the .NET Generic Host with dependency injection,
    creates and migrates its SQLite database through EF Core, and opens a two-pane MVVM window
    whose split position is remembered between runs. Both panes are still empty. There is no note
    list, no editor, no sync and no sign-in yet. The sections below marked as planned are designed
    but not built, and this page is rewritten as each part lands.

A native Windows notes client that works fully offline and syncs with the Android launcher's notes
through the [Azure backend](backend.md).

**Stack:** .NET 10, WPF, MVVM (CommunityToolkit.Mvvm), EF Core with SQLite. All four are in place
and running; what is missing is the features built on top of them.

---

## Why WPF

Chosen over Avalonia and WinUI 3, recorded in
[ADR-0002](https://github.com/flambeauRiverTours/dailyDriver/blob/main/docs/adr/0002-wpf-over-avalonia-winui.md).

This project doubles as a portfolio piece for a .NET job search, and WPF is still the framework most
line-of-business .NET roles hire for. Avalonia is the better cross-platform answer and WinUI 3 is
the newer one, but neither is what the job postings ask for. The target is a single Windows desktop,
so cross-platform support would be paid for and never used. The MVVM discipline, EF Core usage and
async patterns transfer to any of the three.

---

## Planned scope

- Note list with search and tag filtering.
- Markdown editor with checklist support, matching how the Android editor behaves.
- Full offline operation. Local SQLite is authoritative and the network is an optimisation.
- Outbox-based sync mirroring the Android implementation.
- Conflict copies shown as ordinary notes, with no modal prompts.

## The shared contract

Planned, not yet built. `Notes.Shared` will be one C# library referenced by both the desktop client
and the Azure Functions backend, so DTOs, merge rules and parsing are defined once.

Its parsing will have to match Kotlin's exactly. Both languages are to run the
[same JSON fixture corpus](android.md#one-contract-two-languages) instead of maintaining separate
examples. The Kotlin side already does; the C# side is what remains.
