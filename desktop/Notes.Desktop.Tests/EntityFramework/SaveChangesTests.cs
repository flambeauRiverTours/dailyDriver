using FluentAssertions;
using Microsoft.EntityFrameworkCore;
using Notes.Desktop.EntityFramework.Model;
using Notes.Desktop.Tests.Helpers;
using NoteType = Notes.Shared.Model.NoteType;

namespace Notes.Desktop.Tests.EntityFramework
{
    /// <summary>
    /// Covers <c>AppDbContext.SaveChangesAsync</c>'s derive-on-save behaviour: every write to a
    /// <see cref="Note"/> rebuilds that note's tag and task projections and enqueues an outbox row,
    /// all inside the one transaction <c>base.SaveChangesAsync</c> opens.
    ///
    /// **Every assertion reads through a second context over the same connection.** Asserting
    /// against the context that did the writing proves only that the change tracker holds what was
    /// put in it; a fresh context is the only way to show a row actually reached SQLite.
    /// </summary>
    public class SaveChangesTests
    {
        private static CancellationToken Ct => TestContext.Current.CancellationToken;

        /// <summary>
        /// A checklist body exercising three shapes at once: a dated open item, a dated done item,
        /// and an undated item (a checklist item, but never a task). Tags appear both inside a task
        /// line and in loose prose.
        /// </summary>
        private const string ChecklistBody =
            "- [ ] ship the thing @2026-09-20 #work\n" +
            "- [x] wrote the ADR @2026-09-01\n" +
            "- [ ] someday, no date\n" +
            "loose prose mentioning #home";

        private static Note NewNote(string body, NoteType type = NoteType.Note, long? rev = null) => new()
        {
            Id = Guid.NewGuid(),
            Type = type,
            Title = "a title",
            Body = body,
            CreatedAt = DateTimeOffset.UtcNow,
            UpdatedAt = DateTimeOffset.UtcNow,
            Deleted = false,
            Rev = rev,
            SourceDevice = "Desktop"
        };

        #region Tags

        [Fact]
        public async Task SaveChangesAsync_OnInsert_DerivesTagsFromBody()
        {
            using var factory = new InMemoryDbContextFactory();
            var note = NewNote("standup notes #work #Home and a dupe #work");

            using (var write = factory.CreateDbContext())
            {
                write.Notes.Add(note);
                await write.SaveChangesAsync(Ct);
            }

            using var read = factory.CreateDbContext();
            var tags = await read.NoteTags.Where(t => t.NoteId == note.Id)
                                          .Select(t => t.Tag).ToListAsync(Ct);

            // Lower-cased and de-duplicated by TagParser, so "#Home" and the second "#work" collapse.
            tags.Should().BeEquivalentTo(["work", "home"]);
        }

        [Fact]
        public async Task SaveChangesAsync_OnUpdate_RebuildsTagsRatherThanAppending()
        {
            using var factory = new InMemoryDbContextFactory();
            var note = NewNote("first pass #work");

            using (var write = factory.CreateDbContext())
            {
                write.Notes.Add(note);
                await write.SaveChangesAsync(Ct);
            }

            using (var edit = factory.CreateDbContext())
            {
                var tracked = await edit.Notes.SingleAsync(n => n.Id == note.Id, Ct);
                tracked.Body = "second pass #home";
                await edit.SaveChangesAsync(Ct);
            }

            using var read = factory.CreateDbContext();
            var tags = await read.NoteTags.Where(t => t.NoteId == note.Id)
                                          .Select(t => t.Tag).ToListAsync(Ct);

            // "work" is gone, not merely joined by "home": the derivation replaces, it does not accrue.
            tags.Should().BeEquivalentTo(["home"]);
        }

        [Fact]
        public async Task SaveChangesAsync_IgnoresTagsInsideFencedCode()
        {
            using var factory = new InMemoryDbContextFactory();
            var note = NewNote("real #work\n```\n#notatag\n```\nalso #home");

            using (var write = factory.CreateDbContext())
            {
                write.Notes.Add(note);
                await write.SaveChangesAsync(Ct);
            }

            using var read = factory.CreateDbContext();
            var tags = await read.NoteTags.Where(t => t.NoteId == note.Id)
                                          .Select(t => t.Tag).ToListAsync(Ct);

            tags.Should().BeEquivalentTo(["work", "home"]);
        }

        #endregion

        #region Tasks

