# 0009. Markdig.Wpf over WebView2 for the Markdown preview

Status: Accepted (2026-09-14)

## Context

[§5.1](../design.md) pins **Markdig** as the Markdown library for the desktop client, and [§5.3](../design.md) calls for a "Markdown editor with live preview". Neither says how the preview is actually drawn, and that turns out to be the load-bearing half of the decision: **Markdig renders to HTML, and WPF has no HTML renderer.** The library settles parsing; it does not settle display.

That gap has gone unnoticed because nothing until now needed it. T057 is the first task that does, and it is not a detail to discover mid-implementation — the editor is built on top of whatever is chosen, and two later tasks depend on the answer:

- **T059** (light/dark themes bound to system preference) has to theme the preview along with everything else. §5.3 makes this client the only one with both themes, so it is not optional.
- **T063** (`dotnet publish` self-contained, single-file, MSIX) has to package whatever the preview needs. §5.5 calls the packaging story "overkill for one user, deliberately" — precisely because it is the tedious part most side projects skip and employers care about, so anything that complicates it costs more here than it would elsewhere.

## Decision

**Render the preview with `Markdig.Wpf`, which turns Markdig's AST into a WPF `FlowDocument`.**

No browser, no HTML, no second styling system. The preview becomes ordinary WPF content: it themes with WPF styles like every other surface, it packages like every other assembly, and it renders with the same text stack as the rest of the application.

Markdown parsing stays Markdig itself, unchanged and shared with whatever else needs it. `Markdig.Wpf` is only the rendering half.

## Consequences

**T059 gets the preview for free, or nearly.** A `FlowDocument` inherits WPF's theming, so the light/dark work applies to the preview by construction rather than needing a parallel implementation. With a browser, the preview would need its own CSS, tracking the WPF theme by hand — two styling systems describing one surface, and the kind of thing that silently drifts.

**T063's packaging is unaffected.** One more managed assembly, nothing else. No runtime dependency to detect, bootstrap, or bundle.

**A third-party rendering dependency is accepted, and it is the real cost.** `Markdig.Wpf` is community-maintained, and its version compatibility with Markdig proper must be checked before it lands rather than assumed — a renderer pinned to an old Markdig would drag the parser back with it. **The exit is cheap and known**: if it is unmaintained or diverges, the fallback is a hand-rolled `FlowDocument` renderer over Markdig's own AST, which is real work but bounded and owns no new dependency. That fallback is the reason this decision is reversible in a way the WebView one would not be.

**Anything Markdig emits that the WPF renderer does not support degrades rather than fails.** Acceptable: the corpus here is personal notes in a format this project already constrains — `- [ ]` checklists, `#hashtags`, `@YYYY-MM-DD` tokens, prose. Exotic Markdown is out of scope by §6.2.1 rather than by accident.

**Checklist rendering stays the body's job, not the preview's.** T058 has checkboxes rewriting the underlying `- [ ]` line per §6.2.1's parser. That is an editor interaction over the Markdown source, not something the preview renderer provides, and this decision does not change it.

## Alternatives considered

- **WebView2 with Markdig's HTML output.** Microsoft-supported, CSS-stylable, and handles anything Markdig can emit — the most capable option, and rejected on cost rather than capability. It pulls a browser runtime into an application whose stated appeal is being a lean native Windows client; it complicates T063's MSIX story with a runtime dependency and a much larger installer; and it makes T059 maintain CSS in parallel with WPF styles. A browser engine to preview a personal notes file is the kind of dependency [desktop/CLAUDE.md](../../desktop/CLAUDE.md)'s simplicity rule exists to push back on. It is also the option that invites the interview question this project should be able to answer well: *why is a WPF app hosting a browser to render text?*
- **A hand-rolled `FlowDocument` renderer over Markdig's AST.** No new dependency, total theme control, and Markdig's AST is pleasant to walk. Rejected as the *first* choice only because it is work with no payoff over a library that already does it — but retained deliberately as the fallback above, which is what keeps the chosen option low-risk.
- **No live preview; edit raw Markdown only.** Genuinely cheaper, and rejected because §5.3 names live preview as part of what this client is, and because the desktop is explicitly the reading surface (§5.3 gives it both themes for that reason) while Android is the capture surface.
