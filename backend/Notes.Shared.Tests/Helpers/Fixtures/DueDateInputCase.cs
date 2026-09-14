namespace Notes.Shared.Tests.Helpers.Fixtures
{
    /// <summary>
    /// One case from <c>inputCases</c> — the typing shortcuts (<c>@tomorrow</c>, <c>@t+3</c>) that
    /// are resolved to an ISO date at entry and never persisted.
    ///
    /// <see cref="Today"/> is supplied by the fixture rather than read from the clock, which is what
    /// makes these cases reproducible — resolution must take "today" as a parameter.
    /// </summary>
    public class DueDateInputCase
    {
        public required string Name { get; init; } = string.Empty;
        public required DateOnly Today { get; init; }
        public required string Input { get; init; } = string.Empty;

        /// <summary>Null when the input is not a recognised shortcut (<c>someday</c>, <c>t-3</c>, <c>w+</c>).</summary>
        public required DateOnly? Resolved { get; init; }
    }
}