        [Fact]
        public async Task SaveChangesAsync_OnChecklistInsert_DerivesOnlyDatedItemsAsTasks()
        {
            using var factory = new InMemoryDbContextFactory();
            var note = NewNote(ChecklistBody, NoteType.Checklist);

            using (var write = factory.CreateDbContext())
            {
                write.Notes.Add(note);
                await write.SaveChangesAsync(Ct);
            }

            using var read = factory.CreateDbContext();
            var tasks = await read.Tasks.Where(t => t.NoteId == note.Id)
                                        .OrderBy(t => t.Position)
                                        .ToListAsync(Ct);

            // Three checklist items, two dated. The undated one is a checklist item but not a task.
            tasks.Should().HaveCount(2);

            // The hashtag stays in the task text. Only the due-date token is consumed, because
            // @2026-09-20 is syntax and #work is content the user wrote and expects to read back.
            tasks[0].Text.Should().Be("ship the thing #work");
            tasks[0].Due.Should().Be("2026-09-20");
            tasks[0].Checked.Should().BeFalse();
            tasks[0].Position.Should().Be(0);

            tasks[1].Text.Should().Be("wrote the ADR");
            tasks[1].Due.Should().Be("2026-09-01");
            tasks[1].Checked.Should().BeTrue();

            // Position indexes ALL checklist items, not just the dated ones. Pinning it stops a
            // future "renumber tasks 0..n" simplification from going unnoticed — the position is
            // how a checkbox toggle finds its line back in the body.
            tasks[1].Position.Should().Be(1);
        }

        [Fact]
        public async Task SaveChangesAsync_OnPlainNote_DerivesNoTasksEvenWithDatedCheckboxes()
        {
            using var factory = new InMemoryDbContextFactory();
            var note = NewNote(ChecklistBody, NoteType.Note);

            using (var write = factory.CreateDbContext())
            {
                write.Notes.Add(note);
                await write.SaveChangesAsync(Ct);
            }

            using var read = factory.CreateDbContext();

            // TaskIndex short-circuits on NoteType.Note. A body that *looks* like a checklist is not
            // one until the note says it is.
            (await read.Tasks.AnyAsync(t => t.NoteId == note.Id, Ct)).Should().BeFalse();

            // Tags are unconditional, though, so this same note still indexes both of them.
            (await read.NoteTags.CountAsync(t => t.NoteId == note.Id, Ct)).Should().Be(2);
        }

        [Fact]
        public async Task SaveChangesAsync_OnUpdate_RebuildsTasksRatherThanAppending()
        {
            using var factory = new InMemoryDbContextFactory();
            var note = NewNote(ChecklistBody, NoteType.Checklist);

            using (var write = factory.CreateDbContext())
            {
                write.Notes.Add(note);
                await write.SaveChangesAsync(Ct);
            }

            using (var edit = factory.CreateDbContext())
            {
                var tracked = await edit.Notes.SingleAsync(n => n.Id == note.Id, Ct);
                tracked.Body = "- [ ] the only survivor @2026-12-25";
                await edit.SaveChangesAsync(Ct);
            }

            using var read = factory.CreateDbContext();
            var tasks = await read.Tasks.Where(t => t.NoteId == note.Id).ToListAsync(Ct);

            tasks.Should().ContainSingle();
            tasks[0].Text.Should().Be("the only survivor");
            tasks[0].Due.Should().Be("2026-12-25");
        }

        [Fact]
        public async Task SaveChangesAsync_WhenEveryDueDateIsRemoved_LeavesNoTasks()
        {
            using var factory = new InMemoryDbContextFactory();
            var note = NewNote("- [ ] dated @2026-09-20", NoteType.Checklist);

            using (var write = factory.CreateDbContext())
            {
                write.Notes.Add(note);
                await write.SaveChangesAsync(Ct);
            }

            using (var edit = factory.CreateDbContext())
            {
                var tracked = await edit.Notes.SingleAsync(n => n.Id == note.Id, Ct);
                tracked.Body = "- [ ] no longer dated";
                await edit.SaveChangesAsync(Ct);
            }

            using var read = factory.CreateDbContext();

            // The delete half of "remove then re-add" has to survive an empty re-add. A derivation
            // that only ever inserted would strand the old task here and it would never expire.
            (await read.Tasks.AnyAsync(t => t.NoteId == note.Id, Ct)).Should().BeFalse();
        }

        #endregion

        #region Outbox

        [Fact]
        public async Task SaveChangesAsync_OnInsert_EnqueuesOutboxRowPinnedToNullRev()
        {
            using var factory = new InMemoryDbContextFactory();
            var note = NewNote("a brand new note, never synced");

            using (var write = factory.CreateDbContext())
            {
                write.Notes.Add(note);
                await write.SaveChangesAsync(Ct);
            }

            using var read = factory.CreateDbContext();
            var outbox = await read.Outboxes.SingleAsync(o => o.NoteId == note.Id, Ct);

            // A note the server has never seen has no base revision to merge against.
            outbox.BaseRev.Should().BeNull();
            outbox.QueuedAt.Should().BeCloseTo(DateTimeOffset.UtcNow, TimeSpan.FromMinutes(1));
        }

