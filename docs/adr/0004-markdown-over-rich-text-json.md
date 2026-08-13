# 0004. Markdown over rich-text JSON as the note storage format

Status: Accepted

## Context

Notes and checklists need one storage format that works the same way in three different places: Room on Android, EF Core/SQLite on desktop, and the Cosmos DB document body on the backend. The original argument for Markdown was fairly narrow — a rich-text JSON format like Quill Delta or ProseMirror doesn't play well with a plain-text, vim-style modal editor. Then in v3 that modal editor got replaced with a Google Keep-style touch editor (§4.6), which quietly undercuts that argument, since a touch editor doesn't have the same friction with rich text that a keyboard-driven modal one did. So the format choice got revisited rather than just carried forward on autopilot.

## Decision

**Markdown stays as the storage format**, even after the editor redesign removed the original reason for choosing it. Checklists are `- [ ]` / `- [x]` lines parsed into structured items for the UI; hashtags (`#work`) are parsed out of the body on save and denormalized to a `tags` array for querying. The body is the single source of truth — checklist items and tags are both *derived*, never stored as a separate normalized structure.

## Consequences

- The desktop client gets parity basically for free. It already needs a Markdown renderer (Markdig) for its live-preview editor (§5.3), so Android and desktop end up reading and writing the exact same format with no translation layer between them.
- A note's history is just readable text. You can look at a plain diff and understand what changed, and the data isn't locked into a proprietary document model if the project or its storage backend ever changes.
- The tradeoff is that two different languages now have to agree on how to read a note. Kotlin on Android and C# in `Notes.Shared`/desktop both have to parse `- [ ]`/`- [x]` and `#hashtag` the exact same way, or the same note shows different checklist state depending on which client you're looking at it from. That's the same kind of drift risk the shared fixture corpus (ADR 0001, §7.3) exists to catch for sync logic — checklist and tag parsing doesn't have an equivalent cross-language test corpus yet, which is worth watching as both parsers get built.
- You only get the formatting Markdown can express. Bold, italic, lists, and links are all fine; anything a rich-text model would hand you for free — inline comments, tracked changes, embedded rich media — is out of reach unless the format gets extended later.

## Alternatives considered

- **Rich-text JSON, like Quill Delta or ProseMirror.** This was rejected back in v2, and it was genuinely reconsidered rather than just assumed to still be the right call once the editor changed. It got rejected again anyway — it would still cost desktop parity (there's no Markdig-equivalent for a proprietary rich-text tree), diffability, and portability. The touch-editor change knocked out the strongest reason to avoid it, not the only one.
- **Plain unstructured text, with checklist state stored in a separate table.** This would sidestep parsing entirely, but it breaks the "one source of truth" idea — a checklist item's checked state could drift away from what the note body itself says, which is exactly the bug the derived-not-duplicated design in §4.6 and §6.2 is meant to prevent.
