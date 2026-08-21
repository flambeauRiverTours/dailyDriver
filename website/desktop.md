# Desktop client — .NET / WPF

!!! warning "Not started — design complete, code is a scaffold"

    `desktop/Notes.Desktop` currently contains a default WPF project targeting `net10.0-windows`
    and nothing else. Everything below is **designed and decided, not built**. This page will be
    rewritten as it lands.

A native Windows notes client that works fully offline and syncs with the Android launcher's
notes through the [Azure backend](backend.md).

**Planned stack:** .NET 10 · WPF · MVVM (CommunityToolkit.Mvvm) · EF Core + SQLite

---

## Why WPF

Chosen over Avalonia and WinUI 3 — recorded in
[ADR-0002](https://github.com/flambeauRiverTours/dailyDriver/blob/main/docs/adr/0002-wpf-over-avalonia-winui.md).

The project doubles as a portfolio piece for a .NET job search, and WPF remains the framework most
line-of-business .NET roles actually hire for. Avalonia is the better cross-platform answer and
WinUI 3 is the newer one; neither is what the job postings ask for. The target is a single
Windows desktop, so cross-platform capability would be paid for and unused.

The MVVM discipline, EF Core usage, and async patterns transfer to any of the three.

---

## Planned scope

- Note list with search and tag filtering
- Markdown editor with checklist support, matching the Android editor's behaviour
- Full offline operation — local SQLite is authoritative; the network is an optimisation
- Outbox-based sync mirroring the Android implementation
- Conflict copies surfaced in the UI as ordinary notes rather than modal prompts

## The shared contract

`Notes.Shared` is a single C# library referenced by **both** the desktop client and the Azure
Functions backend — DTOs, merge rules, and parsing all defined once.

The parsing rules it implements must match Kotlin's exactly. That's enforced by both languages
executing the [same JSON fixture corpus](engineering.md#cross-language-test-fixtures) rather than
by each maintaining its own examples.
