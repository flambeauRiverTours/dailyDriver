using FluentAssertions;
using Notes.Shared.Tests.Helpers.Fixtures;

namespace Notes.Shared.Tests
{
    public class TagParserTests
    {
        /// <summary>
        /// Loads the test fixture from the specified JSON file.
        /// </summary>
        /// <returns></returns>
        public static IEnumerable<object[]> TagCases() =>
            FixtureParser.Parse<TagFixture>("tag-parsing.json").Cases
            .Select(c => new object[] { c.Body, c.Tags, });

        [Theory]
        [MemberData(nameof(TagCases))]
        public void TagParser_ParseTags_ParsesTagsCorrectly(string input, string[] expectedTags)
        {
            TagParser.ParseTags(input).Should().Equal(expectedTags);
        }

        [Fact]
        public void TagFixture_ContainsAllCases() =>
            FixtureParser.Parse<TagFixture>("tag-parsing.json").Cases.Should().HaveCount(26);
    }
}
