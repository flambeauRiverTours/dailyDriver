# 0010. One ranked candidate list instead of three matched collections

Status: Accepted (2026-09-15)

## Context

Usability testing on the daily driver reported the command line as "all over the place." The code
agreed. Three collections were matched three different ways, related only by a hard-coded cascade
in `dispatchCommand`:

| Collection | How it matched |
|---|---|
| Reserved commands | exact-token walk over T140's registry |
| Aliases | exact lookup in a map |
| Apps | the seven-strategy fuzzy ladder in `AppMatching.kt` |

Two consequences followed, and both were felt rather than theoretical.

**"What will Enter do?" had a different answer per bucket.** A typo in an app name was forgiven;
the identical typo in a command token was not, because commands matched exactly by design. Nothing
ever ranked a command against an app, so there was no single answer to "what did I most likely
mean" — only three separate answers consulted in a fixed order.

**The winner among several matches was effectively alphabetical.** `AppIndex` sorts A→Z and
coverage broke ties, so `ca` resolved to Camera because the label is shorter — regardless of
whether the user had opened Calculator a hundred times and Camera never. T014's log had been
counting real usage since Phase 0 and nothing consulted it.

## Decision

**One candidate list, one pipeline over it, ranked by use.**

A `Candidate` is an app, an alias, or a reserved command, and carries a `target` — the same string
`CommandResolution.target` already records. Matching runs in stages over the whole list: exact,
then the existing fuzzy ladder, then (once T036 lands) the SLM.

Three sub-decisions, taken with the user on 2026-09-15:

**1. Frequency ranks within a match-quality band, not across one.** The strategy ladder decides
tiers; usage orders candidates inside a tier. A heavily-used weak match never outranks an unused
strong one. The alternative — blending usage into the score — was rejected because
`AppMatching.kt` already argues against exactly that shape: it praises max-of-strategies over
"interacting weights" as "far easier to tune and debug," and blending would reintroduce what that
comment rejected.

**2. The one-second timeout replaces the typing-pause debounce rather than joining it.** The
pipeline recomputes per keystroke to paint the preview; a single timer commits one second after
typing stops. Once the SLM exists it runs *inside* that window — deterministic stages finish in
microseconds and paint immediately, and the model either beats the budget and improves the answer
or it does not.

**3. Usage counts live in their own derived index**, not in `command_log`. See below.

## Consequences

**The T0-before-T1 rule becomes structural rather than special-cased.** An alias named `cam` beats
a fuzzy match on Camera because the exact pass completes before the fuzzy pass begins — not
because `dispatchCommand` checks aliases first. The property that `CommandDispatchTest` pins is
now a consequence of the design instead of a rule imposed on top of it.

**`command_log` keeps its single job.** It is training data (T014): keyed `(input, target)` because
it records *phrasings*, and `chr`/`chrome`/`chro` are three examples that happen to share an
outcome. Ranking wants the opposite — one number per target, summed across every way of asking —
so `command_usage` is a separate derived index in the ADR-0007 mould: rebuildable, never synced,
and holding nothing the log does not already imply. Giving the log a second, hot-path job would
have made its failure modes into interaction failures.

**Two behaviours change deliberately, and both are worth stating plainly.**

- `ca` no longer prefers Camera on coverage. With usage data it goes to whichever you open more;
  with none it falls back to the old coverage order. The stale comment in `rankedMatches` was
  updated rather than left to contradict the code.
- Reserved tokens become fuzzy-matchable. `:aps` now reaches `:apps`, where
  `ReservedCommandParser` deliberately matched exactly. This is typo correction of the kind the
  ladder already gives app names, but it *is* a change, not a carry-over.

**The SLM constraint is strengthened, not weakened.** The root hard constraint says the model never
sits on the fast path. Making the one-second budget explicit — after which the best deterministic
answer commits regardless — is a firmer guarantee than the previous arrangement, where nothing
bounded the model's turn at all.

**Three things only implementing it revealed** (added at T184, after the fact).

- **The training log became a second opinion.** `resolutionOf` re-derived its answer from the three
  exact matchers the cascade used to run. Once tokens were fuzzy-matchable, `:aps` ran `:apps` and
  was logged as a T3 *miss*. `classifyResolution`'s own comment had warned about exactly this — "a
  second opinion that could disagree with the first" — and it arrived by a route that comment did
  not anticipate: not a careless duplicate call site, but the same call site left behind by a
  change underneath it. The log now reads the ranking, and the tier it records is the *stage* that
  answered rather than which lookup ran.
- **A typing pause must not commit a reserved command.** Enter commits anything; a pause commits
  only what is cheap to be wrong about. That used to fall out of tokens matching exactly, so no
  timer ever saw a half-typed one. Under fuzzy matching, pausing midway through `:notes` would open
  the editor on `:note`, so the rule has to be stated rather than inherited.
- **`:apps` keeps its own substring filter**, contrary to the plan's "scope the unified list". Its
  auto-launch fires when the filter narrows to exactly one row, and the fuzzy ladder is permissive
  enough — SUBSEQUENCE alone matches dozens of labels on a real device — that it would rarely
  narrow to one, quietly removing the affordance. A filter and a matcher answer different
  questions, and that distinction was already written down in `AppFiltering.kt` before this ADR
  proposed erasing it.

**§4.2's cascade description no longer matches the implementation.** The tiers survive as pipeline
stages and as the `CommandResolution.Tier` labels the log records, but "escalate T0 → T1 → T2 only
when the prior tier's confidence is below threshold" describes separate lookups that no longer
exist. `docs/design.md` §4.2 and `android/CLAUDE.md`'s pipeline table are updated to match.

## Alternatives considered

**Leave the cascade and only add frequency ranking.** Cheapest, and it would have fixed the
alphabetical-winner complaint on its own. Rejected because it leaves the larger half of the
reported problem — that the same typo behaves differently depending on which collection your text
falls into — entirely in place.

**Blend usage into the match score.** Covered under decision 1. The most adaptive option and the
hardest to reason about when it goes wrong, which for a launcher's primary interaction is the
wrong trade.

**Keep the three timers and add a fourth budget for the model.** Rejected: three pause timers with
mutual-exclusion guards between them were already a symptom of the thing being fixed, and a fourth
would have made the interaction harder to describe, not easier.
