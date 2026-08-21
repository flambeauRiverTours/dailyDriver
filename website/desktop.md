# Desktop client, .NET and WPF

!!! warning "Not started. Design is complete, the code is a scaffold."

    `desktop/Notes.Desktop` currently holds a default WPF project targeting `net10.0-windows` and
    nothing else. Everything below is designed and decided but not built. This page gets rewritten
    as it lands.

A native Windows notes client that works fully offline and syncs with the Android launcher's notes
through the [Azure backend](backend.md).

**Planned stack:** .NET 10, WPF, MVVM (CommunityToolkit.Mvvm), EF Core with SQLite.

---

## Why WPF

Chosen over Avalonia and WinUI 3, recorded in
[ADR-0002](https://github.com/flambeauRiverTours/dailyDriver/blob/main/docs/adr/0002-wpf-over-avalonia-winui.md).

This project doubles as a portfolio piece for a .NET job search, and WPF is still the framework
most line-of-business .NET roles hire for. Avalonia is the better cross-platform answer and WinUI 3
is the newer one, but neither is what the job postings ask for. The target here is a single Windows
desktop, so cross-platform support would be paid for and never used.

The MVVM discipline, EF Core usage and async patterns all transfer to any of the three.

---

## Planned scope

- Note list with search and tag filtering.
- Markdown editor with checklist support, matching how the Android editor behaves.
- Full offline operation. The local SQLite database is authoritative and the network is an
  optimisation.
- Outbox-based sync mirroring the Android implementation.
- Conflict copies shown in the UI as ordinary notes, with no modal prompts.

## The shared contract

`Notes.Shared` is a single C# library referenced by both the desktop client and the Azure Functions
backend. DTOs, merge rules and parsing are all defined once.

The parsing rules it implements have to match Kotlin's exactly. That is enforced by having both
languages run the [same JSON fixture corpus](engineering.md#cross-language-test-fixtures), so
neither side maintains its own examples.
