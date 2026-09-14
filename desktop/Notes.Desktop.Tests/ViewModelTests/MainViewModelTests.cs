using FluentAssertions;
using Moq;
using Notes.Desktop.Helpers;
using Microsoft.Extensions.Logging.Abstractions;
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

        #region Tests
        [Fact()]
        public void MainViewModelTests_Constructor_InitializesNoteEditorViewModel()
        {
            // Arrange & Act
            var viewModel = new MainWindowViewModel(NewEditorViewModel(), new NoteListViewModel(), new RegistryAccessHelper());
            // Assert
            viewModel.Editor.Should().NotBeNull();
        }

        [Fact()]
        public void MainViewModelTests_Constructor_InitializesNoteListViewModel()
        {
            // Arrange & Act
            var viewModel = new MainWindowViewModel(NewEditorViewModel(), new NoteListViewModel(), new RegistryAccessHelper());
            // Assert
            viewModel.List.Should().NotBeNull();
        }

        [Fact()]
        public void MainViewModelTests_Constructor_DefaultsListColumnWidthTo200()
        {
            // Arrange & Act
            var viewModel = new MainWindowViewModel(NewEditorViewModel(), new NoteListViewModel(), new Mock<IRegistryAccess>().Object);
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
            var viewModel = new MainWindowViewModel(NewEditorViewModel(), new NoteListViewModel(), mockRegistryAccess.Object);
            // Assert
            viewModel.ListColumnWidth.Should().Be("250");
        }

        [Fact()]
        public void MainViewModelTests_Constructor_DoesNotNewNoteEditorViewModelOrNoteListViewModel()
        {
            // Arrange
            var mockRegistryAccess = new Mock<IRegistryAccess>();
            var editor = NewEditorViewModel();
            var list = new NoteListViewModel();
            // Act
            var viewModel = new MainWindowViewModel(editor, list, mockRegistryAccess.Object);
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
            var viewModel = new MainWindowViewModel(NewEditorViewModel(), new NoteListViewModel(), mockRegistryAccess.Object);
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
            var viewModel = new MainWindowViewModel(NewEditorViewModel(), new NoteListViewModel(), mockRegistryAccess.Object);
            viewModel.ListColumnWidth = "300";
            // Act
            viewModel.BeforeShutdown();
            // Assert
            mockRegistryAccess.Verify(m => m.SetRegistryString("ListColumnWidth", "300"), Times.Once);
        }
        #endregion
    }
}
