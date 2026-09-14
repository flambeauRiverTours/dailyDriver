namespace Notes.Shared.Tests.Helpers.Fixtures
{
    public sealed record TagCase
    {
        public required string Name { get; init; }
        public required string Body { get; init; }
        public required string[] Tags { get; init; }
    }
}
