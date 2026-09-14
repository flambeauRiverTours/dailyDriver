namespace Notes.Shared.Tests.Helpers.Fixtures
{
    /// <summary>
    /// One case from either <c>storedCases</c> or <c>renderCases</c> in
    /// <c>fixtures/due-date-parsing.json</c>.
    ///
    /// Both arrays carry the identical field set (name, body, text, due, checked) because they are
    /// the two directions of one round-trip: stored reads <c>Body</c> and expects
    /// <c>Text</c>/<c>Due</c>/<c>Checked</c>; render reads those three and expects <c>Body</c>.
    /// </summary>
    public class DueDateCase
    {
        public required string Name { get; init; } = string.Empty;
        public required string Body { get; init; } = string.Empty;
        public required string Text { get; init; } = string.Empty;

        /// <summary>
        /// Null for an undated item. <c>required</c> means the key must be present in the JSON;
        /// nullable means its value may legitimately be null. Both are true here.
        /// </summary>
        public required DateOnly? Due { get; init; }

        public required bool Checked { get; init; }
    }
}
