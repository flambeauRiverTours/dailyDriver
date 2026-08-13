# 0005. Conflict-copy resolution over plain last-write-wins

Status: Accepted

## Context

In v1 the desktop was just a web page, so only the phone could ever go offline and diverge — concurrent conflicting edits were mostly a theoretical concern. Then v2 turned the desktop into a fully offline-capable native client, and now **two independent copies of the data can both accept writes while disconnected** (§7). The design doc doesn't treat this as an edge case either — editing a note on the phone on the train, then again on the desktop before the phone syncs, is described as "something you will actually hit... a Tuesday." That meant a conflict policy was needed that both clients and the server could agree on.

## Decision

**Whoever wrote most recently wins, but the loser doesn't just disappear — it gets saved as a separate "conflict copy" note.** This is a middle ground: not plain last-write-wins, and not a full CRDT. Technically, the server assigns a monotonically increasing `rev` to every accepted write; when a client pushes a change whose `baseRev` is older than the server's current `rev` for that note, the server accepts the newer write, returns a conflict marker, and the client turns the losing version into a new note titled `<title> (conflict, <device>, <timestamp>)`. Because the server — not the client — assigns `rev`, ordering stays correct even if two devices' clocks disagree.

## Consequences

- The biggest win is that nothing gets silently thrown away. Plain last-write-wins was fine, per the design doc, back when only one replica could diverge — it's explicitly not fine now that two can. Saving the conflict copy is the price of that guarantee, and it's treated as a hard requirement, not a nice-to-have: the correctness checklist in §8 lists "loser preserved as a conflict copy" as a pass/fail test.
- Merging is left to the user, by hand. For a personal, single-user notes tool that's judged the right tradeoff — it's cheap to build, it's impossible to lose data with, and it's "the same thing Dropbox does," which the design doc calls out directly as an easy thing to explain and defend in an interview (§7.2).
- There's also a lower-level safety net underneath all of this. `If-Match` on the Cosmos ETag guards against a narrower race — two writes landing on the server at nearly the same instant — and it's separate from the app-level `rev` conflict policy: a conflict here triggers a re-pull, not a conflict copy.
- Deletes need their own cleanup policy, too. A delete propagates as a "tombstone" record rather than actually removing anything right away, and those tombstones get purged after 90 days (§7.1) — a deliberate middle ground between purging immediately (which risks a delete getting lost to a race) and keeping them forever (which just grows without bound).

## Alternatives considered

- **Plain last-write-wins, with no conflict copy at all.** This is simpler, and it was arguably good enough back when v1 only had one replica that could ever go offline. It got rejected once the desktop became independently offline-capable, because it silently throws away the losing edit — exactly the failure the conflict-copy policy exists to stop.
- **CRDTs.** The design doc doesn't dodge this one — it calls CRDTs "the correct general answer to this problem" (§7.4) and rules them out anyway, on the grounds that for a single user with occasional conflicts, a conflict-copy strategy gets most of the benefit for a fraction of the implementation cost. It goes further and argues that being able to explain *why* CRDTs were skipped here — and when you'd choose differently for a multi-user product — is worth more in an interview than a half-finished CRDT implementation would be.
