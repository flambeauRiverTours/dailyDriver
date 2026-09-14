using FluentAssertions;
using Notes.Shared.Tests.Helpers.Fixtures;

namespace Notes.Shared.Tests
{
    /// <summary>
    /// Typing shortcuts (<c>tomorrow</c>, <c>t+3</c>), pinned by the <c>inputCases</c> half of
    /// <c>fixtures/due-date-parsing.json</c>.
    ///
    /// This is the corpus's explicitly **less binding** half: shortcuts are resolved at entry and
    /// rewritten to a literal ISO date, so nothing downstream ever sees one. A client that skips
    /// them is still correct, just less pleasant to type into — unlike <c>storedCases</c>, where
    /// getting it wrong corrupts bodies.
    ///
    /// Every case supplies its own <c>today</c>, which is why resolution takes it as a parameter
    /// rather than reading a clock: the corpus spans 2026-01-31 to 2028-02-29 precisely to exercise
    /// month-end clamping across two leap years.
    /// </summary>
    public class InputShortcutParserTests
    {
        public static IEnumerable<object[]> InputCases() =>
            DueDateFixture.Instance.InputCases
                .Select(c => new object[] { c.Input, c.Today, c.Resolved!, c.Name });

        [Theory]
        [MemberData(nameof(InputCases))]
        public void Parse_ResolvesTheShortcutAgainstTheGivenToday(
            string input, DateOnly today, DateOnly? expected, string caseName) =>
            InputShortcutParser.Parse(input, today).Should().Be(expected, because: caseName);

        /// <summary>
        /// Reserved words match the whole normalized input, not a substring of it. The corpus does
        /// not pin this — <c>someday</c> happens not to contain any reserved word — so a
        /// <c>Contains</c>-based implementation passes all 21 cases and still resolves
        /// <c>"not today"</c> to today.
        /// </summary>
        [Theory]
        [InlineData("not today")]
        [InlineData("next weekend")]
        [InlineData("today-ish")]
        public void Parse_MatchesReservedWordsExactly_NotAsSubstrings(string input) =>
            InputShortcutParser.Parse(input, new DateOnly(2026, 8, 21)).Should().BeNull();

        /// <summary>
        /// Resolution is case-insensitive throughout, not just for the word forms. The corpus covers
        /// <c>Tomorrow</c> but every offset case is lowercase, so this closes that gap.
        /// </summary>
        [Theory]
        [InlineData("T+3")]
        [InlineData("W+2")]
        [InlineData("NEXT-WEEK")]
        public void Parse_IsCaseInsensitiveForOffsetsToo(string input) =>
            InputShortcutParser.Parse(input, new DateOnly(2026, 8, 21)).Should().NotBeNull();

        /// <summary>
        /// Surrounding whitespace is tolerated — the input arrives from a text field, so a stray
        /// space either side is ordinary rather than an error worth rejecting.
        /// </summary>
        [Fact]
        public void Parse_TrimsSurroundingWhitespace() =>
            InputShortcutParser.Parse("  tomorrow  ", new DateOnly(2026, 8, 21))
                .Should().Be(new DateOnly(2026, 8, 22));

        /// <summary>
        /// A bare ISO date is checked before normalization, because the hyphen fold that makes
        /// <c>next-week</c> and <c>next week</c> agree would otherwise turn <c>2026-12-25</c> into
        /// <c>2026 12 25</c> and drop it. The corpus pins the happy path; this pins the reason.
        /// </summary>
        [Fact]
        public void Parse_ChecksForAnIsoDateBeforeFoldingHyphens() =>
            InputShortcutParser.Parse("2026-12-25", new DateOnly(2026, 8, 21))
                .Should().Be(new DateOnly(2026, 12, 25));

        /// <summary>
        /// An ISO-shaped string that is not a real calendar day does not resolve, for the same
        /// reason <c>@2026-02-30</c> stays literal text in a stored body — one definition of "is
        /// this a date", shared by both paths.
        /// </summary>
        [Fact]
        public void Parse_RejectsAnImpossibleCalendarDate() =>
            InputShortcutParser.Parse("2026-02-30", new DateOnly(2026, 8, 21)).Should().BeNull();
    }
}
