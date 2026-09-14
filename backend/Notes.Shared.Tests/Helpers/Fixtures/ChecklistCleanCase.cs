using Notes.Shared.Model;

namespace Notes.Shared.Tests.Helpers.Fixtures
{
    public class ChecklistCleanCase
    {
        public required string Name { get; init; } = string.Empty;
        public required IReadOnlyCollection<ChecklistItem> Items { get; init; }
        public required IReadOnlyCollection<ChecklistItem> Cleaned { get; init; }
    }
}
