namespace Notes.Desktop.EntityFramework.Model
{
    /// <summary>
    /// POCO class representing an outbox entry in the application. This class contains properties for the unique identifier of the associated note, the base revision, and the date and time when the entry was queued.
    /// </summary>
    internal class Outbox
    {
        /// <summary>
        /// Gets or sets the unique identifier for the note associated with the outbox entry.
        /// </summary>
        internal Guid NoteId { get; set; }
        /// <summary>
        /// Gets or sets the base revision for the outbox entry.
        /// </summary>
        internal long? BaseRev { get; set; }
        /// <summary>
        /// Gets or sets the date and time when the outbox entry was queued.
        /// </summary>
        internal DateTimeOffset QueuedAt { get; set; }
    }
}
