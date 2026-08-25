using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.Win32;
using Notes.Desktop.Helpers;

namespace Notes.Desktop.ViewModels
{
    public partial class MainWindowViewModel: ObservableObject
    {
        /// <summary>
        /// Initializes a new instance of the MainWindowViewModel class with the specified note editor view model, note list view model, and registry helper.
        /// </summary>
        /// <param name="noteEditorViewModel">An instance of the NoteEditorViewModel class</param>
        /// <param name="noteListViewModel">An instance of the NoteListViewModel class</param>
        /// <param name="registryHelper">An instance of the IRegistryAccess interface</param>
        public MainWindowViewModel(NoteEditorViewModel noteEditorViewModel, NoteListViewModel noteListViewModel, IRegistryAccess registryHelper)
        {
            Editor = noteEditorViewModel;
            List = noteListViewModel;
            RegistryHelper = registryHelper;
            SetListColumnWidth();
        }

        /// <summary>
        /// Handles tasks before shutdown
        /// </summary>
        public void BeforeShutdown()
        {
            WriteListColumnsWidth();
        }

        /// <summary>
        /// Gets or sets the view model for the note editor, which is responsible for managing the content of the currently selected note.
        /// </summary>
        [ObservableProperty]
        public partial NoteEditorViewModel Editor { get; set; }

        /// <summary>
        /// Gets or sets the view model for the note list, which is responsible for managing the list of notes displayed in the main window.
        /// </summary>
        [ObservableProperty]
        public partial NoteListViewModel List { get; set; }

        /// <summary>
        /// Gets or sets the width of the list column, which is stored in the Windows registry. If the value is not set or invalid, it defaults to 200.
        /// </summary>
        [ObservableProperty]
        public partial string ListColumnWidth { get; set; } = "";

        /// <summary>
        /// The registry helper used to read and write settings to the Windows registry
        /// </summary>
        private IRegistryAccess RegistryHelper { get; }

        /// <summary>
        /// Sets the list column width from the registry, or defaults to 200 if not set or invalid
        /// </summary>
        private void SetListColumnWidth()
        {
            ListColumnWidth = RegistryHelper.GetRegistryString(LIST_COLUMN_WIDTH_REG_KEY, "200");
            if(!int.TryParse(ListColumnWidth, out int result))
            {
                ListColumnWidth = "200";
            }
        }

        /// <summary>
        /// Writes the current list column width to the registry
        /// </summary>
        private void WriteListColumnsWidth()
        {
            RegistryHelper.SetRegistryString(LIST_COLUMN_WIDTH_REG_KEY, ListColumnWidth);
        }

        /// <summary>
        /// The registry key used to store the list column width setting
        /// </summary>
        private const string LIST_COLUMN_WIDTH_REG_KEY = "ListColumnWidth";
    }
}
