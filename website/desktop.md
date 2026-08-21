# Desktop client, .NET and WPF

!!! warning "Not started. Design is complete, the code is a scaffold."

    `desktop/Notes.Desktop` holds a default WPF project targeting `net10.0-windows` and nothing
    else. Everything below is designed but not built. This page gets rewritten as it lands.

A native Windows notes client that works fully offline and syncs with the Android launcher's notes
through the [Azure backend](backend.md).

**Planned stack:** .NET 10, WPF, MVVM (CommunityToolkit.Mvvm), EF Core with SQLite.

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

`Notes.Shared` is one C# library referenced by both the desktop client and the Azure Functions
backend, so DTOs, merge rules and parsing are defined once.

Its parsing has to match Kotlin's exactly. Both languages run the
[same JSON fixture corpus](android.md#one-contract-two-languages) instead of maintaining separate
examples.
