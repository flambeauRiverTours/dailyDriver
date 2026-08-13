# 0002. WPF over Avalonia / WinUI 3 for the desktop client

Status: Accepted

## Context

The original design didn't have a real desktop app at all — just a web page (an Azure Static Web Apps SPA). Version 2 replaced that with a genuine, offline-capable native Windows application, which meant picking a UI framework, and the project's stated goals make this pick matter beyond the app itself: demonstrating end-to-end .NET is a first-class portfolio goal (design doc §1), aimed squarely at a .NET job search. Cross-platform desktop support was ruled out up front — the design doc treats "WPF is Windows-only" as an accepted tradeoff, not a problem to solve.

## Decision

**WPF (.NET 10), MVVM throughout**, over Avalonia (cross-platform XAML) or WinUI 3 (Microsoft's newer native UI stack).

## Consequences

- This is mostly a hiring bet, and it's an intentional one. WPF shows up in far more enterprise .NET job postings than WinUI 3 or MAUI, meaning a hiring manager is statistically more likely to have it running in their own production code — which is exactly the point of treating this as a portfolio piece.
- WPF also isn't as dated as its reputation suggests. It ships with .NET 10 and is, per the design doc, "in better shape in 2026 than it has been for years" — Fluent theme support and Store/MSIX distribution close most of the visual and packaging gap with newer UI stacks.
- There's still a real risk here, and the design doc names it directly: some reviewers will see WPF and think "legacy." The fix for that isn't a different technology, it's how the project gets described — lead with .NET 10, MVVM, DI, EF Core, and MSIX in any writeup, since those are the parts that actually transfer between frameworks and stay current regardless of how old WPF itself is.
- There's no path to Mac or Linux here, and that's fine — it was ruled out on purpose. If a non-Windows desktop client is ever wanted later, that means rewriting the UI layer, not just recompiling it.
- If that non-goal ever does get revisited, the work isn't wasted. WPF's XAML/MVVM patterns map onto Avalonia far more directly than they would onto a rewrite in a completely different UI paradigm.

## Alternatives considered

- **Avalonia.** This would have kept the door open to macOS and Linux — it's cross-platform, modern, and also XAML-based. It was rejected because cross-platform desktop isn't a goal here, and its footprint in the 2026 job market is smaller than WPF's for the specific hiring story this project is built around.
- **WinUI 3 / MAUI.** These are Microsoft's newer native stacks, and the more "current" recommendation on paper. They lost for the same job-market reason as Avalonia: they show up less often in the enterprise .NET postings this project is aimed at than WPF does.
- **Keep the v1 web app and skip a native client entirely.** This stopped being viable once v2 required genuine offline capability — a web client can't deliver "fully functional offline" (a stated product goal, §1) as directly as a native app with its own local database can.
