# Architecture Decision Records

Each file here covers one significant decision — the situation that forced it, what was decided, and what's given up by deciding it that way. They're numbered and treated as permanent once accepted: if a decision changes later, that gets a **new** ADR that supersedes the old one, rather than editing the old one to pretend it always said that.

## Format

```
# NNNN. Title

Status: Accepted | Superseded by NNNN

## Context
What forced a decision here.

## Decision
What was decided.

## Consequences
What this costs, not just what it buys.

## Alternatives considered
What else was on the table, and why it lost.
```

## Index

| ADR | Decision |
|---|---|
| [0001](0001-monorepo-over-split-repos.md) | Monorepo over three separate repos |
| [0002](0002-wpf-over-avalonia-winui.md) | WPF over Avalonia / WinUI 3 for the desktop client |
| [0003](0003-mediapipe-over-llama-cpp.md) | MediaPipe LLM Inference API over llama.cpp for on-device inference |
| [0004](0004-markdown-over-rich-text-json.md) | Markdown over rich-text JSON as the note storage format |
| [0005](0005-conflict-copies-over-plain-lww.md) | Conflict-copy resolution over plain last-write-wins |
| [0006](0006-tasks-as-dated-checklist-items.md) | Tasks are dated checklist items, not a separate entity |

0001 to 0005 were already made — they're written up here after the fact, pulled from `docs/design.md`, so each one has its own citable home instead of being buried in a 500-line document. Anything decided from this point forward should get its ADR written at the time, not backfilled later.

0006 is the first one written that way: the decision and the ADR landed together, before any code.
