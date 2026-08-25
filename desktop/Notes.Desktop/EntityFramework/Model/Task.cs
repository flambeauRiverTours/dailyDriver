namespace Notes.Desktop.EntityFramework.Model
{
    /// <summary>
    /// 
    /// </summary>
    internal class Task
    {
        /// <summary>
        /// Gets or sets the unique identifier for the note associated with the task.
        /// </summary>
        internal Guid NoteId { get; set; }
        /// <summary>
        /// Gets or sets the position of the task within the note.
        /// </summary>
        internal int Position { get; set; }
        /// <summary>
        /// Gets or sets the text description of the task.
        /// </summary>
        internal string Text { get; set; } = "";
        /// <summary>
        /// Gets or sets a value indicating whether the task is checked (completed).
        /// </summary>
        internal bool Checked { get; set; }
        /// <summary>
        /// Gets or sets the due date of the task as a string. This property can be null or empty if no due date is set. ISO YYYY-MM-DD
        /// </summary>
        internal string Due { get; set; } = "";
    }
}
