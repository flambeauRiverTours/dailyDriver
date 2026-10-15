using System.Collections.ObjectModel;
using System.ComponentModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;
using Notes.Desktop.EntityFramework;
using Notes.Desktop.EntityFramework.Model;

namespace Notes.Desktop.ViewModels
{
    public partial class NoteListViewModel : ObservableObject
    {

        private readonly IDbContextFactory<AppDbContext> _dbContextFactory;

        public NoteListViewModel(IDbContextFactory<AppDbContext> dbContextFactory, ILogger<NoteListViewModel> _logger)
        {
            _dbContextFactory = dbContextFactory;
            logger = _logger;
        }
        private ILogger<NoteListViewModel> logger;
        /// <summary>
        /// Current list of notes
        /// </summary>
        public ObservableCollection<NoteSummary> Notes { get; } = new();
        /// <summary>
        /// Currently selected note tags
        /// </summary>
        public ObservableCollection<TagFilter> Tags { get; } = new();
        /// <summary>
        /// Currently selected note
        /// </summary>
        [ObservableProperty] public partial NoteSummary? SelectedNote { get; set; }
        /// <summary>
        /// True if no notes exist
        /// </summary>
        [ObservableProperty] public partial bool HasNoNotes { get; set; }
        /// <summary>
        /// True if no notes match the tags
        /// </summary>
        [ObservableProperty] public partial bool HasNoMatches { get; set; }

        private const int SnippetSourceLength = 200;


        /// <summary>
        /// Refreshs the note list
        /// </summary>
        /// <param name="ct">Cancellation token for asyc ops</param>
        /// <returns>Task to await</returns>
        [RelayCommand]
        private async Task RefreshAsync(CancellationToken ct)
        {
            using var dbContext = await _dbContextFactory.CreateDbContextAsync(ct);

            var selectedTags = Tags.Where(t => t.IsSelected).Select(t => t.Tag).ToList();

            //  start building out the query
            IQueryable<Note> query = dbContext.Notes.AsNoTracking().Where(n => !n.Deleted);

            if (selectedTags.Count > 0)
            {
                // OR across chips
                query = query.Where(n =>
                    dbContext.NoteTags.Any(t => t.NoteId == n.Id && selectedTags.Contains(t.Tag)));
            }

            var rows = await query
                .OrderByDescending(n => n.UpdatedAt)
                .Select(n => new
                {
                    n.Id,
                    n.Title,
                    n.UpdatedAt,
                    // Truncated in SQL, so a long note's body never crosses the wire.
                    Head = n.Body.Length > SnippetSourceLength
                         ? n.Body.Substring(0, SnippetSourceLength)
                         : n.Body
                })
                .ToListAsync(ct);

            var previouslySelected = SelectedNote?.Id;

            Notes.Clear();
            foreach (var row in rows)
            {
                Notes.Add(new NoteSummary
                {
                    Id = row.Id,
                    Title = row.Title,
                    Snippet = ToSnippet(row.Head),
                    UpdatedDisplay = NoteSummary.ToUpdatedDisplay(row.UpdatedAt)
                });
            }

            SelectedNote = Notes.FirstOrDefault(n => n.Id == previouslySelected);

            HasNoNotes = Notes.Count == 0 && selectedTags.Count == 0;
            HasNoMatches = Notes.Count == 0 && selectedTags.Count > 0;
        }

        private static string ToSnippet(string head) => string.Join(' ', head.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries));


        /// <summary>
        /// Reloads the available tags
        /// </summary>
        /// <param name="ct">Cancellation token for async ops</param>
        /// <returns>Task to await</returns>
        private async Task ReloadTagsAsync(CancellationToken ct)
        {
            using var dbContext = await _dbContextFactory.CreateDbContextAsync(ct);
            var tags = await dbContext.NoteTags.AsNoTracking().Select(t => t.Tag).Distinct().OrderBy(t => t).ToListAsync(ct);

            var stillSelected = Tags.Where(t => t.IsSelected).Select(t => t.Tag).ToHashSet();

            foreach (var old in Tags) old.PropertyChanged -= OnTagFilterChanged; // or they leak
            Tags.Clear();

            foreach (var tag in tags)
            {
                var filter = new TagFilter { Tag = tag, IsSelected = stillSelected.Contains(tag) };
                filter.PropertyChanged += OnTagFilterChanged;
                Tags.Add(filter);
            }
        }

        /// <summary>
        /// Event handler for tags changing
        /// </summary>
        /// <param name="sender">Sender obj</param>
        /// <param name="e">Event args</param>
        private async void OnTagFilterChanged(object? sender, PropertyChangedEventArgs e)
        {
            if (e.PropertyName == nameof(TagFilter.IsSelected))
            {
                try
                {
                    await RefreshCommand.ExecuteAsync(null);
                }
                catch (Exception ex)
                {
                    logger.LogError(ex, "NoteListViewModel::RefreshCommand failed with exception {exception}", ex.Message);
                }
            }

        }

        /// <summary>
        /// Relaycommand to initialize the list
        /// </summary>
        /// <param name="ct">Cancellation tokens for async ops</param>
        /// <returns>Task to await</returns>
        [RelayCommand]
        private async Task InitializeAsync(CancellationToken ct)
        {
            await ReloadTagsAsync(ct);
            await RefreshAsync(ct);
        }
    }
}
