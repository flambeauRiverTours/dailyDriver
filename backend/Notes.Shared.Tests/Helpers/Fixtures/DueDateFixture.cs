namespace Notes.Shared.Tests.Helpers.Fixtures
{
    /// <summary>
    /// <c>fixtures/due-date-parsing.json</c>.
    ///
    /// <c>required</c> on every array is a structural guard: a truncated fixture that lost one of
    /// them throws at deserialization naming the missing property, rather than handing back a null
    /// list that a theory then silently runs zero times.
    /// </summary>
    public class DueDateFixture
    {
        private static readonly Lazy<DueDateFixture> _instance =
            new(() => FixtureParser.Parse<DueDateFixture>("due-date-parsing.json"));

        public static DueDateFixture Instance => _instance.Value;

        /// <summary>The stored format — every client must implement these; getting them wrong corrupts bodies.</summary>
        public required IReadOnlyList<DueDateCase> StoredCases { get; init; }

        public required IReadOnlyList<DueDateCase> RenderCases { get; init; }

        /// <summary>Typing shortcuts. Never reach storage — a client that skips them is still correct.</summary>
        public required IReadOnlyList<DueDateInputCase> InputCases { get; init; }
    }
}
