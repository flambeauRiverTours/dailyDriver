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
| [0006](0006-tasks-as-dated-checklist-items.md) | ~~Tasks are dated checklist items, not a separate entity~~ — superseded by 0007 |
| [0007](0007-derived-task-index.md) | A derived, queryable task index over note bodies |
| [0008](0008-server-side-task-index.md) | A server-side derived task index (extends 0007) |
| [0009](0009-markdig-wpf-over-webview2.md) | Markdig.Wpf over WebView2 for the Markdown preview |
| [0010](0010-unified-candidate-matching.md) | One ranked candidate list instead of three matched collections |
| [0011](0011-constrained-decoding-and-fine-tuning-as-experiment.md) | Constrained decoding, and fine-tuning as an experiment rather than a dependency |

0001 to 0005 were already made — they're written up here after the fact, pulled from `docs/design.md`, so each one has its own citable home instead of being buried in a 500-line document. Anything decided from this point forward should get its ADR written at the time, not backfilled later.

0006 is the first one written that way: the decision and the ADR landed together, before any code.

0008 **extends** 0007 rather than superseding it, and the distinction is worth keeping straight. 0007 closed by naming the one thing it deliberately left open — the server's view of tasks — and predicted the follow-up would be "narrower and easier". 0008 is that follow-up: every client-side rule in 0007 stands unchanged. A superseding ADR strikes the old one through; an extending one does not, because nothing in it was wrong.

0010 supersedes nothing, which is worth saying because it looks like it should. It replaces the *implementation* of the command cascade — three collections matched three different ways become one candidate list scored in stages — while leaving the cascade decision itself untouched: exact still beats fuzzy, the SLM is still one stage behind both, and T3 is still the deterministic floor. The shape changed; the ordering it exists to guarantee did not. An ADR that rewrites how a decision is carried out is not the same as one that reverses it.

0011 does not supersede 0003, and the distinction matters more here than usual. 0003's *decision* — MediaPipe over llama.cpp — is not reversed; it is vindicated. What changed is that the single cost it consciously accepted, having no grammar-constrained decoding, turned out to be temporary: the capability exists and the builder simply ships in a companion artifact. So 0003 keeps its status and gains a dated note rather than a strikethrough, because a reader who wants to know whether the MediaPipe bet was sound should find "yes, and the one thing it cost stopped costing" rather than a struck-out page. The thing that *is* a decision, and therefore gets its own ADR, is what to do about it: adopt constrained decoding, and demote fine-tuning from a dependency of the command pipeline to a measured experiment. That demotion is the substantive change, and it is what unblocked T039.

0007 supersedes 0006 within a day of it being accepted, which is the process working rather than failing. The decision was reviewed before `:tasks` was built, the cost of "tasks are not queryable" was judged too high once it was weighed against the query the feature exists to answer, and the reversal is recorded as a new ADR with 0006 left standing and struck through. Editing 0006 to pretend it had always said "derived index" would have hidden the most useful thing here, which is why the first answer was wrong.
