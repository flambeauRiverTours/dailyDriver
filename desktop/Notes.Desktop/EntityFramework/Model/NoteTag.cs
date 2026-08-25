namespace Notes.Desktop.EntityFramework.Model
{
    /// <summary>
    /// POCO class representing a note tag in the application. This class contains properties for the unique identifier of the associated note and the tag itself.
    /// </summary>
    internal class NoteTag
    {
        /// <summary>
        /// Gets or sets the unique identifier for the note associated with the tag.
        /// </summary>
        internal Guid NoteId { get; set; }
        /// <summary>
        /// Gets or sets the tag associated with the note. This property is required and cannot be null or empty.
        /// </summary>
        internal string Tag { get; set; } = "";
    }
}
