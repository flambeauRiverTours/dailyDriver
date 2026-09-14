using Notes.Shared.Model;
namespace Notes.Desktop.EntityFramework.Model
{
    /// <summary>
    /// POCO class representing a note in the application. This class contains properties for the note's unique identifier, type, title, body, creation and update timestamps, deletion status, revision number, and source device.
    /// </summary>
    internal class Note
    {
        /// <summary>
        /// Gets or sets the unique identifier for the note. This property is required and cannot be null.
        /// </summary>
        public Guid Id { get; set; }
        /// <summary>
        /// Gets or sets the type of the note, which can be either a standard note or a checklist. This property is required and cannot be null.
        /// </summary>
        public NoteType Type { get; set; }
        /// <summary>
        /// Gets or sets the title of the note. This property is required and cannot be null or empty.
        /// </summary>
        public string Title { get; set; } = "";
        /// <summary>
        /// Gets or sets the body content of the note. This property is required and cannot be null or empty.
        /// </summary>
        public string Body { get; set; } = "";
        /// <summary>
        /// Gets or sets the date and time when the note was created. This property is required and cannot be null.
        /// </summary>
        public DateTimeOffset CreatedAt { get; set; }
        /// <summary>
        /// Gets or sets the date and time when the note was last updated. This property is required and cannot be null.
        /// </summary>
        public DateTimeOffset UpdatedAt { get; set; }
        /// <summary>
        /// Gets or sets a value indicating whether the note has been marked as deleted. This property is required and cannot be null.
        /// </summary>
        public bool Deleted { get; set; }
        /// <summary>
        /// Gets or sets the revision number of the note. This property is required and cannot be null or empty
        /// </summary>
        public long? Rev { get; set; }
        /// <summary>
        /// Gets or sets the source device from which the note was created or last modified. This property can be null or empty if the source device is not specified.
        /// </summary>
        public string SourceDevice { get; set; } = "";
    }
}
