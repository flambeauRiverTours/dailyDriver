namespace Notes.Shared.Model
{
    /// <summary>
    /// Represents a row in a task list, containing information about the task's associated note, position, text, completion status, and due date.
    /// </summary>
    public record TaskRow
    {
        /// <summary>
        /// Gets or sets the unique identifier of the note associated with this task row. This property is used to link the task row to its corresponding note in the application.
        /// </summary>
        public Guid noteId { get; init; }
        /// <summary>
        /// Gets or sets the position of the task row within the task list. This property is used to determine the order of tasks in the list.
        /// </summary>
        public int position { get; init; }
        /// <summary>
        /// Gets or sets the text content of the task row. This property contains the description or title of the task, providing information about what needs to be done.
        /// </summary>
        public required string text { get; init; }
        /// <summary>
        /// Gets or sets a value indicating whether the task row is marked as completed. This property is used to track the completion status of the task, allowing users to see which tasks have been finished.
        /// </summary>
        public bool @checked { get; init; }
        /// <summary>
        /// Gets or sets the due date for the task row. This property is used to indicate when the task is expected to be completed.
        /// </summary>
        public required string due { get; init; }
    }
}