        [Fact]
        public async Task SaveChangesAsync_AcrossThreeEdits_CoalescesToOneOutboxRow()
        {
            using var factory = new InMemoryDbContextFactory();
            var note = NewNote("edit one #work", rev: 5);

            using (var write = factory.CreateDbContext())
            {
                write.Notes.Add(note);
                await write.SaveChangesAsync(Ct);
            }

            foreach (var body in new[] { "edit two #work", "edit three #work" })
            {
                using var edit = factory.CreateDbContext();
                var tracked = await edit.Notes.SingleAsync(n => n.Id == note.Id, Ct);
                tracked.Body = body;
                await edit.SaveChangesAsync(Ct);
            }

            using var read = factory.CreateDbContext();
            var rows = await read.Outboxes.Where(o => o.NoteId == note.Id).ToListAsync(Ct);

            // One row per note, not one per edit. The sync engine pushes current state, so three
            // queued copies of one note would be three identical pushes — and autosave means an
            // afternoon of typing produces hundreds of them.
            rows.Should().ContainSingle();
            rows[0].BaseRev.Should().Be(5);
        }

        [Fact]
        public async Task SaveChangesAsync_AfterRevMovesOn_LeavesOutboxPinnedToFirstDirtyRev()
        {
            using var factory = new InMemoryDbContextFactory();
            var note = NewNote("first local edit", rev: 5);

            using (var write = factory.CreateDbContext())
            {
                write.Notes.Add(note);
                await write.SaveChangesAsync(Ct);
            }

            using (var edit = factory.CreateDbContext())
            {
                var tracked = await edit.Notes.SingleAsync(n => n.Id == note.Id, Ct);
                tracked.Rev = 9;
                tracked.Body = "second local edit";
                await edit.SaveChangesAsync(Ct);
            }

            using var read = factory.CreateDbContext();
            var outbox = await read.Outboxes.SingleAsync(o => o.NoteId == note.Id, Ct);

            // BaseRev answers "what did the server and I last agree on", so it has to stay at the
            // revision the FIRST unsynced edit forked from. Re-stamping it to 9 would tell the merge
            // that rev 9 was the common ancestor when it never was — which is how a real conflict
            // resolves as a clean overwrite and a note quietly loses content.
            outbox.BaseRev.Should().Be(5);
        }

        [Fact]
        public async Task SaveChangesAsync_WritesNoteTagsAndOutboxInOneTransaction()
        {
            using var factory = new InMemoryDbContextFactory();
            var note = NewNote("- [ ] a task @2026-09-20 #work", NoteType.Checklist);

            using (var write = factory.CreateDbContext())
            {
                write.Notes.Add(note);

                // A tag hung off a note id that does not exist. The change tracker is happy with it
                // (it has a valid composite key), so the failure happens where it needs to for this
                // test to mean anything: in SQLite, after derive-on-save has already queued the
                // note's own tags, tasks and outbox row into the same batch.
                write.NoteTags.Add(new NoteTag { NoteId = Guid.NewGuid(), Tag = "orphan" });

                await Assert.ThrowsAsync<DbUpdateException>(() => write.SaveChangesAsync(Ct));
            }

            using var read = factory.CreateDbContext();

            // Nothing at all landed. If the outbox were written outside the transaction, the sync
            // engine would hold a queued push for a note that does not exist — the exact failure
            // the same-transaction rule exists to prevent.
            (await read.Notes.AnyAsync(n => n.Id == note.Id, Ct)).Should().BeFalse();
            (await read.NoteTags.AnyAsync(t => t.NoteId == note.Id, Ct)).Should().BeFalse();
            (await read.Tasks.AnyAsync(t => t.NoteId == note.Id, Ct)).Should().BeFalse();
            (await read.Outboxes.AnyAsync(o => o.NoteId == note.Id, Ct)).Should().BeFalse();
        }

        #endregion

        #region Synchronous entry points

        [Fact]
        public void SaveChanges_Throws()
        {
            using var factory = new InMemoryDbContextFactory();
            using var context = factory.CreateDbContext();
            context.Notes.Add(NewNote("anything"));

            // Derivation is async (it queries for the rows it replaces), so there is no correct
            // synchronous path. Throwing beats silently saving a note with no tags, no tasks and no
            // outbox row, which is what the inherited implementation would do.
            var act = () => context.SaveChanges();

            act.Should().Throw<InvalidOperationException>()
               .WithMessage("*SaveChangesAsync*");
        }

        #endregion
    }
}
