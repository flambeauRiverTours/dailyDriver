using FluentAssertions;
using Notes.Shared.Model;
using Notes.Shared.Tests.Helpers.Fixtures;

namespace Notes.Shared.Tests
{
    public class ChecklistParserTests
    {
        public static IEnumerable<object[]> ParseCases() =>
    ChecklistFixture.Instance.Cases
        .Select(c => new object[] { c.Body, c.Items });

        public static IEnumerable<object[]> RenderCases() =>
            ChecklistFixture.Instance.RenderCases
                .Select(c => new object[] { c.Items, c.Body });   

        public static IEnumerable<object[]> ConversionCases() =>
    ChecklistFixture.Instance.ConversionCases
        .Select(c => new object[] { c.NoteBody, c.ChecklistBody });

        public static IEnumerable<object[]> CleanCases() =>
            ChecklistFixture.Instance.CleanCases
                .Select(c => new object[] { c.Items, c.Cleaned }); 

        public static IEnumerable<object[]> NormalizeCases() =>
    ChecklistFixture.Instance.NormalizeCases
        .Select(c => new object[] { c.Items, c.Normalized, });


        [Theory]
        [MemberData(nameof(ParseCases))]
        public void ChecklistParser_ParseChecklist_ParsesChecklistCorrectly(string input, IReadOnlyList<ChecklistItem> expectedItems)
        {
            ChecklistParser.ParseChecklist(input).Should().Equal(expectedItems);
        }

        [Theory]
        [MemberData(nameof(RenderCases))]
        public void ChecklistParser_RenderChecklist_RendersChecklistCorrectly(IReadOnlyList<ChecklistItem> inputItems, string expectedBody)
        {
            ChecklistParser.RenderChecklist(inputItems).Should().Be(expectedBody);
        }

        [Theory]
        [MemberData(nameof(ParseCases))]
        public void ChecklistParser_ParseAndRender_RoundTripMaintainsIntegrity(string input, IReadOnlyList<ChecklistItem> expectedItems)
        {
            var parsedItems = ChecklistParser.ParseChecklist(input);
            var renderedBody = ChecklistParser.RenderChecklist(parsedItems);
            var reparsedItems = ChecklistParser.ParseChecklist(renderedBody);
            reparsedItems.Should().Equal(expectedItems);
        }

        [Theory]
        [MemberData(nameof(NormalizeCases))]
        public void ChecklistParser_Normalize_NormalizesChecklistItemsCorrectly(IReadOnlyList<ChecklistItem> inputItems, IReadOnlyList<ChecklistItem> expectedItems)
        {
            ChecklistParser.Normalize(inputItems).Should().Equal(expectedItems);
        }

        [Theory]
        [MemberData(nameof(ConversionCases))]
        public void ChecklistParser_Convert_ConvertsChecklistBodyToItemsCorrectly(string inputBody, string expectedBody)
        {
            ChecklistParser.Convert(inputBody).Should().Be(expectedBody);
        }


        [Theory]
        [MemberData(nameof(CleanCases))]
        public void ChecklistParser_Clean_CleansChecklistItemsCorrectly(IReadOnlyList<ChecklistItem> inputItems, IReadOnlyCollection<ChecklistItem> expectedBody)
        {
            ChecklistParser.Clean(inputItems).Should().Equal(expectedBody);
        }

        [Fact]
        public void ChecklistFixture_Parse_ContainsAllCases() =>
            FixtureParser.Parse<ChecklistFixture>("checklist-parsing.json").Cases.Should().HaveCount(20);

        [Fact]
        public void ChecklistFixture_Render_ContainsAllCases() =>
            FixtureParser.Parse<ChecklistFixture>("checklist-parsing.json").RenderCases.Should().HaveCount(4);

        [Fact]
        public void ChecklistFixture_Normalize_ContainsAllCases() =>
            FixtureParser.Parse<ChecklistFixture>("checklist-parsing.json").NormalizeCases.Should().HaveCount(3);

        [Fact]
        public void ChecklistFixture_Clean_ContainsAllCases() =>
            FixtureParser.Parse<ChecklistFixture>("checklist-parsing.json").CleanCases.Should().HaveCount(3);

        [Fact]
        public void ChecklistFixture_Conversion_ContainsAllCases() =>
            FixtureParser.Parse<ChecklistFixture>("checklist-parsing.json").ConversionCases.Should().HaveCount(3);
    }
}
