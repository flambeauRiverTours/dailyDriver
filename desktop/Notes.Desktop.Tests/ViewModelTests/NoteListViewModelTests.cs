using FluentAssertions;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using Notes.Desktop.EntityFramework.Model;
using Notes.Desktop.Tests.Helpers;
using Notes.Desktop.ViewModels;

namespace Notes.Desktop.Tests.ViewModelTests
{
    /// <summary>
    /// Covers <see cref="NoteListViewModel"/> against a real SQLite database, because the whole
    /// subject is a query — what it selects, what it excludes, and what order it returns.
    ///
    /// Tags are never inserted by hand here. <c>AppDbContext.SaveChangesAsync</c> derives them from
    /// the body on every write, so seeding a note whose body contains <c>#work</c> is the only
    /// honest way to get a tag: a hand-inserted row disagreeing with its body would be wiped on the
    /// next save anyway.
    /// </summary>
    public class NoteListViewModelTests
    {
        private static CancellationToken Ct => TestContext.Current.CancellationToken;

        private static NoteListViewModel NewViewModel(InMemoryDbContextFactory factory) =>
            new(factory, NullLogger<NoteListViewModel>.Instance);

        private static Task Refresh(NoteListViewModel viewModel) =>
            viewModel.RefreshCommand.ExecuteAsync(null);

        private static Task Initialize(NoteListViewModel viewModel) =>
            viewModel.InitializeCommand.ExecuteAsync(null);

        /// <summary>
        /// Seeds one note. <paramref name="minutesAgo"/> drives <c>UpdatedAt</c> so ordering tests
        /// have something deterministic to assert — wall-clock writes in a loop can land in the
        /// same millisecond.
        /// </summary>
        private static async Task<Guid> GivenANote(
            InMemoryDbContextFactory factory,
            string title,
            string body,
            int minutesAgo = 0,
            bool deleted = false)
        {
            var id = Guid.NewGuid();
            var stamp = DateTimeOffset.UtcNow.AddMinutes(-minutesAgo);

            using var write = factory.CreateDbContext();
            write.Notes.Add(new Note
            {
                Id = id,
                Title = title,
                Body = body,
                CreatedAt = stamp,
                UpdatedAt = stamp,
                Deleted = deleted,
                SourceDevice = "Desktop"
            });
            await write.SaveChangesAsync(Ct);
            return id;
        }

        #region Ordering and exclusion

        [Fact]
        public async Task Refresh_ListsNotesNewestFirst()
        {
            using var factory = new InMemoryDbContextFactory();
            await GivenANote(factory, "oldest", "body", minutesAgo: 30);
            await GivenANote(factory, "newest", "body", minutesAgo: 1);
            await GivenANote(factory, "middle", "body", minutesAgo: 10);

            var viewModel = NewViewModel(factory);
            await Refresh(viewModel);

            // UpdatedAt descending is what makes the list read as "what I was just working on".
            viewModel.Notes.Select(n => n.Title)
                     .Should().Equal("newest", "middle", "oldest");
        }

        [Fact]
        public async Task Refresh_ExcludesSoftDeletedNotes()
        {
            using var factory = new InMemoryDbContextFactory();
            await GivenANote(factory, "kept", "body");
            await GivenANote(factory, "binned", "body", deleted: true);

            var viewModel = NewViewModel(factory);
            await Refresh(viewModel);

            // A soft-deleted note is still a row — it has to survive as a tombstone for sync. The
            // list is the layer that has to remember that, and forgetting shows deleted notes.
            viewModel.Notes.Should().ContainSingle();
            viewModel.Notes[0].Title.Should().Be("kept");
        }

        #endregion

        #region Projection

