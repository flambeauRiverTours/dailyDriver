# Azure backend

!!! warning "Not started. Design is complete, the code is a scaffold."

    `backend/Notes.Functions` and `backend/Notes.Shared` are empty project files. The API surface,
    document model and sync semantics below are designed and decided but not built. This page gets
    rewritten as it lands.

A C# Azure Functions backend that stores notes in Cosmos DB and arbitrates sync between the Android
launcher and the WPF desktop client.

**Planned stack:** C# Azure Functions (isolated worker), Cosmos DB NoSQL, Bicep IaC, on a fixed
$0/month budget.

---

## Two constraints that are easy to get wrong

Both are recorded as hard constraints because the common answer is the wrong one.

**Isolated worker model, never in-process.** In-process support for Azure Functions ends on
**10 November 2026**. Most tutorials and Stack Overflow answers predate that and demonstrate the
model being retired.

**Cosmos DB provisioned throughput, not serverless.** The free tier requires provisioned-throughput
capacity mode, and that mode can only be selected when the account is created. Choosing serverless,
which sounds right sitting next to "Azure Functions", forfeits the free tier permanently. Fixing it
means destroying the account and starting again.

---

## Sync model

Local-first. Each client owns an authoritative local database and a transactional outbox. The
server assigns ordering.

```mermaid
flowchart LR
    A["Android<br/>Room + outbox"] -->|push| F["Azure Functions<br/>isolated worker"]
    D[".NET desktop<br/>EF Core + outbox"] -->|push| F
    F -->|"assigns rev"| C[("Cosmos DB")]
    F -->|pull| A
    F -->|pull| D
```

Both clients push their outbox to the Functions app, which assigns a revision number and writes to
Cosmos. Clients pull changes since the last revision they saw.

**Revisions are assigned by the server, not the client.** Ordering does not depend on device
clocks, so a phone set an hour ahead cannot win a conflict it should have lost. Clock skew is
designed out as a source of corruption.

**Conflicts produce copies.** When two devices edit the same note offline, the higher revision
keeps the name and the losing version is saved as a conflict copy, which is an ordinary note the
user can read and merge by hand. Plain last-write-wins is simpler and quietly destroys work. The
reasoning is in
[ADR-0005](https://github.com/flambeauRiverTours/dailyDriver/blob/main/docs/adr/0005-conflict-copies-over-plain-lww.md).

### Correctness scenarios

Sync is specified as pass/fail cases, to be automated in xUnit against an in-memory harness:

| Scenario | Expectation |
|---|---|
| Edit offline on one client | Converges on reconnect, no conflict copy |
| Edit offline on both, same note | Higher `rev` wins, loser saved as a conflict copy |
| Delete on one, edit on the other | Tombstone wins, edited version preserved |
| Sync interrupted mid-push | Outbox replays, no duplicates and no lost writes |
| Device clock an hour ahead | Ordering unaffected, since `rev` is server-assigned |
| Shared fixture corpus | Kotlin and C# produce identical parses |

---

## Planned API

A small REST surface over Cosmos: push a batch of outbox entries, pull changes since a known
revision, and resolve conflicts server-side. Authentication is deferred on purpose, since a
single-user personal project does not need identity infrastructure before it has working sync.

Infrastructure is defined in Bicep, so the whole environment can be rebuilt from source with no
clicking through a portal.
