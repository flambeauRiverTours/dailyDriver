# Desktop client, .NET and WPF

!!! warning "In progress. Notes can be written and stored. Sync and sign-in cannot."

    What runs today: the application starts on the .NET Generic Host with dependency injection,
    creates and migrates its SQLite database through EF Core, and opens a two-pane MVVM window
    whose split position is remembered between runs. The right pane is a working Markdown editor
    with a live preview and a three second autosave, and everything it writes is stored in SQLite
    with its tags and tasks indexed. The left pane, which lists and filters notes, is still empty.
    There is no sync and no sign-in yet. The sections below marked as planned are designed but not
    built, and this page is rewritten as each part lands.

A native Windows notes client that works fully offline and syncs with the Android launcher's notes
through the [Azure backend](backend.md).

**Stack:** .NET 10, WPF, MVVM (CommunityToolkit.Mvvm), EF Core with SQLite, Markdig for Markdown.
51 automated tests, most of them running against a real SQLite database rather than a fake one.

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

## The editor

Built. The note body is Markdown, the same format the Android client stores, and a live preview
sits beside the text as you type. Typing stops for three seconds and the note saves itself.

The preview renders through Markdig into a WPF document rather than into a browser control, a
choice recorded in
[ADR-0009](https://github.com/flambeauRiverTours/dailyDriver/blob/main/docs/adr/0009-markdig-wpf-over-webview2.md).
Hosting a browser engine to display a page of text would have added a runtime dependency to the
installer and a second styling system to keep in step with the application's own light and dark
themes. The preview is ordinary application content instead, so it themes and packages like
everything else.

## Writing a note also indexes it

Every note save rebuilds that note's hashtags and its dated checklist items into their own tables,
and queues the note for sync, inside a single database transaction. Either all of it is written or
none of it is.

That last part matters more than it sounds. The alternative, writing the note first and queuing it
afterwards, leaves a gap where the application can close between the two steps and the note is
never sent. Putting both in one transaction is what makes an interrupted save safe to resume.

The queue holds one entry per note rather than one per save, so an afternoon of typing produces a
single pending item instead of hundreds.

## The shared contract

Built. `Notes.Shared` is one C# library referenced by both the desktop client and the Azure
Functions backend, so the note format, the parsers and the task derivation are defined once.

Its parsing has to match Kotlin's exactly. Both languages run the
[same JSON fixture corpus](android.md#one-contract-two-languages) instead of maintaining separate
examples: 95 cases across tag parsing, checklist parsing and due dates. Both sides pass it. Changing
the format means changing the fixture and both implementations together, which is the point.

## Planned scope

- Note list with search and tag filtering.
- Checklist checkboxes that rewrite the underlying Markdown line.
- Light and dark themes following the system setting.
- Outbox-based sync mirroring the Android implementation.
- Conflict copies shown as ordinary notes, with no modal prompts.
