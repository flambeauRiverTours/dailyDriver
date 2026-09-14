using FluentAssertions;
using Notes.Shared.Tests.Helpers.Fixtures;

namespace Notes.Shared.Tests
{
    /// <summary>
    /// Conversion between prose and checklist bodies, and the round-trip properties that hold
    /// across it.
    ///
    /// <c>conversionCases</c> pins <b>both</b> directions, which is how the Kotlin suite consumes
    /// it — see <c>ChecklistParsingTest.kt</c>. The checklist→note direction compares against
    /// <c>noteBody</c> with blank lines removed, because that direction is lossy by construction:
    /// <c>milk\n\neggs</c> parses to two items which render back as <c>milk\neggs</c>. Asserting
    /// <c>noteBody</c> verbatim would fail for a reason that is not a bug.
    /// </summary>
    public class ChecklistConversionTests
    {
        public static IEnumerable<object[]> ConversionCases() =>
            ChecklistFixture.Instance.ConversionCases
                .Select(c => new object[] { c.NoteBody, c.ChecklistBody, c.Name });

        [Theory]
        [MemberData(nameof(ConversionCases))]
        public void Convert_TurnsProseIntoAChecklist(string noteBody, string checklistBody, string caseName) =>
            ChecklistParser.Convert(noteBody).Should().Be(checklistBody, because: caseName);

        [Theory]
        [MemberData(nameof(ConversionCases))]
        public void ToNoteBody_StripsTheMarkersAndKeepsTheText(string noteBody, string checklistBody, string caseName)
        {
            var expected = string.Join("\n",
                noteBody.Split('\n').Where(line => !string.IsNullOrWhiteSpace(line)));

            ChecklistParser.ToNoteBody(checklistBody).Should().Be(expected, because: caseName);
        }

        /// <summary>The lossless direction — prose survives a round trip exactly.</summary>
        [Fact]
        public void NoteToChecklistToNote_ReturnsTheOriginalProse()
        {
            const string prose = "milk\neggs\nbread";

            ChecklistParser.ToNoteBody(ChecklistParser.Convert(prose)).Should().Be(prose);
        }

        /// <summary>
        /// The lossy direction, and deliberately so: prose has nowhere to record a checkbox, so
        /// every word survives and every tick does not.
        /// </summary>
        [Fact]
        public void ChecklistToNoteToChecklist_KeepsEveryWordAndLosesEveryTick()
        {
            const string checklist = "- [x] milk\n- [ ] eggs";

            var roundTripped = ChecklistParser.Convert(ChecklistParser.ToNoteBody(checklist));

            roundTripped.Should().Be("- [ ] milk\n- [ ] eggs");
        }

        /// <summary>
        /// Converting a body that is already a checklist is a no-op, not a second pass of marking.
        /// Without this, conversion produces <c>- [ ] - [ ] milk</c>. No fixture case covers it.
        /// </summary>
        [Fact]
        public void Convert_DoesNotDoubleMarkAnAlreadyChecklistBody()
        {
            const string checklist = "- [x] milk\n- [ ] eggs";

            ChecklistParser.Convert(checklist).Should().Be(checklist);
        }

        /// <summary>
        /// <c>ToNoteBody</c> cleans before joining, so trailing whitespace does not survive the
        /// conversion and empty items do not become blank lines.
        /// </summary>
        [Fact]
        public void ToNoteBody_DoesNotCarryWhitespaceAcross() =>
            ChecklistParser.ToNoteBody("- [ ] peanut \n- [x] milk  ").Should().Be("peanut\nmilk");

        /// <summary>
        /// CRLF must parse and convert identically to LF. The shared fixtures are LF-only, so this
        /// gap is invisible to the corpus — but a WPF <c>TextBox</c> with <c>AcceptsReturn</c>
        /// produces CRLF, which makes this the desktop client's default input.
        /// </summary>
        [Fact]
        public void Convert_TreatsCrlfTheSameAsLf() =>
            ChecklistParser.Convert("milk\r\neggs").Should().Be(ChecklistParser.Convert("milk\neggs"));

        [Fact]
        public void ConversionCases_AreAllLoaded() =>
            ChecklistFixture.Instance.ConversionCases.Should().HaveCount(3);
    }
}
