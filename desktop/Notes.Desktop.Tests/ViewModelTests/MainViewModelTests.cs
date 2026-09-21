using FluentAssertions;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using Moq;
using Notes.Desktop.EntityFramework.Model;
using Notes.Desktop.Helpers;
using Notes.Desktop.Tests.Helpers;
using Notes.Desktop.ViewModels;

namespace Notes.Desktop.Tests.ViewModelTests
{
    public class MainViewModelTests
    {
        /// <summary>
        /// The editor is a collaborator here, not the subject — these tests assert that
        /// <see cref="MainWindowViewModel"/> composes what it is given. A mocked factory and a null
        /// logger keep that from dragging a database in.
        /// </summary>
        private static NoteEditorViewModel NewEditorViewModel() =>
            new(new StubDbContextFactory(), NullLogger<NoteEditorViewModel>.Instance);

        /// <summary>
        /// The list is a collaborator here too. It takes a context factory as of T056, and the
        /// stub keeps these composition tests from reaching a database they do not care about.
        /// </summary>
        private static NoteListViewModel NewListViewModel() =>
            new(new StubDbContextFactory(), NullLogger<NoteListViewModel>.Instance);

        private static MainWindowViewModel NewViewModel(IRegistryAccess registry) =>
            new(NewEditorViewModel(),
                NewListViewModel(),
                registry,
                NullLogger<MainWindowViewModel>.Instance);

        #region Tests
        [Fact()]
        public void MainViewModelTests_Constructor_InitializesNoteEditorViewModel()
        {
            // Arrange & Act
            var viewModel = NewViewModel(new RegistryAccessHelper());
            // Assert
            viewModel.Editor.Should().NotBeNull();
        }

        [Fact()]
        public void MainViewModelTests_Constructor_InitializesNoteListViewModel()
        {
            // Arrange & Act
            var viewModel = NewViewModel(new RegistryAccessHelper());
            // Assert
            viewModel.List.Should().NotBeNull();
        }

        [Fact()]
        public void MainViewModelTests_Constructor_DefaultsListColumnWidthTo200()
        {
            // Arrange & Act
            var viewModel = NewViewModel(new Mock<IRegistryAccess>().Object);
            // Assert
            viewModel.ListColumnWidth.Should().Be("200");
        }

        [Fact()]
        public void MainViewModelTests_Constructor_LoadsListColumnWidthFromRegistry()
        {
            // Arrange
            var mockRegistryAccess = new Mock<IRegistryAccess>();
            mockRegistryAccess.Setup(m => m.GetRegistryString("ListColumnWidth", "200")).Returns("250");
            // Act
            var viewModel = NewViewModel(mockRegistryAccess.Object);
            // Assert
            viewModel.ListColumnWidth.Should().Be("250");
        }

        [Fact()]
        public void MainViewModelTests_Constructor_DoesNotNewNoteEditorViewModelOrNoteListViewModel()
        {
            // Arrange
            var mockRegistryAccess = new Mock<IRegistryAccess>();
            var editor = NewEditorViewModel();
            var list = NewListViewModel();
            // Act
            var viewModel = new MainWindowViewModel(
                editor, list, mockRegistryAccess.Object, NullLogger<MainWindowViewModel>.Instance);
            // Assert — reference identity is the whole point: the shell must use what it was given,
            // not construct its own.
            viewModel.Editor.Should().BeSameAs(editor);
            viewModel.List.Should().BeSameAs(list);
        }

        [Fact()]
        public void MainViewModelTests_ListColumnWidth_RaisesPropertyChangedEvent()
        {
            // Arrange
            var mockRegistryAccess = new Mock<IRegistryAccess>();
            var viewModel = NewViewModel(mockRegistryAccess.Object);
            bool eventRaised = false;
            viewModel.PropertyChanged += (sender, e) =>
            {
                if (e.PropertyName == nameof(MainWindowViewModel.ListColumnWidth))
                {
                    eventRaised = true;
                }
            };
            // Act
            viewModel.ListColumnWidth = "300";
            // Assert
            eventRaised.Should().BeTrue();
        }

