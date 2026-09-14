namespace Notes.Shared.Model
{
    public record ChecklistItem
    {
        /// <summary>
        /// Gets or sets the text content of the checklist item.
        /// </summary>
        public required string text { get; init; }
        /// <summary>
        /// Gets or sets whether the checklist item is marked as completed.
        /// </summary>
        public required bool @checked { get; init; }
    }
}