        [Fact]
        public async Task Refresh_ProjectsTitleAndASingleLineSnippet()
        {
            using var factory = new InMemoryDbContextFactory();
            await GivenANote(factory, "Standup", "first line\nsecond line\n\n  third  ");

            var viewModel = NewViewModel(factory);
            await Refresh(viewModel);

            var row = viewModel.Notes.Single();
            row.Title.Should().Be("Standup");

            // The snippet is one line: a list row is a fixed height, so a raw body would render as
            // its first line only and hide whatever follows.
            row.Snippet.Should().Be("first line second line third");
            row.Snippet.Should().NotContain("\n");
        }

        [Fact]
        public async Task Refresh_CarriesTheNoteIdSoSelectionCanOpenIt()
        {
            using var factory = new InMemoryDbContextFactory();
            var id = await GivenANote(factory, "Standup", "body");

            var viewModel = NewViewModel(factory);
            await Refresh(viewModel);

            // The id is the only part of a summary that is load-bearing rather than cosmetic — it
            // is what the shell hands to the editor's LoadCommand.
            viewModel.Notes.Single().Id.Should().Be(id);
        }

        [Fact]
        public async Task Refresh_FormatsUpdatedAtForDisplay()
        {
            using var factory = new InMemoryDbContextFactory();
            await GivenANote(factory, "Standup", "body");

            var viewModel = NewViewModel(factory);
            await Refresh(viewModel);

            // Saved moments ago, so it renders as a time rather than a date. Asserting the shape
            // rather than the exact string keeps this from failing at midnight.
            viewModel.Notes.Single().UpdatedDisplay.Should().MatchRegex(@"^\d{2}:\d{2}$");
        }

        #endregion

        #region Empty states

        [Fact]
        public async Task Refresh_OnAnEmptyStore_ReportsNoNotesRatherThanNoMatches()
        {
            using var factory = new InMemoryDbContextFactory();
            var viewModel = NewViewModel(factory);

            await Refresh(viewModel);

            // Two different problems needing two different messages: "you have no notes" invites
            // writing one, "nothing matches" invites clearing the filter.
            viewModel.HasNoNotes.Should().BeTrue();
            viewModel.HasNoMatches.Should().BeFalse();
        }

        [Fact]
        public async Task Refresh_WithAFilterThatMatchesNothing_ReportsNoMatches()
        {
            using var factory = new InMemoryDbContextFactory();
            await GivenANote(factory, "Standup", "notes #work");
            await GivenANote(factory, "Shopping", "milk #home");

            var viewModel = NewViewModel(factory);
            await Initialize(viewModel);

            // Select both, then delete every note that could match, so the filter is live and the
            // result is empty.
            using (var write = factory.CreateDbContext())
            {
                write.Notes.RemoveRange(await write.Notes.ToListAsync(Ct));
                await write.SaveChangesAsync(Ct);
            }

            viewModel.Tags.First(t => t.Tag == "work").IsSelected = true;
            await Refresh(viewModel);

            viewModel.HasNoMatches.Should().BeTrue();
            viewModel.HasNoNotes.Should().BeFalse();
        }

        #endregion

        #region Tag filter

        [Fact]
        public async Task Initialize_BuildsADistinctSortedTagList()
        {
            using var factory = new InMemoryDbContextFactory();
            await GivenANote(factory, "one", "a #work note");
            await GivenANote(factory, "two", "another #work note, also #home");
            await GivenANote(factory, "three", "and #admin");

            var viewModel = NewViewModel(factory);
            await Initialize(viewModel);

            // "work" appears in two notes and must produce one chip, not two.
            viewModel.Tags.Select(t => t.Tag).Should().Equal("admin", "home", "work");
            viewModel.Tags.Should().OnlyContain(t => !t.IsSelected);
        }

        [Fact]
        public async Task Refresh_WithOneTagSelected_ShowsOnlyThatTagsNotes()
        {
            using var factory = new InMemoryDbContextFactory();
            await GivenANote(factory, "Standup", "notes #work");
            await GivenANote(factory, "Shopping", "milk #home");

            var viewModel = NewViewModel(factory);
            await Initialize(viewModel);

            viewModel.Tags.First(t => t.Tag == "work").IsSelected = true;
            await Refresh(viewModel);

            viewModel.Notes.Should().ContainSingle();
            viewModel.Notes[0].Title.Should().Be("Standup");
        }

