# 0001. Monorepo over split repos

Status: Accepted

## Context

This project ships three separate things — an Android app, a WPF desktop app, and an Azure Functions backend — which is normally a good reason to give each one its own repo. But two of those three genuinely share code: `Notes.Shared` (POCOs, DTOs, tag parsing, merge rules) is referenced by both the desktop app and the backend, and a fixture corpus needs to stay in sync between the C# and Kotlin sync implementations too (design doc §7.3).

## Decision

One repository — `android/`, `desktop/`, `backend/`, `infra/`, `docs/` as top-level folders — not three or four separate repos.

## Consequences

- The desktop app and the backend can't quietly drift apart on what a note *is*. They both reference the same `Notes.Shared` project directly, so the document model and merge rules are defined once, and that's enforced by the code rather than by someone remembering to keep two copies in sync (design doc §5.2).
- The test data for sync scenarios lives in one place instead of two. The shared JSON fixture corpus (`base`/`local`/`remote`/`expected`) is consumed by both the xUnit suite and the Android unit tests from the same commit history, so a change to the corpus and the tests that depend on it can't land out of step across separate repos.
- Shared build settings just apply everywhere without extra coordination. `Directory.Build.props` and `Directory.Packages.props` (T105) work the same way across `backend/` and `desktop/` because they're one repo, not two that would otherwise need to be kept in step by hand.
- There's a real cost to this, though. A change that only touches Android still lands in a repo full of unrelated WPF and Functions history, so commit noise and clone size are both bigger than a split repo would give — at this project's size (one contributor, three deployables sharing one contract), that cost is judged worth paying.
- The build pipeline has to do some of the isolation work that separate repos would give for free. CI/CD (task list section 10) gets scoped per-folder within one pipeline instead — a docs-only change, for instance, still lives in the same Azure DevOps project as the Android build.

## Alternatives considered

- **Three repos, with `Notes.Shared` published as a package.** This would keep each deployable fully independent, but publishing and versioning a package just to share code between two projects one person already controls is overhead with nothing to show for it. The Azure Artifacts feed idea does show up later (T119) — but as deliberate exam-relevant practice, not as the default way to share a contract that two projects in the same repo could just reference directly.
- **Two repos, clients together and the backend on its own.** This is the split most likely to cause exactly the problem `Notes.Shared` exists to prevent — the desktop and the backend are the two ends of the same contract, so separating them is precisely where drift would show up.
