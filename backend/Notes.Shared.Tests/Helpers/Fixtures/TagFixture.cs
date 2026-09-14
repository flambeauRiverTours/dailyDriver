namespace Notes.Shared.Tests.Helpers.Fixtures
{
    public sealed record TagFixture
    {
        private static readonly Lazy<TagFixture> _instance =
            new(() => FixtureParser.Parse<TagFixture>("tag-parsing.json"));

        /// <summary>
        /// The corpus, parsed once. <c>MemberData</c> sources are enumerated at discovery *and*
        /// again at run, so without this the file is read and deserialized several times per run and
        /// the filename is duplicated at every call site.
        /// </summary>
        public static TagFixture Instance => _instance.Value;

        public required IReadOnlyList<TagCase> Cases { get; init; }
    }
}
