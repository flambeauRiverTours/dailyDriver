using System.Text.Json.Serialization;

namespace Notes.Shared.Model
{
    /// <summary>
    /// Represents a note with its associated properties, including ID, user ID, type, title, body, tags, timestamps, deletion status, source device, and revision number.
    /// </summary>
    public record Note
    {
        /// <summary>
        /// Gets or sets the unique identifier for the note.
        /// </summary>
        public required Guid id { get; init; }
        /// <summary>
        /// Gets or sets the unique identifier for the user associated with the note.
        /// </summary>
        public required string userId { get; init; }
        /// <summary>
        /// Gets or sets the type of the note, which can be either a standard note or a checklist.
        /// </summary>
        public required NoteType type { get; init; }
        /// <summary>
        /// Gets or sets the title of the note.
        /// </summary>
        public required string title { get; init; }
        /// <summary>
        /// Gets or sets the body content of the note.
        /// </summary>
        public required string body { get; init; }
        /// <summary>
        /// Gets or sets the collection of tags associated with the note. Tags are used for categorization and organization of notes.
        /// </summary>
        public required IReadOnlyList<string> tags { get; init; }
        /// <summary>
        /// Gets or sets the timestamp indicating when the note was created. This property is represented as a DateTimeOffset to include both date and time information along with the offset from Coordinated Universal Time (UTC).
        /// </summary>
        public required DateTimeOffset createdAt { get; init; }
        /// <summary>
        /// Gets or sets the timestamp indicating when the note was last updated.
        /// </summary>
        public DateTimeOffset updatedAt { get; init; }
        /// <summary>
        /// Gets or sets a value indicating whether the note is deleted.
        /// </summary>
        public required bool deleted { get; init; }
        /// <summary>
        /// Gets or sets the identifier of the device from which the note was created.
        /// </summary>
        public required string sourceDevice { get; init; }
        /// <summary>
        /// Gets or sets the revision number of the note. This property is used to track changes and updates to the note over time. It is nullable, allowing for cases where a revision number may not be applicable or available.
        /// </summary>
        public long? rev { get; init; }
    }

    /// <summary>
    /// Represents the type of a note, which can be either a standard note or a checklist. This enumeration is used to differentiate between different types of notes in the application.
    /// </summary>
    [JsonConverter(typeof(JsonStringEnumConverter<NoteType>))]
    public enum NoteType
    {
        [JsonStringEnumMemberName("note")] Note,
        [JsonStringEnumMemberName("checklist")] Checklist
    }
}