        [Fact()]
        public void MainViewModelTests_BeforeShutdown_SavesListColumnWidthToRegistry()
        {
            // Arrange
            var mockRegistryAccess = new Mock<IRegistryAccess>();
            var viewModel = NewViewModel(mockRegistryAccess.Object);
            viewModel.ListColumnWidth = "300";
            // Act
            viewModel.BeforeShutdown();
            // Assert
            mockRegistryAccess.Verify(m => m.SetRegistryString("ListColumnWidth", "300"), Times.Once);
        }
        #endregion

        #region The list-to-editor seam

        /// <summary>
        /// Builds a shell over one real SQLite database, so the editor and the list share a store.
        /// These tests are about the wiring between the two panes, which is the only place that
        /// connection exists — neither ViewModel references the other.
        /// </summary>
        private static MainWindowViewModel NewShell(InMemoryDbContextFactory factory) =>
            new(new NoteEditorViewModel(factory, NullLogger<NoteEditorViewModel>.Instance),
                new NoteListViewModel(factory, NullLogger<NoteListViewModel>.Instance),
                new Mock<IRegistryAccess>().Object,
                NullLogger<MainWindowViewModel>.Instance);

        private static async Task<Guid> GivenANote(
            InMemoryDbContextFactory factory, string title, string body)
        {
            var id = Guid.NewGuid();
            using var write = factory.CreateDbContext();
            write.Notes.Add(new Note
            {
                Id = id,
                Title = title,
                Body = body,
                CreatedAt = DateTimeOffset.UtcNow,
                UpdatedAt = DateTimeOffset.UtcNow,
                SourceDevice = "Desktop"
            });
            await write.SaveChangesAsync(TestContext.Current.CancellationToken);
            return id;
        }

        /// <summary>
        /// The shell reacts to a selection change from an <c>async void</c> event handler, so the
        /// work is in flight when the assignment returns. Polling beats a fixed delay: it passes as
        /// soon as the load lands rather than always costing the worst case, and it fails with a
        /// readable timeout instead of an intermittent assertion.
        /// </summary>
        private static async Task WaitUntil(Func<bool> condition, string because)
        {
            for (var attempt = 0; attempt < 100; attempt++)
            {
                if (condition()) return;
                await Task.Delay(10, TestContext.Current.CancellationToken);
            }
            throw new Xunit.Sdk.XunitException($"Timed out waiting: {because}");
        }

        [Fact]
        public async Task SelectingANote_OpensItInTheEditor()
        {
            using var factory = new InMemoryDbContextFactory();
            var id = await GivenANote(factory, "Standup", "the body of the note");
            var shell = NewShell(factory);

            shell.List.SelectedNote = new NoteSummary
            {
                Id = id,
                Title = "Standup",
                Snippet = "the body of the note",
                UpdatedDisplay = "09:00"
            };

            await WaitUntil(() => shell.Editor.Body.Length > 0, "the editor to load the note");

            // The whole list-to-editor path, and the only place the two panes meet. Neither
            // ViewModel names the other; the shell watches a property and calls a command.
            shell.Editor.Title.Should().Be("Standup");
            shell.Editor.Body.Should().Be("the body of the note");
        }

        [Fact]
        public async Task SelectingNothing_LeavesTheEditorAlone()
        {
            using var factory = new InMemoryDbContextFactory();
            var id = await GivenANote(factory, "Standup", "the body of the note");
            var shell = NewShell(factory);

            shell.List.SelectedNote = new NoteSummary
            {
                Id = id,
                Title = "Standup",
                Snippet = "x",
                UpdatedDisplay = "09:00"
            };
            await WaitUntil(() => shell.Editor.Body.Length > 0, "the editor to load the note");

            shell.List.SelectedNote = null;
            await Task.Delay(50, TestContext.Current.CancellationToken);

            // A refresh clears the collection, which nulls the selection. That is list bookkeeping,
            // not a user deselecting — treating it as one would blank the editor every time an
            // autosave refreshed the list.
            shell.Editor.Title.Should().Be("Standup");
            shell.Editor.Body.Should().Be("the body of the note");
        }