        [Fact]
        public async Task Refresh_WithTwoTagsSelected_ShowsNotesMatchingEither()
        {
            using var factory = new InMemoryDbContextFactory();
            await GivenANote(factory, "Standup", "notes #work", minutesAgo: 1);
            await GivenANote(factory, "Shopping", "milk #home", minutesAgo: 2);
            await GivenANote(factory, "Taxes", "forms #admin", minutesAgo: 3);

            var viewModel = NewViewModel(factory);
            await Initialize(viewModel);

            viewModel.Tags.First(t => t.Tag == "work").IsSelected = true;
            viewModel.Tags.First(t => t.Tag == "home").IsSelected = true;
            await Refresh(viewModel);

            // OR, not AND — this pins a decision rather than an implementation detail. Two selected
            // chips widen the result; they do not narrow it to notes carrying both. If that call is
            // ever revisited, this test is where it is written down.
            viewModel.Notes.Select(n => n.Title).Should().Equal("Standup", "Shopping");
        }

        [Fact]
        public async Task Initialize_KeepsAlreadySelectedTagsSelected()
        {
            using var factory = new InMemoryDbContextFactory();
            await GivenANote(factory, "Standup", "notes #work");

            var viewModel = NewViewModel(factory);
            await Initialize(viewModel);
            viewModel.Tags.First(t => t.Tag == "work").IsSelected = true;

            await GivenANote(factory, "Shopping", "milk #home");
            await Initialize(viewModel);

            // Initialize rebuilds the chip objects, so the selection has to be carried across by
            // tag name. Losing it would silently widen the list every time a save reloaded tags —
            // and a save reloads tags on every autosave.
            viewModel.Tags.Should().HaveCount(2);
            viewModel.Tags.First(t => t.Tag == "work").IsSelected.Should().BeTrue();
            viewModel.Tags.First(t => t.Tag == "home").IsSelected.Should().BeFalse();
        }

        #endregion

        #region Selection across a refresh

        [Fact]
        public async Task Refresh_KeepsTheSelectedNoteSelected()
        {
            using var factory = new InMemoryDbContextFactory();
            await GivenANote(factory, "Standup", "body", minutesAgo: 1);
            var idB = await GivenANote(factory, "Shopping", "body", minutesAgo: 2);

            var viewModel = NewViewModel(factory);
            await Refresh(viewModel);
            viewModel.SelectedNote = viewModel.Notes.Single(n => n.Id == idB);

            await Refresh(viewModel);

            // Refresh clears the collection, which nulls SelectedNote. Restoring it matters because
            // the shell treats a selection change as "open this note" — without the restore, every
            // autosave-triggered refresh would deselect, and with a naive shell would close the
            // note the user is typing in.
            viewModel.SelectedNote.Should().NotBeNull();
            viewModel.SelectedNote!.Id.Should().Be(idB);
        }

        [Fact]
        public async Task Refresh_WhenTheSelectedNoteFallsOutOfTheFilter_ClearsSelection()
        {
            using var factory = new InMemoryDbContextFactory();
            await GivenANote(factory, "Standup", "notes #work");
            var idHome = await GivenANote(factory, "Shopping", "milk #home");

            var viewModel = NewViewModel(factory);
            await Initialize(viewModel);
            viewModel.SelectedNote = viewModel.Notes.Single(n => n.Id == idHome);

            viewModel.Tags.First(t => t.Tag == "work").IsSelected = true;
            await Refresh(viewModel);

            // Null rather than a stale summary for a row no longer on screen. The shell ignores
            // null selections, so the editor keeps showing the note — which is the right outcome:
            // filtering the list should not close what is open.
            viewModel.SelectedNote.Should().BeNull();
        }

        #endregion
    }
}
