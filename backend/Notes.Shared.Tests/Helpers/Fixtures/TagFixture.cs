namespace Notes.Shared.Tests.Helpers.Fixtures
{
    public sealed record TagFixture
    {
        /// <summary>
        /// Gets or sets the list of tag cases for testing.
        /// </summary>
        public required IReadOnlyList<TagCase> Cases { get; init; }
    }
}