        [Fact]
        public async Task ReselectingTheOpenNote_DoesNotReloadOverUnsavedTyping()
        {
            using var factory = new InMemoryDbContextFactory();
            var id = await GivenANote(factory, "Standup", "the body of the note");
            var shell = NewShell(factory);

            NoteSummary Summary() => new()
            {
                Id = id,
                Title = "Standup",
                Snippet = "x",
                UpdatedDisplay = "09:00"
            };

            shell.List.SelectedNote = Summary();
            await WaitUntil(() => shell.Editor.Body.Length > 0, "the editor to load the note");

            shell.Editor.Body = "typed, and not yet autosaved";

            // A fresh NoteSummary instance with the same id — exactly what a refresh produces when
            // it rebuilds the collection and restores the selection.
            shell.List.SelectedNote = Summary();
            await Task.Delay(50, TestContext.Current.CancellationToken);

            // Guarding on the id matters because LoadAsync flushes and overwrites. Without it, every
            // autosave-driven refresh would reload the note mid-sentence and the caret would jump.
            shell.Editor.Body.Should().Be("typed, and not yet autosaved");
        }

        #endregion

        #region New note

        [Fact]
        public async Task NewNote_GivesTheEditorABlankDraftWithAFreshIdentity()
        {
            using var factory = new InMemoryDbContextFactory();
            var id = await GivenANote(factory, "Standup", "the body of the note");
            var shell = NewShell(factory);

            shell.List.SelectedNote = new NoteSummary
            {
                Id = id,
                Title = "Standup",
                Snippet = "x",
                UpdatedDisplay = "09:00"
            };
            await WaitUntil(() => shell.Editor.Body.Length > 0, "the editor to load the note");

            await shell.NewNoteCommand.ExecuteAsync(null);

            shell.Editor.Title.Should().BeEmpty();
            shell.Editor.Body.Should().BeEmpty();
        }

        [Fact]
        public async Task NewNote_FlushesTheNoteThatWasOpen()
        {
            using var factory = new InMemoryDbContextFactory();
            var id = await GivenANote(factory, "Standup", "the body of the note");
            var shell = NewShell(factory);

            shell.List.SelectedNote = new NoteSummary
            {
                Id = id,
                Title = "Standup",
                Snippet = "x",
                UpdatedDisplay = "09:00"
            };
            await WaitUntil(() => shell.Editor.Body.Length > 0, "the editor to load the note");

            shell.Editor.Body = "edited, and not yet autosaved";
            await shell.NewNoteCommand.ExecuteAsync(null);

            using var read = factory.CreateDbContext();
            var note = await read.Notes.SingleAsync(n => n.Id == id, TestContext.Current.CancellationToken);

            // Starting a new note is the same kind of deadline as switching to another one: the
            // outgoing note's in-memory edits have nowhere else to go.
            note.Body.Should().Be("edited, and not yet autosaved");
        }

        [Fact]
        public async Task NewNote_LeavesTheEditorClean()
        {
            using var factory = new InMemoryDbContextFactory();
            var id = await GivenANote(factory, "Standup", "the body of the note");
            var shell = NewShell(factory);

            shell.List.SelectedNote = new NoteSummary
            {
                Id = id,
                Title = "Standup",
                Snippet = "x",
                UpdatedDisplay = "09:00"
            };
            await WaitUntil(() => shell.Editor.Body.Length > 0, "the editor to load the note");

            await shell.NewNoteCommand.ExecuteAsync(null);

            // A blank draft nobody has typed into has nothing to save. IsDirty compares against the
            // last-persisted snapshot, so blanking Title and Body without also resetting that
            // snapshot leaves the new note reading as modified from birth — which T062's pending
            // indicator would show on an untouched note.
            shell.Editor.IsDirty.Should().BeFalse();
        }

        [Fact]
        public async Task SavingInTheEditor_RefreshesTheList()
        {
            using var factory = new InMemoryDbContextFactory();
            var shell = NewShell(factory);
            await shell.List.InitializeCommand.ExecuteAsync(null);
            shell.List.Notes.Should().BeEmpty();

            shell.Editor.Title = "Brand new";
            shell.Editor.Body = "written from scratch";
            await shell.Editor.SaveCommand.ExecuteAsync(null);

            await WaitUntil(() => shell.List.Notes.Count == 1, "the list to pick up the saved note");

            // IsDirty going true -> false happens at exactly one moment: a successful save. The
            // shell uses that as the signal to re-read, so the list stays current with no polling,
            // no new event, and no editor that knows a list exists.
            shell.List.Notes.Single().Title.Should().Be("Brand new");
        }

        #endregion
    }
}
