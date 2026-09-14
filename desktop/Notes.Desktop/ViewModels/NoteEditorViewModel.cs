using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using Notes.Desktop.EntityFramework;
using Notes.Desktop.EntityFramework.Model;

namespace Notes.Desktop.ViewModels
{
    public partial class NoteEditorViewModel: ObservableObject
    {
        /// <summary>
        /// Initialized the VM
        /// </summary>
        /// <param name="dbContextFactory">DB context factory</param>
        /// <param name="logger">Logger instance</param>
        internal NoteEditorViewModel(IDbContextFactory<AppDbContext> dbContextFactory, ILogger<NoteEditorViewModel> logger)
        {
            _dbContextFactory = dbContextFactory;
            _logger = logger;
        }

        #region DIs
        private IDbContextFactory<AppDbContext> _dbContextFactory;
        private ILogger<NoteEditorViewModel> _logger;
        #endregion
        #region Observable Properties
        [ObservableProperty]
        public partial string Title { get; set; }
        [ObservableProperty]
        public partial string Body { get; set; }
        [ObservableProperty]
        public partial bool IsDirty { get; set; }
        #endregion

        #region Non-Observable Note Properties
        private Guid id = Guid.NewGuid();
        private DateTimeOffset createdAt = DateTimeOffset.UtcNow;

        private bool deleted = false;

        private long? rev = null;
        private Notes.Shared.Model.NoteType noteType = Shared.Model.NoteType.Note;
        private string sourceDevice = "Desktop";
        #endregion

        #region Relay Commands
        /// <summary>
        /// Loads a given note Id
        /// </summary>
        /// <param name="noteId">Note id to load</param>
        /// <param name="ct">Cancellation tokey</param>
        /// <returns>Task for loading</returns>
        /// <exception cref="InvalidOperationException">Thrown if an invalid note id is passed</exception>
        [RelayCommand]
        async Task LoadAsync(Guid noteId, CancellationToken ct)
        {
            using(var dbContext = await _dbContextFactory.CreateDbContextAsync(ct))
            {
                var note = await dbContext.Notes.AsNoTracking().FirstOrDefaultAsync(n => n.Id == noteId, ct);
                if (note == null)
                {
                    throw new InvalidOperationException("Invalid note id passed to LoadAsync");
                }
                NoteToDraft(note);
            }
        }

        /// <summary>
        /// Saves the current note
        /// </summary>
        /// <param name="ct">Cancellation token</param>
        /// <returns>Task to be awaited</returns>
        [RelayCommand]
        async Task SaveAsync(CancellationToken ct)
        {
            if(string.IsNullOrWhiteSpace(Body)) { return; }
            if(!IsDirty) { return; }
            using (var dbContext = await _dbContextFactory.CreateDbContextAsync(ct))
            {
                var note = await dbContext.Notes.FirstOrDefaultAsync(n => n.Id == id, ct);
                if (note == null) //net-new note
                {
                    dbContext.Notes.Add(DraftToNote());
                }
                else
                {
                    note.UpdatedAt = DateTimeOffset.UtcNow;
                    note.Title = GetFormattedTitle();
                    note.Body = GetFormattedBodyString();
                }
                IsDirty = false;
                await dbContext.SaveChangesAsync(ct);
            }
        }
        #endregion

        #region Save/Load Helpers
        /// <summary>
        /// Converts a draft note represented by the various properties on this object to a Note object
        /// </summary>
        /// <returns>Note object</returns>
        private Note DraftToNote()
        {
            return new Note()
            {
                Title = GetFormattedTitle(),
                Body = GetFormattedBodyString(),
                CreatedAt = createdAt,
                Id = id,
                UpdatedAt = DateTimeOffset.UtcNow,
                Deleted = deleted,
                Rev = rev,
                Type = noteType,
                SourceDevice = sourceDevice
            };
        }

        /// <summary>
        /// Pulls relevant properties from a note object to be applied to a draft
        /// </summary>
        /// <param name="note">Note to apply</param>
        private void NoteToDraft(Note note)
        {
            Body = note.Body.Replace("\n", "\r\n");
            createdAt = note.CreatedAt;
            Title = note.Title;
            id = note.Id;
            deleted = note.Deleted;
            rev = note.Rev;
            noteType = note.Type;
            sourceDevice = note.SourceDevice;
        }

        /// <summary>
        /// Formats the title properly for saving
        /// </summary>
        /// <returns>Formatted title</returns>
        private string GetFormattedTitle()
        {
            return string.IsNullOrWhiteSpace(Title) ? createdAt.ToString("ddd d MMM ' - ' HH':'mm", CultureInfo.InvariantCulture) : Title;
        }

        /// <summary>
        /// Formats the body properly for saving
        /// </summary>
        /// <returns>Formatted body</returns>
        private string GetFormattedBodyString()
        {
            return Body.Replace("\r\n", "\n");
        }
        #endregion

        #region Events
        /// <summary>
        /// Invoked when the title changes
        /// </summary>
        /// <param name="value">String title value. Unused</param>
        partial void OnTitleChanged(string value)
        {
            IsDirty = true;
            Debounce();
        }

        /// <summary>
        /// Invoked when the body changes
        /// </summary>
        /// <param name="value">String body value. Unused</param>
        partial void OnBodyChanged(string value)
        {
            IsDirty = true;
            Debounce();
        }
        #endregion

        #region AutoSave/Deboumcing
        private CancellationTokenSource? _debounce;

        /// <summary>
        /// Debounces autosaving
        /// </summary>
        private void Debounce()
        {
            _debounce?.Cancel();
            _debounce?.Dispose();

            var cts = new CancellationTokenSource();
            _debounce = cts;

            _ = AutosaveAfterDelayAsync(cts.Token);
        }

        /// <summary>
        /// Waits the autosave duration and then saves
        /// </summary>
        /// <param name="ct">Cancellation token</param>
        /// <returns>task to await</returns>
        private async Task AutosaveAfterDelayAsync(CancellationToken ct)
        {
            try
            {
                await Task.Delay(AutosaveDelay, ct);
                await SaveAsync(ct);
            }
            catch (OperationCanceledException)
            {
                // Superseded by a later keystroke
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Autosave failed for note {NoteId}", id);
            }
        }

        private const int AutosaveDelay = 3000;
        #endregion
    }
}
